package com.finrecon.exceptioncase.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.finrecon.exceptioncase.domain.AuditLogRepository;
import com.finrecon.exceptioncase.domain.ExceptionEvidenceRepository;
import com.finrecon.exceptioncase.domain.LedgerEntry;
import com.finrecon.exceptioncase.domain.LedgerEntryRepository;
import com.finrecon.exceptioncase.domain.Payment;
import com.finrecon.exceptioncase.domain.PaymentRepository;
import com.finrecon.exceptioncase.domain.ReconExceptionRepository;
import com.finrecon.exceptioncase.domain.ReconciliationResult;
import com.finrecon.exceptioncase.domain.ReconciliationResultRepository;
import com.finrecon.exceptioncase.domain.ReconciliationRun;
import com.finrecon.exceptioncase.domain.ReconciliationRunRepository;
import com.finrecon.exceptioncase.domain.ResolutionActionRepository;
import com.finrecon.exceptioncase.domain.Settlement;
import com.finrecon.exceptioncase.domain.SettlementRepository;

// P4 service tests on H2: sync, idempotent re-sync, lifecycle, evidence,
// actions, audit trail. No Postgres needed.
@SpringBootTest
class CaseServiceTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-09-01T10:00:00+05:30");

    @Autowired
    private CaseService cases;

    @Autowired
    private ReconciliationRunRepository runs;

    @Autowired
    private ReconciliationResultRepository results;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private LedgerEntryRepository ledgers;

    @Autowired
    private SettlementRepository settlements;

    @Autowired
    private ReconExceptionRepository exceptions;

    @Autowired
    private ExceptionEvidenceRepository evidence;

    @Autowired
    private ResolutionActionRepository actions;

    @Autowired
    private AuditLogRepository audits;

    private UUID runId;

    @BeforeEach
    void seed() {
        audits.deleteAll();
        actions.deleteAll();
        evidence.deleteAll();
        exceptions.deleteAll();
        results.deleteAll();
        runs.deleteAll();
        settlements.deleteAll();
        ledgers.deleteAll();
        payments.deleteAll();

        ReconciliationRun run = runs.save(new ReconciliationRun("P4-FIXTURES", "1.0.0"));
        run.complete();
        runId = run.getRunId();

        // Mismatched fee case with full source chain.
        Payment fee = payments.save(new Payment("TXN-P4-FEE", "C1", "M1",
                new BigDecimal("100.00"), "INR", "SUCCESS", T0));
        ledgers.save(new LedgerEntry(fee, new BigDecimal("100.00"),
                new BigDecimal("5.00"), new BigDecimal("95.00"), "INR", "SUCCESS",
                T0.plusHours(1)));
        settlements.save(new Settlement(fee, new BigDecimal("95.00"),
                new BigDecimal("8.00"), "INR", "SUCCESS",
                LocalDate.parse("2026-09-02"), "BATCH-P4"));
        results.save(new ReconciliationResult(run, fee, "MISMATCHED",
                "FEE_VARIANCE", new BigDecimal("3.00")));

        // Missing-settlement case: payment + ledger only.
        Payment miss = payments.save(new Payment("TXN-P4-MISS", "C1", "M1",
                new BigDecimal("50.00"), "INR", "SUCCESS", T0));
        ledgers.save(new LedgerEntry(miss, new BigDecimal("50.00"),
                new BigDecimal("2.00"), new BigDecimal("48.00"), "INR", "SUCCESS",
                T0.plusHours(1)));
        results.save(new ReconciliationResult(run, miss, "MISMATCHED",
                "MISSING_SETTLEMENT", new BigDecimal("48.00")));

        // Matched result must never become a case.
        Payment ok = payments.save(new Payment("TXN-P4-OK", "C1", "M1",
                new BigDecimal("10.00"), "INR", "SUCCESS", T0));
        results.save(new ReconciliationResult(run, ok, "MATCHED", null, null));
    }

    @Test
    void syncOpensCasesWithEvidenceAndSkipsMatched() {
        CaseService.SyncResult sync = cases.syncRun(runId);

        assertEquals(2, sync.opened());
        assertEquals(0, sync.skipped());
        assertEquals(2, exceptions.count());
        assertTrue(evidence.count() >= 2);
        // Fee case carries the quoted comparison values.
        var feeCase = exceptions.findAll().stream()
                .filter(e -> "FEE_VARIANCE".equals(e.getCategory()))
                .findFirst().orElseThrow();
        assertEquals("HIGH", feeCase.getSeverity());
        assertEquals("OPEN", feeCase.getStatus());
        var feeRows = evidence.findByExceptionExceptionId(feeCase.getExceptionId());
        assertTrue(feeRows.stream().anyMatch(
                r -> "fee_amount".equals(r.getFieldName())
                        && "5.00".equals(r.getExpectedValue())
                        && "8.00".equals(r.getObservedValue())));
    }

    @Test
    void resyncIsIdempotent() {
        cases.syncRun(runId);
        CaseService.SyncResult again = cases.syncRun(runId);
        assertEquals(0, again.opened());
        assertEquals(2, again.skipped());
        assertEquals(2, exceptions.count());
    }

    @Test
    void lifecycleAssignResolveWithActionAndAudit() {
        cases.syncRun(runId);
        var target = exceptions.findAll().get(0);
        UUID id = target.getExceptionId();

        var assigned = cases.assign(id, "analyst-1", "analyst-1");
        assertEquals("INVESTIGATING", assigned.status());
        assertEquals("analyst-1", assigned.assignedTo());

        var resolved = cases.resolve(id, "FEE_ADJUSTED", "ANALYST", "analyst-1",
                "Fee corrected with merchant");
        assertEquals("RESOLVED", resolved.status());

        var detail = cases.detail(id);
        assertEquals(2, detail.caseActions().size());
        assertEquals("ASSIGN", detail.caseActions().get(0).actionType());
        assertEquals("FEE_ADJUSTED", detail.caseActions().get(1).actionType());
        assertTrue(detail.auditTrail().size() >= 3);
        assertTrue(detail.sources().size() >= 3);
    }

    @Test
    void illegalTransitionsAreRejected() {
        cases.syncRun(runId);
        UUID id = exceptions.findAll().get(0).getExceptionId();

        // Resolve before assign is illegal.
        assertThrows(IllegalStateException.class,
                () -> cases.resolve(id, "X", "ANALYST", "a1", "n"));
        // Assign twice is illegal.
        cases.assign(id, "analyst-1", "analyst-1");
        assertThrows(IllegalStateException.class,
                () -> cases.assign(id, "analyst-2", "analyst-1"));
    }

    @Test
    void escalateFromOpenAndQueueFilters() {
        cases.syncRun(runId);
        UUID id = exceptions.findAll().get(0).getExceptionId();
        cases.escalate(id, "ANALYST", "lead-1", "Needs manager review");

        assertEquals("ESCALATED",
                exceptions.findById(id).orElseThrow().getStatus());
        assertEquals(1, cases.queue("ESCALATED", null, null).size());
        assertEquals(1, cases.queue(null, "FEE_VARIANCE", null).size());
        assertEquals(1, cases.queue("OPEN", null, null).size());
    }

    @Test
    void unknownCaseAndRunAreReported() {
        UUID unknown = UUID.randomUUID();
        assertThrows(CaseService.CaseNotFoundException.class,
                () -> cases.detail(unknown));
        assertThrows(CaseService.CaseNotFoundException.class,
                () -> cases.syncRun(unknown));
    }
}
