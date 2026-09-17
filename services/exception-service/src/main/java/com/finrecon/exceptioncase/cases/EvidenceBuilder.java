package com.finrecon.exceptioncase.cases;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.finrecon.exceptioncase.domain.LedgerEntry;
import com.finrecon.exceptioncase.domain.Payment;
import com.finrecon.exceptioncase.domain.ReconciliationResult;
import com.finrecon.exceptioncase.domain.Settlement;

// P4 evidence builder. Pure function: the same inputs always produce the
// same evidence rows. Each row pins one compared field to its source
// record (FR-08); P4 never invents values, it only quotes stored ones.
public final class EvidenceBuilder {

    private EvidenceBuilder() {
    }

    public record EvidenceSpec(String sourceType, String sourceRecordId,
                               String fieldName, String expectedValue,
                               String observedValue) {
    }

    public static List<EvidenceSpec> build(String category, Payment payment,
                                           List<LedgerEntry> ledgers,
                                           List<Settlement> settlements,
                                           ReconciliationResult result,
                                           LocalDate deadline) {
        List<EvidenceSpec> rows = new ArrayList<>();
        rows.add(record("payment_gateway", id(payment.getPaymentId()),
                null, null, "external_txn_id=" + payment.getExternalTxnId()));
        for (LedgerEntry ledger : ledgers) {
            rows.add(record("ledger", id(ledger.getLedgerEntryId()),
                    null, null, "payment=" + payment.getExternalTxnId()));
        }
        for (Settlement settlement : settlements) {
            rows.add(record("settlement", id(settlement.getSettlementId()),
                    null, null, "batch=" + settlement.getBatchId()));
        }
        rows.add(record("reconciliation_result", id(result.getResultId()),
                "match_status", "MATCHED", result.getMatchStatus()));

        LedgerEntry ledger = ledgers.isEmpty() ? null : ledgers.get(0);
        Settlement settlement = settlements.isEmpty() ? null : settlements.get(0);
        switch (category) {
            case "AMOUNT_MISMATCH" -> rows.add(record("ledger",
                    ledger == null ? "-" : id(ledger.getLedgerEntryId()),
                    "gross_amount", money(payment.getAmount()),
                    ledger == null ? "absent" : money(ledger.getGrossAmount())));
            case "FEE_VARIANCE" -> rows.add(record("settlement",
                    id(settlement.getSettlementId()), "fee_amount",
                    money(ledger.getFeeAmount()), money(settlement.getFeeAmount())));
            case "PARTIAL_SETTLEMENT" -> rows.add(record("settlement",
                    id(settlement.getSettlementId()), "settled_amount",
                    money(ledger.getNetAmount()), money(settlement.getSettledAmount())));
            case "MISSING_SETTLEMENT" -> rows.add(record("settlement", "-",
                    "settlement", "present", "absent"));
            case "DUPLICATE_SETTLEMENT" -> rows.add(record("settlement",
                    id(settlement.getSettlementId()), "settlement_count",
                    "1", String.valueOf(settlements.size())));
            case "FX_VARIANCE" -> rows.add(record("settlement",
                    id(settlement.getSettlementId()), "currency",
                    payment.getCurrency(), distinctCurrencies(payment, ledger, settlement)));
            case "STATUS_MISMATCH" -> rows.add(record("ledger",
                    id(ledger.getLedgerEntryId()), "status",
                    payment.getStatus(), ledger.getPostingStatus()
                            + " / " + settlement.getSettlementStatus()));
            case "LATE_SETTLEMENT" -> rows.add(record("settlement",
                    id(settlement.getSettlementId()), "settlement_date",
                    "on or before " + deadline, String.valueOf(settlement.getSettlementDate())));
            default -> rows.add(record("ledger", "-",
                    "ledger_entry_count", "1", String.valueOf(ledgers.size())));
        }
        return List.copyOf(rows);
    }

    private static EvidenceSpec record(String sourceType, String sourceRecordId,
                                       String field, String expected, String observed) {
        return new EvidenceSpec(sourceType, sourceRecordId, field, expected, observed);
    }

    private static String id(Object id) {
        return String.valueOf(id);
    }

    private static String money(java.math.BigDecimal amount) {
        return amount == null ? "-" : amount.toPlainString();
    }

    private static String distinctCurrencies(Payment payment, LedgerEntry ledger,
                                             Settlement settlement) {
        return java.util.stream.Stream.of(payment.getCurrency(),
                        ledger == null ? null : ledger.getCurrency(),
                        settlement == null ? null : settlement.getCurrency())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(" / "));
    }
}
