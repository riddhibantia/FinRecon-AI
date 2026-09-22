package com.finrecon.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import com.finrecon.shared.domain.LedgerEntry;
import com.finrecon.shared.domain.LedgerEntryRepository;
import com.finrecon.shared.domain.Payment;
import com.finrecon.shared.domain.PaymentRepository;
import com.finrecon.shared.domain.ReconciliationResultRepository;
import com.finrecon.shared.domain.ReconciliationRunRepository;
import com.finrecon.shared.domain.Settlement;
import com.finrecon.shared.domain.SettlementRepository;
import com.finrecon.reconciliation.reconcile.ReconciliationService;

// P3 service tests on H2: ten payments covering MATCHED plus all nine
// taxonomy categories, run persistence with rule version, and rerun
// determinism. No Postgres needed.
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReconciliationServiceTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-09-01T10:00:00+05:30");
    private static final LocalDate DAY = LocalDate.parse("2026-09-01");

    @Autowired
    private ReconciliationService service;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private LedgerEntryRepository ledgers;

    @Autowired
    private SettlementRepository settlements;

    @Autowired
    private ReconciliationRunRepository runs;

    @Autowired
    private ReconciliationResultRepository results;

    @BeforeEach
    void seed() {
        results.deleteAll();
        runs.deleteAll();
        settlements.deleteAll();
        ledgers.deleteAll();
        payments.deleteAll();

        trio("TXN-OK", "100.00", "5.00", "95.00", "INR", "SUCCESS", "SUCCESS", 1, 1);
        trio("TXN-AMT", "100.00", "5.00", "95.00", "INR", "SUCCESS", "SUCCESS", 1, 1);
        overgross("TXN-AMT");
        trio("TXN-FEE", "100.00", "5.00", "95.00", "INR", "SUCCESS", "SUCCESS", 1, 1);
        overfee("TXN-FEE");
        trio("TXN-PART", "100.00", "5.00", "95.00", "INR", "SUCCESS", "SUCCESS", 1, 1);
        partial("TXN-PART");
        // TXN-MISS: payment + ledger, no settlement
        Payment miss = trio("TXN-MISS", "100.00", "5.00", "95.00", "INR", "SUCCESS", "SUCCESS", 0, 1);
        settlements.deleteAll(settlements.findByPaymentPaymentId(miss.getPaymentId()));
        // TXN-DUP: payment + ledger + two settlements
        Payment dup = trio("TXN-DUP", "100.00", "5.00", "95.00", "INR", "SUCCESS", "SUCCESS", 1, 1);
        addSettlement(dup, "95.00", "5.00", "INR", "SUCCESS", 1, "BATCH-DUP2");
        // TXN-FX / TXN-STATUS / TXN-LATE variants
        Payment fx = trio("TXN-FX", "100.00", "5.00", "95.00", "INR", "SUCCESS", "SUCCESS", 1, 1);
        settlements.deleteAll(settlements.findByPaymentPaymentId(fx.getPaymentId()));
        addSettlement(fx, "95.00", "5.00", "USD", "SUCCESS", 1, "BATCH-FX");
        Payment st = trio("TXN-ST", "100.00", "5.00", "95.00", "INR", "SUCCESS", "SUCCESS", 1, 1);
        settlements.deleteAll(settlements.findByPaymentPaymentId(st.getPaymentId()));
        addSettlement(st, "95.00", "5.00", "INR", "FAILED", 1, "BATCH-ST");
        Payment late = trio("TXN-LT", "100.00", "5.00", "95.00", "INR", "SUCCESS", "SUCCESS", 1, 1);
        settlements.deleteAll(settlements.findByPaymentPaymentId(late.getPaymentId()));
        addSettlement(late, "95.00", "5.00", "INR", "SUCCESS", 5, "BATCH-LT");
        // TXN-UNK: two ledger rows (ambiguous books)
        Payment unk = trio("TXN-UNK", "100.00", "5.00", "95.00", "INR", "SUCCESS", "SUCCESS", 1, 1);
        ledgers.save(new LedgerEntry(unk, new BigDecimal("100.00"), new BigDecimal("5.00"),
                new BigDecimal("95.00"), "INR", "SUCCESS", T0.plusHours(2)));
    }

    private Payment trio(String txn, String amount, String fee, String net,
                         String currency, String gwStatus, String otherStatus,
                         int settlementCount, int ledgerCount) {
        Payment payment = payments.save(new Payment(txn, "CUST-1", "MERCH-1",
                new BigDecimal(amount), currency, gwStatus, T0));
        for (int i = 0; i < ledgerCount; i++) {
            ledgers.save(new LedgerEntry(payment, new BigDecimal(amount),
                    new BigDecimal(fee), new BigDecimal(net), currency,
                    otherStatus, T0.plusHours(1)));
        }
        for (int i = 0; i < settlementCount; i++) {
            addSettlement(payment, net, fee, currency, otherStatus, 1, "BATCH-" + txn);
        }
        return payment;
    }

    private void addSettlement(Payment payment, String settled, String fee,
                               String currency, String status, int dayOffset, String batch) {
        settlements.save(new Settlement(payment, new BigDecimal(settled),
                new BigDecimal(fee), currency, status, DAY.plusDays(dayOffset), batch));
    }

    private void overgross(String txn) {
        Payment payment = payments.findByExternalTxnId(txn).orElseThrow();
        ledgers.deleteAll(ledgers.findByPaymentPaymentId(payment.getPaymentId()));
        ledgers.save(new LedgerEntry(payment, new BigDecimal("110.00"),
                new BigDecimal("5.00"), new BigDecimal("105.00"), "INR", "SUCCESS",
                T0.plusHours(1)));
        settlements.deleteAll(settlements.findByPaymentPaymentId(payment.getPaymentId()));
        addSettlement(payment, "105.00", "5.00", "INR", "SUCCESS", 1, "BATCH-" + txn);
    }

    private void overfee(String txn) {
        Payment payment = payments.findByExternalTxnId(txn).orElseThrow();
        settlements.deleteAll(settlements.findByPaymentPaymentId(payment.getPaymentId()));
        addSettlement(payment, "95.00", "8.00", "INR", "SUCCESS", 1, "BATCH-" + txn);
    }

    private void partial(String txn) {
        Payment payment = payments.findByExternalTxnId(txn).orElseThrow();
        settlements.deleteAll(settlements.findByPaymentPaymentId(payment.getPaymentId()));
        addSettlement(payment, "50.00", "5.00", "INR", "SUCCESS", 1, "BATCH-" + txn);
    }

    private Map<String, String> categories(ReconciliationService.RunSummary summary) {
        return service.resultsFor(summary.runId()).stream().collect(Collectors.toMap(
                ReconciliationService.ResultView::externalTxnId,
                v -> v.mismatchType() == null ? "MATCHED" : v.mismatchType()));
    }

    @Test
    void runCoversMatchedPlusAllNineCategories() {
        ReconciliationService.RunSummary summary = service.run("P3-FIXTURES");

        assertEquals("COMPLETED", summary.status());
        assertEquals("1.0.0", summary.ruleVersion());
        assertEquals(10, summary.total());
        assertEquals(1, summary.matched());
        assertEquals(9, summary.mismatched());

        Map<String, String> byTxn = categories(summary);
        assertEquals("MATCHED", byTxn.get("TXN-OK"));
        assertEquals("AMOUNT_MISMATCH", byTxn.get("TXN-AMT"));
        assertEquals("FEE_VARIANCE", byTxn.get("TXN-FEE"));
        assertEquals("PARTIAL_SETTLEMENT", byTxn.get("TXN-PART"));
        assertEquals("MISSING_SETTLEMENT", byTxn.get("TXN-MISS"));
        assertEquals("DUPLICATE_SETTLEMENT", byTxn.get("TXN-DUP"));
        assertEquals("FX_VARIANCE", byTxn.get("TXN-FX"));
        assertEquals("STATUS_MISMATCH", byTxn.get("TXN-ST"));
        assertEquals("LATE_SETTLEMENT", byTxn.get("TXN-LT"));
        assertEquals("UNKNOWN_EXCEPTION", byTxn.get("TXN-UNK"));
    }

    @Test
    void rerunsAreDeterministic() {
        Map<String, String> first = categories(service.run("FIRST"));
        Map<String, String> second = categories(service.run("SECOND"));
        assertEquals(first, second);
        assertEquals(2, runs.count());
        assertEquals(20, results.count());
    }

    @Test
    void runIsPersistedWithSourceSet() {
        ReconciliationService.RunSummary summary = service.run("NIGHTLY");
        var run = runs.findById(summary.runId()).orElseThrow();
        assertEquals("NIGHTLY", run.getSourceSet());
        assertEquals("COMPLETED", run.getStatus());
        assertTrue(run.getCompletedAt().isAfter(run.getStartedAt())
                || run.getCompletedAt().isEqual(run.getStartedAt()));
        assertEquals(10, service.resultsFor(summary.runId()).size());
    }

    @Test
    void partialDifferenceIsReported() {
        ReconciliationService.RunSummary summary = service.run("DIFF");
        var partial = service.resultsFor(summary.runId()).stream()
                .filter(v -> "TXN-PART".equals(v.externalTxnId()))
                .findFirst().orElseThrow();
        assertEquals(0, partial.amountDifference().compareTo(new BigDecimal("45.00")));
    }
}
