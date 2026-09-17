---
document_id: FIN-SET-001
title: Settlement Timing Policy
version: 1.0
effective_from: 2026-01-01
effective_to: null
synthetic: true
---
# Settlement Timing Policy
<!-- page: 1 -->
## Settlement window
This synthetic policy uses a two-calendar-day settlement window from the payment event date. A settlement dated after event date plus two days is late. The current P3 engine uses calendar days, not banking days, and does not adjust for weekends or public holidays. Analysts must not claim a business-day calendar is implemented.

## Missing settlement
For a missing settlement, confirm the payment reference and check the settlement batch delivery record before contacting payment operations. The P3 engine flags an absent settlement immediately; it does not wait for the two-day window to expire. Before the deadline, describe the finding as pending delivery rather than a proven SLA breach. Preserve the gateway and ledger records and the last observed batch timestamp.
<!-- page: 2 -->
## Batch delivery evidence
A settlement batch acknowledgement confirms file receipt, not final funds availability. Compare the expected batch identifier with the received file manifest and source settlement rows. If the batch is incomplete, request the missing file from payment operations. Never manufacture a settlement row to clear an exception.
