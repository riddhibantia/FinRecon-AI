package com.finrecon.reconciliation.reconcile;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finrecon.reconciliation.domain.LedgerEntry;
import com.finrecon.reconciliation.domain.LedgerEntryRepository;
import com.finrecon.reconciliation.domain.Payment;
import com.finrecon.reconciliation.domain.PaymentRepository;
import com.finrecon.reconciliation.domain.ReconciliationResult;
import com.finrecon.reconciliation.domain.ReconciliationResultRepository;
import com.finrecon.reconciliation.domain.ReconciliationRun;
import com.finrecon.reconciliation.domain.ReconciliationRunRepository;
import com.finrecon.reconciliation.domain.Settlement;
import com.finrecon.reconciliation.domain.SettlementRepository;

// P3 orchestration: one run row per invocation, one result row per payment,
// engine rule version recorded for reproducibility. Failed runs are marked
// FAILED. Reads source records only; never writes them.
@Service
public class ReconciliationService {

    private final PaymentRepository payments;
    private final LedgerEntryRepository ledgerEntries;
    private final SettlementRepository settlements;
    private final ReconciliationRunRepository runs;
    private final ReconciliationResultRepository results;

    public ReconciliationService(PaymentRepository payments,
                                 LedgerEntryRepository ledgerEntries,
                                 SettlementRepository settlements,
                                 ReconciliationRunRepository runs,
                                 ReconciliationResultRepository results) {
        this.payments = payments;
        this.ledgerEntries = ledgerEntries;
        this.settlements = settlements;
        this.runs = runs;
        this.results = results;
    }

    public record RunSummary(UUID runId, String status, String ruleVersion,
                             long total, long matched, long mismatched) {
    }

    @Transactional
    public RunSummary run(String sourceSet) {
        ReconciliationRun run = runs.save(
                new ReconciliationRun(sourceSet, ReconciliationEngine.RULE_VERSION));
        try {
            List<Payment> candidates = payments.findAll();
            long matched = 0;
            for (Payment payment : candidates) {
                // Exact resolution via canonical linkage (P2 enforces the
                // payment reference, P1 enforces the FK). The constrained
                // fallback matcher (ReconciliationEngine.matchPaymentFor)
                // stays available for reference-less feeds; it is specified
                // and tested but not on this pipeline path by design.
                List<LedgerEntry> ledgers =
                        ledgerEntries.findByPaymentPaymentId(payment.getPaymentId());
                List<Settlement> linked =
                        settlements.findByPaymentPaymentId(payment.getPaymentId());
                ReconciliationEngine.Outcome outcome =
                        ReconciliationEngine.reconcile(payment, ledgers, linked);
                results.save(new ReconciliationResult(run, payment,
                        outcome.matchStatus(), outcome.mismatchType(),
                        outcome.amountDifference()));
                if ("MATCHED".equals(outcome.matchStatus())) {
                    matched++;
                }
            }
            run.complete();
            long total = candidates.size();
            return new RunSummary(run.getRunId(), run.getStatus(),
                    run.getRuleVersion(), total, matched, total - matched);
        } catch (RuntimeException e) {
            run.fail();
            throw e;
        }
    }

    // Flat read view: safe to serialize (no lazy proxies leak to JSON).
    public record ResultView(UUID resultId, UUID runId, UUID paymentId,
                             String externalTxnId, String matchStatus,
                             String mismatchType, java.math.BigDecimal amountDifference,
                             java.time.OffsetDateTime createdAt) {
    }

    @Transactional(readOnly = true)
    public List<ResultView> resultsFor(UUID runId) {
        return results.findByRunRunId(runId).stream()
                .map(r -> new ResultView(r.getResultId(), runId,
                        r.getPayment().getPaymentId(), r.getPayment().getExternalTxnId(),
                        r.getMatchStatus(), r.getMismatchType(),
                        r.getAmountDifference(), r.getCreatedAt()))
                .toList();
    }
}
