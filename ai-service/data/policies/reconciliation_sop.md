---
document_id: FIN-SOP-001
title: Reconciliation Operating Procedure
version: 1.0
effective_from: 2026-01-01
effective_to: null
synthetic: true
---
# Reconciliation Operating Procedure
<!-- page: 1 -->
## Matching controls
This is a synthetic training policy, not a merchant instruction. Match payment, ledger and settlement using the exact external transaction reference first. If no exact reference exists, controlled fallback requires the same merchant, amount and currency within a 24-hour time window. Exactly one candidate must qualify. Multiple candidates require manual review; never merge ambiguous payments using fuzzy similarity.

## Financial evidence
Retain the gateway payment, ledger entries and settlement records with their source identifiers. Record expected value, observed value, difference, tolerance and rule version. Use decimal arithmetic for money. Retrieved text and model predictions are advisory evidence; they must not overwrite financial facts or calculate whether money moved correctly.
<!-- page: 2 -->
## P3 rule order
The current engine reports the first failing condition: duplicate settlement, missing settlement, gross amount mismatch, ambiguous ledger entries, fee variance, partial or excess settlement, currency disagreement, status disagreement, then late settlement. A matched outcome means no listed condition failed, not proof of bank movement. Investigators must retain combined faults as evidence even when the engine emits one category.
