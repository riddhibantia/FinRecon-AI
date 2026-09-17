---
document_id: FIN-FEE-001
title: Merchant Fee Schedule
version: 1.0
effective_from: 2026-01-01
effective_to: null
synthetic: true
---
# Merchant Fee Schedule
<!-- page: 1 -->
## Standard domestic processing fee
For the synthetic DEMO-STANDARD merchant tier, the domestic processing fee is 2.50 percent of gross amount, with no fixed charge. Round the calculated fee to two decimal places using half-up rounding. A gross amount of INR 10000.00 therefore has a fee of INR 250.00 and a net amount of INR 9750.00. These demonstration rates are not an implemented fee-rule store or a real commercial offer.

## Fee variance investigation
Investigate a fee variance by comparing the ledger fee with the settlement fee and retaining both observed values. Check the merchant agreement version and effective date before using a schedule. The current P3 engine compares observed fees only; it does not compute the contracted fee from this document. A retrieved schedule must not be presented as a database-backed fee rule.
<!-- page: 2 -->
## Refund processing fees
For the synthetic standard tier, the original processing fee is not automatically returned on a refund. A refund fee credit requires an explicit approved adjustment linked to the original payment. Do not net an unsupported fee credit against a settlement discrepancy.
