---
document_id: FIN-EXC-001
title: Exception Investigation Procedures
version: 1.0
effective_from: 2026-01-01
effective_to: null
synthetic: true
---
# Exception Investigation Procedures
<!-- page: 1 -->
## Duplicate settlement investigation
For duplicate settlement records, compare the source settlement identifiers, batch identifiers and transaction reference. Preserve every row and check whether a file was replayed before concluding that money moved twice. Do not delete duplicate evidence or issue a refund automatically. Ask payment operations to confirm whether there were two transfers or only duplicate records. This is a synthetic training procedure.

## Partial settlement investigation
For a partial settlement, compare total settled amount with the ledger net amount. Request the outstanding tranche reference and expected completion date. Do not mark the case resolved merely because one settlement row exists. Record the remaining amount using deterministic source calculations, not a language model.
<!-- page: 2 -->
## Status mismatch investigation
For status disagreement, compare the original gateway, ledger and settlement status fields. The P3 engine trims and uppercases text; it has no richer lifecycle mapping. Preserve source status timestamps and ask operations whether a delayed lifecycle event explains the mismatch. Do not silently translate SUCCESS to SETTLED in the evidence.
