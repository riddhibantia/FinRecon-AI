package com.finrecon.exceptioncase.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.finrecon.exceptioncase.domain.LedgerEntry;
import com.finrecon.exceptioncase.domain.Payment;
import com.finrecon.exceptioncase.domain.ReconciliationResult;
import com.finrecon.exceptioncase.domain.Settlement;

// P4 evidence tests. Pure function: same inputs, same rows, quoting only
// stored values (master FR-08).
class EvidenceBuilderTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-09-01T10:00:00+05:30");

    private Payment payment() {
        return new Payment("TXN-E1", "C1", "M1", new BigDecimal("100.00"),
                "INR", "SUCCESS", T0);
    }

    private ReconciliationResult result(Payment payment) {
        return new ReconciliationResult(null, payment, "MISMATCHED",
                "FEE_VARIANCE", new BigDecimal("3.00"));
    }

    @Test
    void feeVarianceQuotesBothObservedFees() {
        Payment payment = payment();
        LedgerEntry ledger = new LedgerEntry(payment, new BigDecimal("100.00"),
                new BigDecimal("5.00"), new BigDecimal("95.00"), "INR", "SUCCESS",
                T0.plusHours(1));
        Settlement settlement = new Settlement(payment, new BigDecimal("95.00"),
                new BigDecimal("8.00"), "INR", "SUCCESS",
                LocalDate.parse("2026-09-02"), "BATCH-1");

        List<EvidenceBuilder.EvidenceSpec> rows = EvidenceBuilder.build("FEE_VARIANCE",
                payment, List.of(ledger), List.of(settlement),
                result(payment), LocalDate.parse("2026-09-03"));

        var fee = rows.stream().filter(r -> "fee_amount".equals(r.fieldName()))
                .findFirst().orElseThrow();
        assertEquals("5.00", fee.expectedValue());
        assertEquals("8.00", fee.observedValue());
        assertTrue(rows.stream().anyMatch(r -> "payment_gateway".equals(r.sourceType())));
        assertTrue(rows.stream().anyMatch(r -> "reconciliation_result".equals(r.sourceType())));
    }

    @Test
    void missingSettlementRecordsAbsenceWithoutInventing() {
        Payment payment = payment();
        List<EvidenceBuilder.EvidenceSpec> rows = EvidenceBuilder.build(
                "MISSING_SETTLEMENT", payment, List.of(), List.of(),
                result(payment), LocalDate.parse("2026-09-03"));

        var row = rows.stream().filter(r -> "settlement".equals(r.fieldName()))
                .findFirst().orElseThrow();
        assertEquals("present", row.expectedValue());
        assertEquals("absent", row.observedValue());
    }
}
