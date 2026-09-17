---
document_id: FIN-DATA-001
title: Reconciliation Data Dictionary
version: 1.0
effective_from: 2026-01-01
effective_to: null
synthetic: true
---
# Reconciliation Data Dictionary
<!-- page: 1 -->
## Money fields
In this synthetic data dictionary, gross_amount is the ledger amount before fees. fee_amount is the observed processing fee. net_amount is the ledger expected amount after fees. settled_amount is the amount reported by the settlement source. Currency accompanies every amount. Store and compare decimal values; binary floating point must not establish financial truth.

## Reference identifiers
external_txn_id identifies the gateway transaction used for exact matching. payment_id is the internal payment identifier. settlement_id identifies a source settlement row, while batch_id identifies its delivery batch. A shared batch identifier is not a unique payment reference. Cite the original record identifier rather than inventing a combined record.
<!-- page: 2 -->
## Evidence and timestamps
event_time is the gateway payment timestamp with timezone. posted_at is the ledger posting timestamp. settlement_date is a calendar date, not a timezone-aware transfer instant. expected_value and observed_value describe a particular rule comparison; amount_difference alone does not explain the complete exception. Store the rule version with reconciliation evidence.
