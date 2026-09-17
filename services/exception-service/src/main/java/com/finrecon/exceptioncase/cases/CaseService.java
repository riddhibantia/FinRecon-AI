package com.finrecon.exceptioncase.cases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finrecon.exceptioncase.domain.AuditLog;
import com.finrecon.exceptioncase.domain.AuditLogRepository;
import com.finrecon.exceptioncase.domain.ExceptionEvidence;
import com.finrecon.exceptioncase.domain.ExceptionEvidenceRepository;
import com.finrecon.exceptioncase.domain.LedgerEntry;
import com.finrecon.exceptioncase.domain.LedgerEntryRepository;
import com.finrecon.exceptioncase.domain.Payment;
import com.finrecon.exceptioncase.domain.PaymentRepository;
import com.finrecon.exceptioncase.domain.ReconException;
import com.finrecon.exceptioncase.domain.ReconExceptionRepository;
import com.finrecon.exceptioncase.domain.ReconciliationResult;
import com.finrecon.exceptioncase.domain.ReconciliationResultRepository;
import com.finrecon.exceptioncase.domain.ReconciliationRunRepository;
import com.finrecon.exceptioncase.domain.ResolutionAction;
import com.finrecon.exceptioncase.domain.ResolutionActionRepository;
import com.finrecon.exceptioncase.domain.Settlement;
import com.finrecon.exceptioncase.domain.SettlementRepository;

// P4 case management: open cases from MISMATCHED results, lifecycle
// transitions with resolution actions, and audit rows for every step.
// Category comes 1:1 from the reconciliation mismatch_type; severity is a
// documented P4 placeholder (money moved -> HIGH, else MEDIUM) because the
// master fixes no severity values.
@Service
public class CaseService {

    // Mirrors the P3 engine window so evidence deadlines stay consistent.
    private static final int SETTLEMENT_WINDOW_DAYS = 2;

    private final ReconciliationRunRepository runs;
    private final ReconciliationResultRepository results;
    private final PaymentRepository payments;
    private final LedgerEntryRepository ledgers;
    private final SettlementRepository settlements;
    private final ReconExceptionRepository exceptions;
    private final ExceptionEvidenceRepository evidence;
    private final ResolutionActionRepository actions;
    private final AuditLogRepository audits;

    public CaseService(ReconciliationRunRepository runs,
                       ReconciliationResultRepository results,
                       PaymentRepository payments,
                       LedgerEntryRepository ledgers,
                       SettlementRepository settlements,
                       ReconExceptionRepository exceptions,
                       ExceptionEvidenceRepository evidence,
                       ResolutionActionRepository actions,
                       AuditLogRepository audits) {
        this.runs = runs;
        this.results = results;
        this.payments = payments;
        this.ledgers = ledgers;
        this.settlements = settlements;
        this.exceptions = exceptions;
        this.evidence = evidence;
        this.actions = actions;
        this.audits = audits;
    }

    public record SyncResult(UUID runId, int opened, int skipped) {
    }

    public record CaseSummary(UUID exceptionId, UUID resultId, String category,
                              String severity, String status, String assignedTo,
                              String externalTxnId, BigDecimal amountDifference,
                              OffsetDateTime createdAt) {
    }

    public record SourceRecordView(String sourceType, String recordId, String summary) {
    }

    public record EvidenceView(String sourceType, String sourceRecordId,
                               String fieldName, String expectedValue,
                               String observedValue) {
    }

    public record ActionView(String actionType, String actorType, String actorId,
                             String notes, OffsetDateTime createdAt) {
    }

    public record AuditView(String actorType, String actorId, String action,
                            OffsetDateTime timestamp) {
    }

    public record CaseDetail(UUID exceptionId, String category, String severity,
                             String status, String assignedTo, OffsetDateTime createdAt,
                             OffsetDateTime resolvedAt, UUID resultId, UUID runId,
                             String ruleVersion, String matchStatus, String mismatchType,
                             BigDecimal amountDifference, List<SourceRecordView> sources,
                             List<EvidenceView> evidence, List<ActionView> caseActions,
                             List<AuditView> auditTrail) {
    }

    static String severityFor(BigDecimal amountDifference) {
        return amountDifference != null && amountDifference.signum() != 0 ? "HIGH" : "MEDIUM";
    }

