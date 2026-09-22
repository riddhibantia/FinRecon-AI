package com.finrecon.ingestion.ingest;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import com.finrecon.shared.domain.LedgerEntry;
import com.finrecon.shared.domain.LedgerEntryRepository;
import com.finrecon.shared.domain.Payment;
import com.finrecon.shared.domain.PaymentRepository;
import com.finrecon.shared.domain.Settlement;
import com.finrecon.shared.domain.SettlementRepository;

// P2 ingestion: CSV/JSON rows -> validated canonical P1 entities.
// Deterministic: invalid rows are rejected with per-row errors, never
// silently fixed. Idempotent: repeats skip via existing P1 constraints
// (payments) or exact-duplicate match (ledger/settlement). No matching,
// no reconciliation, no exception logic here.
// P5: accepted rows are also published as ingest events when a publisher
// bean exists; publishing is best-effort and never fails the sync path.

// P2 ingestion: CSV/JSON rows -> validated canonical P1 entities.
// Deterministic: invalid rows are rejected with per-row errors, never
// silently fixed. Idempotent: repeats skip via existing P1 constraints
// (payments) or exact-duplicate match (ledger/settlement). No matching,
// no reconciliation, no exception logic here.
// P5: accepted rows are also published as ingest events when a publisher
// bean exists; publishing is best-effort and never fails the sync path.
@Service
public class IngestionService {

    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    static final String[] PAYMENT_COLUMNS =
            {"external_txn_id", "customer_id", "merchant_id", "amount",
             "currency", "status", "event_time"};
    static final String[] LEDGER_COLUMNS =
            {"external_txn_id", "gross_amount", "fee_amount", "net_amount",
             "currency", "posting_status", "posted_at"};
    static final String[] SETTLEMENT_COLUMNS =
            {"external_txn_id", "settled_amount", "fee_amount", "currency",
             "settlement_status", "settlement_date", "batch_id"};

    private final PaymentRepository payments;
    private final LedgerEntryRepository ledgerEntries;
    private final SettlementRepository settlements;
        private final io.micrometer.core.instrument.MeterRegistry meters;

    public IngestionService(PaymentRepository payments,
                            LedgerEntryRepository ledgerEntries,
                            SettlementRepository settlements,
                                                        io.micrometer.core.instrument.MeterRegistry meters) {
        this.payments = payments;
        this.ledgerEntries = ledgerEntries;
        this.settlements = settlements;
                this.meters = meters;
    }

    // ---- CSV entry points (header row required) ----

    public BatchResult ingestPaymentsCsv(Reader csv) throws IOException {
        return ingest("PAYMENT_GATEWAY", PAYMENT_COLUMNS, csv, this::toPaymentRow, this::storePayment);
    }

    public BatchResult ingestLedgerCsv(Reader csv) throws IOException {
        return ingest("INTERNAL_LEDGER", LEDGER_COLUMNS, csv, this::toLedgerRow, this::storeLedger);
    }

    public BatchResult ingestSettlementsCsv(Reader csv) throws IOException {
        return ingest("SETTLEMENT_SYSTEM", SETTLEMENT_COLUMNS, csv, this::toSettlementRow, this::storeSettlement);
    }

    // ---- JSON entry points (already-structured rows) ----

    public BatchResult ingestPayments(List<PaymentIngestRequest> rows) {
        return ingestRequests("PAYMENT_GATEWAY", rows, this::storePayment);
    }

    public BatchResult ingestLedger(List<LedgerIngestRequest> rows) {
        return ingestRequests("INTERNAL_LEDGER", rows, this::storeLedger);
    }

    public BatchResult ingestSettlements(List<SettlementIngestRequest> rows) {
        return ingestRequests("SETTLEMENT_SYSTEM", rows, this::storeSettlement);
    }

    // ---- generic batch driver: valid rows persist, bad rows collect errors ----

