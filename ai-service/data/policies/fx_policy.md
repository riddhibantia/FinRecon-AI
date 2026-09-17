---
document_id: FIN-FX-001
title: Foreign Exchange Reference Policy
version: 1.0
effective_from: 2026-01-01
effective_to: null
synthetic: true
---
# Foreign Exchange Reference Policy
<!-- page: 1 -->
## FX reference evidence
For a foreign exchange discrepancy, obtain the currency pair, quoted exchange rate, rate timestamp and provider reference from treasury. Preserve original currency amounts and conversion direction. Never infer an exchange rate from a retrieved paragraph or use a current market quote for an older payment. This synthetic policy supplies no live FX rates.

## Currency disagreement
The current P3 FX_VARIANCE condition means that payment, ledger or settlement currencies disagree. P3 does not calculate rate deltas, cross-currency net amounts or FX tolerances. If a historical reference rate is unavailable, return insufficient evidence and request treasury review instead of inventing a conversion amount.
<!-- page: 2 -->
## FX rounding review
The agreed conversion basis belongs to the effective merchant agreement. Treasury must approve the reference source and rounding basis before an analyst proposes an FX adjustment. Retain the approval and calculated decimal values separately from any AI explanation.
