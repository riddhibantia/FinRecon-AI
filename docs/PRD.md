# PRD — Product Requirements (extracted from `Doc/FINRECON_MASTER.md` #2–#6)

## Problem

Payment information lives in three systems with different schemas,
identifiers, timestamps, fees, currencies, and settlement states.
Operations teams need to know which records agree, which do not, why
they differ, and what to do next.

Core business question: "Do the financial systems agree about what
happened to this payment, and if not, what explains the discrepancy?"
This project is not a fraud detector.

## Target users

- Reconciliation Analyst
- Payment Operations Analyst
- Risk/Operations Manager
- System Administrator
- ML/AI Engineer

## V1 scope

- Three sources: Payment Gateway, Internal Ledger, Settlement System.
- Nine exception categories (taxonomy below).
- Case lifecycle: OPEN -> INVESTIGATING -> RESOLVED / ESCALATED.
- Analyst queue, case detail, evidence view, AI investigation panel,
  resolution controls, management metrics.

## Business data

| Source | Important fields |
|---|---|
| Payment Gateway | transaction ID, merchant, amount, currency, status, timestamp, gateway reference |
| Internal Ledger | ledger entry ID, transaction reference, gross amount, fee, net amount, posting status, posting timestamp |
| Settlement System | settlement ID, transaction reference, settled amount, fee, currency, status, settlement date, batch ID |

Example: Gateway = INR 10,000 SUCCESS; Ledger = INR 10,000 SUCCESS;
Settlement = INR 9,750 SETTLED. The engine detects the INR 250
variance, checks the fee rule, classifies FEE_VARIANCE, retrieves the
relevant policy, and presents evidence plus a suggested next action.

## Exception taxonomy (9 categories)

| Category | Meaning |
|---|---|
| MISSING_SETTLEMENT | Payment exists but no valid settlement found in window |
| AMOUNT_MISMATCH | Gross/settled amount differs beyond tolerance |
| FEE_VARIANCE | Observed fee differs from expected fee rule |
| FX_VARIANCE | Currency conversion differs from expected basis |
| DUPLICATE_SETTLEMENT | Multiple settlements for the same payment |
| PARTIAL_SETTLEMENT | Only part of the expected amount settled |
| STATUS_MISMATCH | Sources disagree on lifecycle status |
| LATE_SETTLEMENT | Settlement outside the 2-calendar-day window |
| UNKNOWN_EXCEPTION | Mismatch that maps to no known class safely |

## User stories

- As an analyst, I want a queue of matched and mismatched transactions
  so I can focus on exceptions.
- As an analyst, I want exact source records and comparison values
  attached to each exception.
- As an analyst, I want policy/SOP evidence with citations before
  accepting an AI recommendation.
- As a manager, I want exception volume, category, ageing, amount
  impact, and resolution metrics.
- As an ML engineer, I want analyst corrections stored as
  labels/feedback for future model improvement.
- As an administrator, I want data changes, AI actions, and
  resolutions auditable.

## Functional requirements

| ID | Area | Requirement | Delivered |
|---|---|---|---|
| FR-01 | Ingestion | Load gateway, ledger, settlement records from CSV and REST | P2 |
| FR-02 | Normalization | Map source fields into the canonical model | P2 |
| FR-03 | Matching | Match by reference, constrained fallback keys | P3 |
| FR-04 | Reconciliation | Compare amount, fee, net, currency, status, dates, duplicates | P3 |
| FR-05 | Exception creation | Versioned exceptions with evidence on rule failure | P4 |
| FR-06 | Classification | Deterministic baseline plus trained classifier | P6 |
| FR-07 | Case management | Create, assign, investigate, resolve, escalate | P4 |
| FR-08 | Evidence | Persist source records and comparison values | P4 |
| FR-09 | RAG | Retrieve policies/SOPs with citations | P7 |
| FR-10 | AI investigator | Tool-grounded, cited investigation drafts | P8 |
| FR-11 | Human feedback | Analyst confirm/correct API + dashboard form | P13 (Tasks 1–2) |
| FR-12 | Audit | Ingestion, reconciliation, AI, analyst, resolution events | P4/P11 |
| FR-13 | Reporting | Operational KPIs, ageing, impact summaries | P13 (Tasks 3–5) |

## Deferred

- P9 fine-tuning: deferred on measured evidence (`docs/ML.md`).
- Identity/RBAC enforcement: deployment concern (`docs/SECURITY.md`).
- Kubernetes/AWS: not prerequisites (`docs/DECISIONS.md` P12).