    @Transactional
    public SyncResult syncRun(UUID runId) {
        var run = runs.findById(runId).orElseThrow(
                () -> new CaseNotFoundException("Unknown run: " + runId));
        int opened = 0;
        int skipped = 0;
        for (ReconciliationResult result : results.findByRunRunId(runId)) {
            if (!"MISMATCHED".equals(result.getMatchStatus())
                    || result.getMismatchType() == null) {
                continue;
            }
            if (exceptions.findByResultResultId(result.getResultId()).isPresent()) {
                skipped++;
                continue;
            }
            openCase(run.getRuleVersion(), result);
            opened++;
        }
        audits.save(new AuditLog("SERVICE", "exception-service", "CASES_SYNCED",
                "reconciliation_run", runId.toString(),
                "{\"opened\":" + opened + ",\"skipped\":" + skipped + "}"));
        return new SyncResult(runId, opened, skipped);
    }

    private void openCase(String ruleVersion, ReconciliationResult result) {
        Payment payment = result.getPayment();
        List<LedgerEntry> ledgerRows = ledgers.findByPaymentPaymentId(payment.getPaymentId());
        List<Settlement> settlementRows =
                settlements.findByPaymentPaymentId(payment.getPaymentId());
        ReconException exception = exceptions.save(new ReconException(result,
                result.getMismatchType(), severityFor(result.getAmountDifference())));
        LocalDate deadline = payment.getEventTime().toLocalDate().plusDays(SETTLEMENT_WINDOW_DAYS);
        for (EvidenceBuilder.EvidenceSpec spec : EvidenceBuilder.build(
                result.getMismatchType(), payment, ledgerRows, settlementRows,
                result, deadline)) {
            evidence.save(new ExceptionEvidence(exception, spec.sourceType(),
                    spec.sourceRecordId(), spec.fieldName(), spec.expectedValue(),
                    spec.observedValue()));
        }
        audits.save(new AuditLog("SERVICE", "exception-service", "CASE_OPENED",
                "exception", exception.getExceptionId().toString(),
                "{\"category\":\"" + result.getMismatchType()
                        + "\",\"ruleVersion\":\"" + ruleVersion + "\"}"));
    }

