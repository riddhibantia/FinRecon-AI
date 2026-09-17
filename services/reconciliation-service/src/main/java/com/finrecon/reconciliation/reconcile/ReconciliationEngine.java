package com.finrecon.reconciliation.reconcile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.finrecon.reconciliation.domain.LedgerEntry;
import com.finrecon.reconciliation.domain.Payment;
import com.finrecon.reconciliation.domain.Settlement;

// P3 deterministic reconciliation engine. Pure functions over detached
// records: no repositories, no clock reads inside comparisons, no LLM, no
// ML. Same input always yields the same outcome.
//
// Check order is fixed (first failure wins):
//  1. DUPLICATE_SETTLEMENT  - more than one settlement for the payment
//  2. MISSING_SETTLEMENT    - no settlement at all
//  3. AMOUNT_MISMATCH       - no ledger entry, or ledger gross != gateway amount
//  4. UNKNOWN_EXCEPTION     - more than one ledger entry (ambiguous books)
//  5. FEE_VARIANCE          - ledger fee != settlement fee (no fee-rule
//                             store exists in P1, so P3 compares the two
//                             observed fees; the rule-based check is P7+)
//  6. PARTIAL_SETTLEMENT    - settled < net; AMOUNT_MISMATCH when settled > net
//  7. FX_VARIANCE           - gateway/ledger/settlement currencies differ
//  8. STATUS_MISMATCH       - the three statuses differ after trim+uppercase
//                             (P3 simplification; lifecycle mapping is later)
//  9. LATE_SETTLEMENT       - settlement_date beyond event date + window
// 10. MATCHED               - everything agrees
//
// P3-defined parameters (documented, versioned under RULE_VERSION):
//  - SETTLEMENT_WINDOW_DAYS = 2 (no SLA store exists in P1)
//  - FALLBACK_WINDOW_HOURS = 24 for constrained fallback matching
//    (merchant + amount + currency + time window, master #12)
public final class ReconciliationEngine {

    public static final String RULE_VERSION = "1.0.0";
    public static final int SETTLEMENT_WINDOW_DAYS = 2;
    public static final int FALLBACK_WINDOW_HOURS = 24;

    private ReconciliationEngine() {
    }

    public record Outcome(String matchStatus, String mismatchType, BigDecimal amountDifference) {

        public static Outcome matched() {
            return new Outcome("MATCHED", null, null);
        }

        public static Outcome mismatched(String type, BigDecimal difference) {
            return new Outcome("MISMATCHED", type, difference);
        }
    }

    public static Outcome reconcile(Payment payment, List<LedgerEntry> ledgers,
                                    List<Settlement> settlements) {
        if (settlements.size() > 1) {
            BigDecimal total = totalSettled(settlements);
            BigDecimal net = ledgers.isEmpty() ? null : ledgers.get(0).getNetAmount();
            return Outcome.mismatched("DUPLICATE_SETTLEMENT",
                    net == null ? total.negate() : net.subtract(total));
        }
        if (settlements.isEmpty()) {
            BigDecimal expected = ledgers.isEmpty()
                    ? payment.getAmount() : ledgers.get(0).getNetAmount();
            return Outcome.mismatched("MISSING_SETTLEMENT", expected);
        }
        Settlement settlement = settlements.get(0);
        if (ledgers.isEmpty()) {
            return Outcome.mismatched("AMOUNT_MISMATCH", payment.getAmount());
        }
        LedgerEntry ledger = ledgers.get(0);
        if (ledgers.size() > 1) {
            return Outcome.mismatched("UNKNOWN_EXCEPTION", null);
        }
        if (ledger.getGrossAmount().compareTo(payment.getAmount()) != 0) {
            return Outcome.mismatched("AMOUNT_MISMATCH",
                    ledger.getGrossAmount().subtract(payment.getAmount()));
        }
        if (ledger.getFeeAmount().compareTo(settlement.getFeeAmount()) != 0) {
            return Outcome.mismatched("FEE_VARIANCE",
                    settlement.getFeeAmount().subtract(ledger.getFeeAmount()));
        }
        if (settlement.getSettledAmount().compareTo(ledger.getNetAmount()) < 0) {
            return Outcome.mismatched("PARTIAL_SETTLEMENT",
                    ledger.getNetAmount().subtract(settlement.getSettledAmount()));
        }
        if (settlement.getSettledAmount().compareTo(ledger.getNetAmount()) != 0) {
            return Outcome.mismatched("AMOUNT_MISMATCH",
                    ledger.getNetAmount().subtract(settlement.getSettledAmount()));
        }
        if (!sameText(payment.getCurrency(), ledger.getCurrency())
                || !sameText(payment.getCurrency(), settlement.getCurrency())) {
            return Outcome.mismatched("FX_VARIANCE",
                    ledger.getNetAmount().subtract(settlement.getSettledAmount()));
        }
        if (!sameText(payment.getStatus(), ledger.getPostingStatus())
                || !sameText(payment.getStatus(), settlement.getSettlementStatus())) {
            return Outcome.mismatched("STATUS_MISMATCH",
                    ledger.getNetAmount().subtract(settlement.getSettledAmount()));
        }
        LocalDate deadline = payment.getEventTime().toLocalDate().plusDays(SETTLEMENT_WINDOW_DAYS);
        if (settlement.getSettlementDate().isAfter(deadline)) {
            return Outcome.mismatched("LATE_SETTLEMENT",
                    ledger.getNetAmount().subtract(settlement.getSettledAmount()));
        }
        return Outcome.matched();
    }

    // Constrained fallback matching (master #12): exact reference first,
    // else merchant + amount + currency + time window; exactly one
    // candidate must qualify, otherwise empty (never merge ambiguously).
    public static Optional<Payment> matchPaymentFor(String externalTxnId, String merchantId,
                                                    BigDecimal amount, String currency,
                                                    java.time.OffsetDateTime eventTime,
                                                    List<Payment> candidates) {
        if (externalTxnId != null && !externalTxnId.isBlank()) {
            String ref = externalTxnId.trim();
            for (Payment candidate : candidates) {
                if (ref.equals(candidate.getExternalTxnId())) {
                    return Optional.of(candidate);
                }
            }
        }
        if (merchantId == null || amount == null || currency == null || eventTime == null) {
            return Optional.empty();
        }
        Payment hit = null;
        for (Payment candidate : candidates) {
            if (!merchantId.trim().equals(candidate.getMerchantId())) {
                continue;
            }
            if (amount.compareTo(candidate.getAmount()) != 0) {
                continue;
            }
            if (!sameText(currency, candidate.getCurrency())) {
                continue;
            }
            long hours = Math.abs(Duration.between(eventTime, candidate.getEventTime()).toHours());
            if (hours > FALLBACK_WINDOW_HOURS) {
                continue;
            }
            if (hit != null) {
                return Optional.empty(); // ambiguous: refuse to merge
            }
            hit = candidate;
        }
        return Optional.ofNullable(hit);
    }

    private static BigDecimal totalSettled(List<Settlement> settlements) {
        BigDecimal total = BigDecimal.ZERO;
        for (Settlement settlement : settlements) {
            total = total.add(settlement.getSettledAmount());
        }
        return total;
    }

    private static boolean sameText(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }
}
