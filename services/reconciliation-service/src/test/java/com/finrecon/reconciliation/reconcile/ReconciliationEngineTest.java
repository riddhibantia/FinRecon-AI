package com.finrecon.reconciliation.reconcile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.finrecon.reconciliation.domain.LedgerEntry;
import com.finrecon.reconciliation.domain.Payment;
import com.finrecon.reconciliation.domain.Settlement;

// P3 engine tests. Pure functions, no Spring, no database: the same input
// must always produce the same outcome. Covers MATCHED plus every check.
class ReconciliationEngineTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-09-01T10:00:00+05:30");

    private static Payment payment(String txn, String amount, String currency, String status) {
        return new Payment(txn, "CUST-1", "MERCH-1", new BigDecimal(amount),
                currency, status, T0);
    }

    private static LedgerEntry ledger(Payment payment, String gross, String fee,
                                      String net, String currency, String status) {
        return new LedgerEntry(payment, new BigDecimal(gross), new BigDecimal(fee),
                new BigDecimal(net), currency, status, T0.plusHours(1));
    }

    private static Settlement settlement(Payment payment, String settled, String fee,
                                         String currency, String status, int dayOffset,
                                         String batch) {
        return new Settlement(payment, new BigDecimal(settled), new BigDecimal(fee),
                currency, status, LocalDate.parse("2026-09-01").plusDays(dayOffset), batch);
    }

    private static Payment base() {
        return payment("TXN-1", "100.00", "INR", "SUCCESS");
    }

    private static LedgerEntry baseLedger(Payment payment) {
        return ledger(payment, "100.00", "5.00", "95.00", "INR", "SUCCESS");
    }

    private static Settlement baseSettlement(Payment payment) {
        return settlement(payment, "95.00", "5.00", "INR", "SUCCESS", 1, "BATCH-1");
    }

    @Test
    void matchedWhenEverythingAgrees() {
        Payment payment = base();
        ReconciliationEngine.Outcome outcome = ReconciliationEngine.reconcile(
                payment, List.of(baseLedger(payment)), List.of(baseSettlement(payment)));
        assertEquals("MATCHED", outcome.matchStatus());
        assertEquals(null, outcome.mismatchType());
    }

    @Test
    void duplicateSettlementWinsOverAllOtherChecks() {
        Payment payment = base();
        ReconciliationEngine.Outcome outcome = ReconciliationEngine.reconcile(payment,
                List.of(baseLedger(payment)),
                List.of(baseSettlement(payment), baseSettlement(payment)));
        assertEquals("MISMATCHED", outcome.matchStatus());
        assertEquals("DUPLICATE_SETTLEMENT", outcome.mismatchType());
        assertEquals(0, outcome.amountDifference().compareTo(new BigDecimal("-95.00")));
    }

    @Test
    void missingSettlement() {
        Payment payment = base();
        ReconciliationEngine.Outcome outcome = ReconciliationEngine.reconcile(
                payment, List.of(baseLedger(payment)), List.of());
        assertEquals("MISSING_SETTLEMENT", outcome.mismatchType());
        assertEquals(0, outcome.amountDifference().compareTo(new BigDecimal("95.00")));
    }

    @Test
    void grossMismatchAgainstGateway() {
        Payment payment = base();
        LedgerEntry heavy = ledger(payment, "110.00", "5.00", "105.00", "INR", "SUCCESS");
        Settlement settled = settlement(payment, "105.00", "5.00", "INR", "SUCCESS", 1, "B1");
        ReconciliationEngine.Outcome outcome =
                ReconciliationEngine.reconcile(payment, List.of(heavy), List.of(settled));
        assertEquals("AMOUNT_MISMATCH", outcome.mismatchType());
        assertEquals(0, outcome.amountDifference().compareTo(new BigDecimal("10.00")));
    }

    @Test
    void missingLedgerIsAmountMismatch() {
        Payment payment = base();
        ReconciliationEngine.Outcome outcome = ReconciliationEngine.reconcile(
                payment, List.of(), List.of(baseSettlement(payment)));
        assertEquals("AMOUNT_MISMATCH", outcome.mismatchType());
    }

    @Test
    void ambiguousLedgersAreUnknown() {
        Payment payment = base();
        ReconciliationEngine.Outcome outcome = ReconciliationEngine.reconcile(payment,
                List.of(baseLedger(payment), baseLedger(payment)),
                List.of(baseSettlement(payment)));
        assertEquals("UNKNOWN_EXCEPTION", outcome.mismatchType());
    }

    @Test
    void feeVariance() {
        Payment payment = base();
        Settlement pricey = settlement(payment, "95.00", "8.00", "INR", "SUCCESS", 1, "B1");
        ReconciliationEngine.Outcome outcome = ReconciliationEngine.reconcile(
                payment, List.of(baseLedger(payment)), List.of(pricey));
        assertEquals("FEE_VARIANCE", outcome.mismatchType());
        assertEquals(0, outcome.amountDifference().compareTo(new BigDecimal("3.00")));
    }

    @Test
    void partialSettlement() {
        Payment payment = base();
        Settlement partial = settlement(payment, "50.00", "5.00", "INR", "SUCCESS", 1, "B1");
        ReconciliationEngine.Outcome outcome = ReconciliationEngine.reconcile(
                payment, List.of(baseLedger(payment)), List.of(partial));
        assertEquals("PARTIAL_SETTLEMENT", outcome.mismatchType());
        assertEquals(0, outcome.amountDifference().compareTo(new BigDecimal("45.00")));
    }

    @Test
    void overSettlementIsAmountMismatch() {
        Payment payment = base();
        Settlement over = settlement(payment, "99.00", "5.00", "INR", "SUCCESS", 1, "B1");
        ReconciliationEngine.Outcome outcome = ReconciliationEngine.reconcile(
                payment, List.of(baseLedger(payment)), List.of(over));
        assertEquals("AMOUNT_MISMATCH", outcome.mismatchType());
    }

    @Test
    void currencyDriftIsFxVariance() {
        Payment payment = base();
        Settlement fx = settlement(payment, "95.00", "5.00", "USD", "SUCCESS", 1, "B1");
        ReconciliationEngine.Outcome outcome = ReconciliationEngine.reconcile(
                payment, List.of(baseLedger(payment)), List.of(fx));
        assertEquals("FX_VARIANCE", outcome.mismatchType());
    }

    @Test
    void statusDriftIsStatusMismatch() {
        Payment payment = base();
        Settlement failed = settlement(payment, "95.00", "5.00", "INR", "FAILED", 1, "B1");
        ReconciliationEngine.Outcome outcome = ReconciliationEngine.reconcile(
                payment, List.of(baseLedger(payment)), List.of(failed));
        assertEquals("STATUS_MISMATCH", outcome.mismatchType());
    }

    @Test
    void settlementBeyondWindowIsLate() {
        Payment payment = base();
        Settlement late = settlement(payment, "95.00", "5.00", "INR", "SUCCESS", 5, "B1");
        ReconciliationEngine.Outcome outcome = ReconciliationEngine.reconcile(
                payment, List.of(baseLedger(payment)), List.of(late));
        assertEquals("LATE_SETTLEMENT", outcome.mismatchType());
    }

    @Test
    void settlementOnWindowEdgeIsOnTime() {
        Payment payment = base();
        Settlement edge = settlement(payment, "95.00", "5.00", "INR", "SUCCESS", 2, "B1");
        ReconciliationEngine.Outcome outcome = ReconciliationEngine.reconcile(
                payment, List.of(baseLedger(payment)), List.of(edge));
        assertEquals("MATCHED", outcome.matchStatus());
    }

    @Test
    void fallbackMatchesOnMerchantAmountCurrencyAndWindow() {
        Payment payment = base();
        Optional<Payment> hit = ReconciliationEngine.matchPaymentFor(
                "SOME-OTHER-REF", "MERCH-1", new BigDecimal("100.00"), "INR",
                T0.plusHours(3), List.of(payment));
        assertTrue(hit.isPresent());
    }

    @Test
    void fallbackRefusesAmbiguousCandidates() {
        Payment first = base();
        Payment second = payment("TXN-2", "100.00", "INR", "SUCCESS");
        Optional<Payment> hit = ReconciliationEngine.matchPaymentFor(
                null, "MERCH-1", new BigDecimal("100.00"), "INR", T0.plusHours(3),
                List.of(first, second));
        assertTrue(hit.isEmpty());
    }

    @Test
    void fallbackRejectsOutsideWindow() {
        Payment payment = base();
        Optional<Payment> hit = ReconciliationEngine.matchPaymentFor(
                null, "MERCH-1", new BigDecimal("100.00"), "INR", T0.plusDays(5),
                List.of(payment));
        assertTrue(hit.isEmpty());
    }
}