    private <T> BatchResult ingest(String sourceType, String[] columns, Reader csv,
                                   RowMapper<T> mapper, RowStore<T> store) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setTrim(true).build();
        List<NumberedRow<T>> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        int rejected = 0;
        List<CSVRecord> records;
        try (CSVParser parser = CSVParser.parse(csv, format)) {
            records = parser.getRecords();
        }
        if (records.isEmpty()) {
            throw new MalformedCsvException("CSV is empty: header row required");
        }
        List<String> header = records.get(0).toList().stream()
                .map(h -> h == null ? "" : h.trim()).toList();
        for (String column : columns) {
            if (!header.contains(column)) {
                throw new MalformedCsvException("Missing required column: " + column);
            }
        }
        for (int i = 1; i < records.size(); i++) {
            CSVRecord record = records.get(i);
            if (isEmptyRow(record)) {
                continue;
            }
            int row = i + 1; // header is row 1
            try {
                rows.add(new NumberedRow<>(mapper.map(asMap(header, record), row), row));
            } catch (RowRejectedException e) {
                errors.add(new RowError(row, e.field(), e.getMessage()));
                rejected++;
            }
        }
        return storeAll(sourceType, rows, errors, rejected, store);
    }

    private static Map<String, String> asMap(List<String> header, CSVRecord record) {
        Map<String, String> map = new java.util.HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String value = i < record.size() ? record.get(i) : "";
            map.put(header.get(i), value == null ? "" : value.trim());
        }
        return map;
    }

    private static boolean isEmptyRow(CSVRecord record) {
        for (int i = 0; i < record.size(); i++) {
            if (record.get(i) != null && !record.get(i).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private <T> BatchResult ingestRequests(String sourceType, List<T> rows, RowStore<T> store) {
        List<NumberedRow<T>> numbered = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            numbered.add(new NumberedRow<>(rows.get(i), i + 1));
        }
        return storeAll(sourceType, numbered, new ArrayList<>(), 0, store);
    }

    private <T> BatchResult storeAll(String sourceType, List<NumberedRow<T>> rows,
                                     List<RowError> errors, int rejected, RowStore<T> store) {
        int accepted = 0;
        int duplicates = 0;
        List<T> acceptedRows = new ArrayList<>();
        for (NumberedRow<T> numbered : rows) {
            StoreOutcome outcome = store.store(numbered.request(), numbered.row(), errors);
            if (outcome == StoreOutcome.ACCEPTED) {
                accepted++;
                acceptedRows.add(numbered.request());
            } else if (outcome == StoreOutcome.DUPLICATE) {
                duplicates++;
            } else {
                rejected++;
            }
        }
        BatchResult result = BatchResult.of(sourceType, java.util.UUID.randomUUID(),
                accepted, duplicates, rejected, errors);
        // P12 basic metrics: outcome counts per source type.
        meters.counter("finrecon.ingest.accepted", "sourceType", sourceType)
                .increment(accepted);
        meters.counter("finrecon.ingest.rejected", "sourceType", sourceType)
                .increment(rejected);
                return result;
    }

            // ---- row mapping (structure) ----

    private PaymentIngestRequest toPaymentRow(Map<String, String> cols, int row) {
        return new PaymentIngestRequest(
                required(cols, "external_txn_id", row), required(cols, "customer_id", row),
                required(cols, "merchant_id", row), required(cols, "amount", row),
                required(cols, "currency", row), required(cols, "status", row),
                required(cols, "event_time", row));
    }

    private LedgerIngestRequest toLedgerRow(Map<String, String> cols, int row) {
        return new LedgerIngestRequest(
                required(cols, "external_txn_id", row), required(cols, "gross_amount", row),
                required(cols, "fee_amount", row), required(cols, "net_amount", row),
                required(cols, "currency", row), required(cols, "posting_status", row),
                required(cols, "posted_at", row));
    }

    private SettlementIngestRequest toSettlementRow(Map<String, String> cols, int row) {
        return new SettlementIngestRequest(
                required(cols, "external_txn_id", row), required(cols, "settled_amount", row),
                required(cols, "fee_amount", row), required(cols, "currency", row),
                required(cols, "settlement_status", row), required(cols, "settlement_date", row),
                required(cols, "batch_id", row));
    }

    private static String required(Map<String, String> cols, String column, int row) {
        String value = cols.get(column);
        if (value == null) {
            throw new MalformedCsvException("Missing required column: " + column);
        }
        return value;
    }

    // ---- row stores (validation + idempotent persist) ----

    private StoreOutcome storePayment(PaymentIngestRequest req, int row, List<RowError> errors) {
        String txn = blank(req.externalTxnId(), "external_txn_id", row, errors);
        String customer = blank(req.customerId(), "customer_id", row, errors);
        String merchant = blank(req.merchantId(), "merchant_id", row, errors);
        BigDecimal amount = money(req.amount(), "amount", row, errors);
        String currency = currency(req.currency(), row, errors);
        String status = blank(req.status(), "status", row, errors);
        OffsetDateTime eventTime = timestamp(req.eventTime(), "event_time", row, errors);
        if (hasRowError(errors, row)) {
            return StoreOutcome.REJECTED;
        }
        if (payments.findByExternalTxnId(txn).isPresent()) {
            return StoreOutcome.DUPLICATE; // P1 UNIQUE(external_txn_id) anchor
        }
        payments.save(new Payment(txn, customer, merchant, amount, currency, status, eventTime));
        return StoreOutcome.ACCEPTED;
    }

    private StoreOutcome storeLedger(LedgerIngestRequest req, int row, List<RowError> errors) {
        Optional<Payment> payment = paymentFor(req.externalTxnId(), row, errors);
        BigDecimal gross = money(req.grossAmount(), "gross_amount", row, errors);
        BigDecimal fee = money(req.feeAmount(), "fee_amount", row, errors);
        BigDecimal net = money(req.netAmount(), "net_amount", row, errors);
        String currency = currency(req.currency(), row, errors);
        String status = blank(req.postingStatus(), "posting_status", row, errors);
        OffsetDateTime postedAt = timestamp(req.postedAt(), "posted_at", row, errors);
        if (hasRowError(errors, row)) {
            return StoreOutcome.REJECTED;
        }
        boolean duplicate = ledgerEntries.findByPaymentPaymentId(payment.get().getPaymentId())
                .stream().anyMatch(e -> e.getGrossAmount().compareTo(gross) == 0
                        && e.getFeeAmount().compareTo(fee) == 0
                        && e.getNetAmount().compareTo(net) == 0
                        && e.getPostedAt().isEqual(postedAt));
        if (duplicate) {
            return StoreOutcome.DUPLICATE;
        }
        ledgerEntries.save(new LedgerEntry(payment.get(), gross, fee, net,
                currency, status, postedAt));
        return StoreOutcome.ACCEPTED;
    }

    private StoreOutcome storeSettlement(SettlementIngestRequest req, int row, List<RowError> errors) {
        Optional<Payment> payment = paymentFor(req.externalTxnId(), row, errors);
        BigDecimal settled = money(req.settledAmount(), "settled_amount", row, errors);
        BigDecimal fee = money(req.feeAmount(), "fee_amount", row, errors);
        String currency = currency(req.currency(), row, errors);
        String status = blank(req.settlementStatus(), "settlement_status", row, errors);
        LocalDate date = date(req.settlementDate(), "settlement_date", row, errors);
        String batch = blank(req.batchId(), "batch_id", row, errors);
        if (hasRowError(errors, row)) {
            return StoreOutcome.REJECTED;
        }
        boolean duplicate = settlements.findByPaymentPaymentId(payment.get().getPaymentId())
                .stream().anyMatch(s -> s.getSettledAmount().compareTo(settled) == 0
                        && s.getSettlementDate().isEqual(date)
                        && s.getBatchId().equals(batch));
        if (duplicate) {
            return StoreOutcome.DUPLICATE;
        }
        settlements.save(new Settlement(payment.get(), settled, fee, currency,
                status, date, batch));
        return StoreOutcome.ACCEPTED;
    }

    // ---- field validators (mirror V1 CHECKs; reject, never coerce) ----

    private Optional<Payment> paymentFor(String txn, int row, List<RowError> errors) {
        if (isBlank(txn)) {
            errors.add(new RowError(row, "external_txn_id", "external_txn_id is required"));
            return Optional.empty();
        }
        Optional<Payment> payment = payments.findByExternalTxnId(txn.trim());
        if (payment.isEmpty()) {
            errors.add(new RowError(row, "external_txn_id",
                    "Unknown external_txn_id: " + txn.trim()));
        }
        return payment;
    }

    private String blank(String value, String field, int row, List<RowError> errors) {
        if (isBlank(value)) {
            errors.add(new RowError(row, field, field + " is required"));
            return null;
        }
        return value.trim();
    }

    private BigDecimal money(String value, String field, int row, List<RowError> errors) {
        if (isBlank(value)) {
            errors.add(new RowError(row, field, field + " is required"));
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(value.trim());
            if (amount.signum() < 0) {
                errors.add(new RowError(row, field, field + " must be >= 0"));
                return null;
            }
            if (amount.scale() > 2) {
                errors.add(new RowError(row, field, field + " allows at most 2 decimals"));
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            errors.add(new RowError(row, field, field + " is not a valid amount: " + value.trim()));
            return null;
        }
    }

    private String currency(String value, int row, List<RowError> errors) {
        if (isBlank(value)) {
            errors.add(new RowError(row, "currency", "currency is required"));
            return null;
        }
        if (!CURRENCY.matcher(value.trim()).matches()) {
            errors.add(new RowError(row, "currency",
                    "currency must be a 3-letter uppercase code: " + value.trim()));
            return null;
        }
        return value.trim();
    }

    private OffsetDateTime timestamp(String value, String field, int row, List<RowError> errors) {
        if (isBlank(value)) {
            errors.add(new RowError(row, field, field + " is required"));
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            errors.add(new RowError(row, field,
                    field + " must be ISO-8601 with offset: " + value.trim()));
            return null;
        }
    }

    private LocalDate date(String value, String field, int row, List<RowError> errors) {
        if (isBlank(value)) {
            errors.add(new RowError(row, field, field + " is required"));
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            errors.add(new RowError(row, field,
                    field + " must be ISO-8601 date (YYYY-MM-DD): " + value.trim()));
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasRowError(List<RowError> errors, int row) {
        return errors.stream().anyMatch(e -> e.row() == row);
    }

    // ---- plumbing ----

    private enum StoreOutcome { ACCEPTED, DUPLICATE, REJECTED }

    private record NumberedRow<T>(T request, int row) {
    }

    private interface RowMapper<T> {
        T map(Map<String, String> columns, int row);
    }

    private interface RowStore<T> {
        StoreOutcome store(T request, int row, List<RowError> errors);
    }

    static final class RowRejectedException extends RuntimeException {
        private final String field;

        RowRejectedException(String field, String message) {
            super(message);
            this.field = field;
        }

        String field() {
            return field;
        }
    }

    public static final class MalformedCsvException extends RuntimeException {
        MalformedCsvException(String message) {
            super(message);
        }
    }
}