    @Transactional(readOnly = true)
    public List<CaseSummary> queue(String status, String category, String assignedTo) {
        List<ReconException> rows;
        if (status != null) {
            rows = exceptions.findByStatus(status);
        } else if (category != null) {
            rows = exceptions.findByCategory(category);
        } else if (assignedTo != null) {
            rows = exceptions.findByAssignedTo(assignedTo);
        } else {
            rows = exceptions.findAll();
        }
        return rows.stream().map(e -> new CaseSummary(e.getExceptionId(),
                e.getResult().getResultId(), e.getCategory(), e.getSeverity(),
                e.getStatus(), e.getAssignedTo(),
                e.getResult().getPayment().getExternalTxnId(),
                e.getResult().getAmountDifference(), e.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public CaseDetail detail(UUID exceptionId) {
        ReconException exception = getCase(exceptionId);
        ReconciliationResult result = exception.getResult();
        Payment payment = result.getPayment();
        List<SourceRecordView> sources = new ArrayList<>();
        sources.add(new SourceRecordView("payment_gateway",
                payment.getPaymentId().toString(),
                payment.getExternalTxnId() + " " + payment.getAmount()
                        + " " + payment.getCurrency() + " " + payment.getStatus()));
        for (LedgerEntry ledger : ledgers.findByPaymentPaymentId(payment.getPaymentId())) {
            sources.add(new SourceRecordView("ledger",
                    ledger.getLedgerEntryId().toString(), "gross="
                            + ledger.getGrossAmount() + " fee=" + ledger.getFeeAmount()
                            + " net=" + ledger.getNetAmount() + " " + ledger.getPostingStatus()));
        }
        for (Settlement settlement : settlements.findByPaymentPaymentId(payment.getPaymentId())) {
            sources.add(new SourceRecordView("settlement",
                    settlement.getSettlementId().toString(), "settled="
                            + settlement.getSettledAmount() + " batch="
                            + settlement.getBatchId() + " " + settlement.getSettlementStatus()));
        }
        return new CaseDetail(exception.getExceptionId(), exception.getCategory(),
                exception.getSeverity(), exception.getStatus(), exception.getAssignedTo(),
                exception.getCreatedAt(), exception.getResolvedAt(),
                result.getResultId(), result.getRun().getRunId(),
                result.getRun().getRuleVersion(), result.getMatchStatus(),
                result.getMismatchType(), result.getAmountDifference(), List.copyOf(sources),
                evidence.findByExceptionExceptionId(exceptionId).stream()
                        .map(v -> new EvidenceView(v.getSourceType(), v.getSourceRecordId(),
                                v.getFieldName(), v.getExpectedValue(), v.getObservedValue()))
                        .toList(),
                actions.findByExceptionExceptionIdOrderByCreatedAtAsc(exceptionId).stream()
                        .map(a -> new ActionView(a.getActionType(), a.getActorType(),
                                a.getActorId(), a.getNotes(), a.getCreatedAt()))
                        .toList(),
                audits.findByEntityTypeAndEntityIdOrderByTimestampAsc(
                                "exception", exceptionId.toString()).stream()
                        .map(a -> new AuditView(a.getActorType(), a.getActorId(),
                                a.getAction(), a.getTimestamp()))
                        .toList());
    }

    @Transactional
    public CaseSummary assign(UUID exceptionId, String analystId, String actorId) {
        requireText(analystId, "assignedTo");
        ReconException exception = getCase(exceptionId);
        exception.assign(analystId.trim());
        actions.save(new ResolutionAction(exception, "ASSIGN", "ANALYST",
                actor(actorId, analystId), "Assigned to " + analystId.trim()));
        audit(exception, "CASE_ASSIGNED", actor(actorId, analystId));
        return summaryOf(exception);
    }

    @Transactional
    public CaseSummary resolve(UUID exceptionId, String actionType, String actorType,
                               String actorId, String notes) {
        requireText(actionType, "actionType");
        ReconException exception = getCase(exceptionId);
        exception.resolve();
        actions.save(new ResolutionAction(exception, actionType.trim(),
                actorType == null ? "ANALYST" : actorType.trim(),
                actor(actorId, exception.getAssignedTo()), notes));
        audit(exception, "CASE_RESOLVED", actor(actorId, exception.getAssignedTo()));
        return summaryOf(exception);
    }

    @Transactional
    public CaseSummary escalate(UUID exceptionId, String actorType, String actorId,
                                String notes) {
        ReconException exception = getCase(exceptionId);
        exception.escalate();
        actions.save(new ResolutionAction(exception, "ESCALATE",
                actorType == null ? "ANALYST" : actorType.trim(),
                actor(actorId, exception.getAssignedTo()), notes));
        audit(exception, "CASE_ESCALATED", actor(actorId, exception.getAssignedTo()));
        return summaryOf(exception);
    }

    private ReconException getCase(UUID exceptionId) {
        return exceptions.findById(exceptionId).orElseThrow(
                () -> new CaseNotFoundException("Unknown case: " + exceptionId));
    }

    private CaseSummary summaryOf(ReconException exception) {
        return new CaseSummary(exception.getExceptionId(),
                exception.getResult().getResultId(), exception.getCategory(),
                exception.getSeverity(), exception.getStatus(), exception.getAssignedTo(),
                exception.getResult().getPayment().getExternalTxnId(),
                exception.getResult().getAmountDifference(), exception.getCreatedAt());
    }

    private void audit(ReconException exception, String action, String actorId) {
        audits.save(new AuditLog("ANALYST", actorId, action,
                "exception", exception.getExceptionId().toString(), "{}"));
    }

    private static String actor(String actorId, String fallback) {
        if (actorId != null && !actorId.isBlank()) {
            return actorId.trim();
        }
        return fallback == null ? "unknown" : fallback;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadCaseRequestException(field + " is required");
        }
    }

    public static final class CaseNotFoundException extends RuntimeException {
        public CaseNotFoundException(String message) {
            super(message);
        }
    }

    public static final class BadCaseRequestException extends RuntimeException {
        public BadCaseRequestException(String message) {
            super(message);
        }
    }
}
