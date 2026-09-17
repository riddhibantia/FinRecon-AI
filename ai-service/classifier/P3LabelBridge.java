import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrecon.reconciliation.domain.Payment;
import com.finrecon.reconciliation.domain.LedgerEntry;
import com.finrecon.reconciliation.domain.Settlement;
import com.finrecon.reconciliation.reconcile.ReconciliationEngine;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Transport only: all labels come from the compiled, unchanged P3 engine. */
public class P3LabelBridge {
    private static BigDecimal money(JsonNode row, String key) {
        return new BigDecimal(row.get(key).asText());
    }
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
        String line;
        while ((line = input.readLine()) != null) {
            JsonNode root = mapper.readTree(line);
            JsonNode p = root.get("payment");
            if (root.get("context").get("settlement_window_days").asInt() != ReconciliationEngine.SETTLEMENT_WINDOW_DAYS) {
                throw new IllegalArgumentException("Snapshot window does not match P3");
            }
            Payment payment = new Payment("synthetic", "synthetic", "synthetic", money(p, "amount"),
                p.get("currency").asText(), p.get("status").asText(), OffsetDateTime.parse(p.get("event_time").asText()));
            var ledgers = new ArrayList<LedgerEntry>();
            for (JsonNode l : root.get("ledgers")) {
                ledgers.add(new LedgerEntry(payment, money(l, "gross_amount"), money(l, "fee_amount"), money(l, "net_amount"),
                    l.get("currency").asText(), l.get("posting_status").asText(), OffsetDateTime.parse(l.get("posted_at").asText())));
            }
            var settlements = new ArrayList<Settlement>();
            for (JsonNode s : root.get("settlements")) {
                settlements.add(new Settlement(payment, money(s, "settled_amount"), money(s, "fee_amount"),
                    s.get("currency").asText(), s.get("settlement_status").asText(), LocalDate.parse(s.get("settlement_date").asText()), "synthetic"));
            }
            var outcome = ReconciliationEngine.reconcile(payment, ledgers, settlements);
            var output = new LinkedHashMap<String, Object>();
            output.put("match_status", outcome.matchStatus());
            output.put("label", outcome.mismatchType());
            output.put("rule_version", ReconciliationEngine.RULE_VERSION);
            output.put("settlement_window_days", ReconciliationEngine.SETTLEMENT_WINDOW_DAYS);
            System.out.println(mapper.writeValueAsString(output));
        }
    }
}
