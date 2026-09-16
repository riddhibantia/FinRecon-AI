**Intelligent Payment Reconciliation & Exception Resolution Platform**

MASTER BUILD DOCUMENT — Product Requirements, Technical Design,
Architecture, Data Model, Build Phases, Agent Responsibilities, Testing,
Evaluation, and Definition of Done

# 0. How to Use This Document

This is the single source of truth for building FinRecon AI. An AI
coding agent should read this document before implementing a phase. The
build is intentionally staged: first make the financial facts correct
and reproducible, then add ML, then RAG, then the investigation agent,
then optional fine-tuning, and finally production hardening.

- OpenCore and OMP are development agents, not runtime technologies.
  They help write, test, review, and integrate the project; they are not
  part of the deployed architecture.

- Only one agent should own a file set at a time. Do not let both agents
  edit the same files concurrently unless the integration step is
  explicitly assigned.

- No invented business metrics. All performance numbers in the final
  resume must be measured from the implemented system.

- Use synthetic or public-safe data only. Never use real card numbers,
  authentication secrets, or confidential financial records.

# 1. Executive Summary

FinRecon AI is a simulated financial-operations platform that reconciles
payment gateway, internal ledger, and settlement records; detects
mismatches; classifies exceptions; and assists analysts in investigating
and resolving them. The platform separates deterministic financial facts
from probabilistic AI: the reconciliation engine establishes what
differs, the ML layer classifies the exception, the RAG layer retrieves
operational policy and evidence, and the AI investigator generates a
cited investigation summary for human review.

Core business question: “Do the financial systems agree about what
happened to this payment, and if not, what explains the discrepancy?”
The project is not a fraud detector.

# 2. Product Requirements

## 2.1 Problem

Payment information exists in multiple systems with different schemas,
identifiers, timestamps, fees, currencies, and settlement states.
Operations teams need to determine which records match, which do not,
why they differ, and what action should be taken.

## 2.2 Target Users

- Reconciliation Analyst

- Payment Operations Analyst

- Risk/Operations Manager

- System Administrator

- ML/AI Engineer

## 2.3 V1 Scope

- Three sources: Payment Gateway, Internal Ledger, Settlement System

- Five to nine exception categories, with a recommended initial five for
  the first working release

- Case lifecycle: OPEN -\> INVESTIGATING -\> RESOLVED / ESCALATED

- Analyst queue, case detail, evidence view, AI investigation panel, and
  resolution controls

# 3. Business Data and Example

| **Source**        | **Important Fields**                                                                                     |
|-------------------|----------------------------------------------------------------------------------------------------------|
| Payment Gateway   | transaction ID, merchant, amount, currency, status, timestamp, gateway reference                         |
| Internal Ledger   | ledger entry ID, transaction reference, gross amount, fee, net amount, posting status, posting timestamp |
| Settlement System | settlement ID, transaction reference, settled amount, fee, currency, status, settlement date, batch ID   |

Example: Gateway = ₹10,000 SUCCESS; Ledger = ₹10,000 SUCCESS; Settlement
= ₹9,750 SETTLED. The engine detects a ₹250 variance, checks the stored
fee rule, classifies the case as FEE_VARIANCE, retrieves the relevant
policy, and presents evidence plus a suggested next action.

# 4. Exception Taxonomy

| **Category**         | **Meaning**                                                    | **Primary Detection**                     |
|----------------------|----------------------------------------------------------------|-------------------------------------------|
| MISSING_SETTLEMENT   | Gateway/ledger payment exists but no valid settlement is found | No settlement match after matching window |
| AMOUNT_MISMATCH      | Gross/settled amount differs beyond tolerance                  | Amount comparison                         |
| FEE_VARIANCE         | Observed fee differs from expected fee rule                    | Fee calculation vs policy                 |
| FX_VARIANCE          | Currency conversion or FX amount differs from expected basis   | FX reference + tolerance                  |
| DUPLICATE_SETTLEMENT | Multiple settlements appear for the same payment               | Duplicate match detection                 |
| PARTIAL_SETTLEMENT   | Only part of the expected amount settled                       | Settled amount \< expected net under rule |
| STATUS_MISMATCH      | Source systems disagree on lifecycle status                    | Status comparison                         |
| LATE_SETTLEMENT      | Settlement occurs outside configured SLA/window                | Timestamp + SLA rule                      |
| UNKNOWN_EXCEPTION    | Mismatch exists but cannot be safely mapped to a known class   | Fallback classification                   |

# 5. User Stories

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

- As an administrator, I want important data changes, AI actions, and
  resolutions auditable.

# 6. Functional Requirements

| **ID** | **Area**           | **Requirement**                                                                                      |
|--------|--------------------|------------------------------------------------------------------------------------------------------|
| FR-01  | Ingestion          | Load gateway, ledger, settlement records from CSV and REST first; object storage can be added later. |
| FR-02  | Normalization      | Map source-specific fields into a canonical transaction representation.                              |
| FR-03  | Matching           | Match by transaction reference and fall back to controlled secondary keys.                           |
| FR-04  | Reconciliation     | Compare amount, fee, net, currency, status, dates, and duplicate conditions.                         |
| FR-05  | Exception creation | Create versioned exception records with evidence whenever a reconciliation rule fails.               |
| FR-06  | Classification     | Assign category using deterministic baseline plus trained classifier.                                |
| FR-07  | Case management    | Create, assign, investigate, resolve, and escalate cases.                                            |
| FR-08  | Evidence           | Persist source records and exact comparison values used for the case.                                |
| FR-09  | RAG                | Retrieve policies, fee schedules, SOPs, agreements, and escalation rules with citations.             |
| FR-10  | AI investigator    | Use tools to retrieve facts and policies and produce a structured cited investigation.               |
| FR-11  | Human feedback     | Allow analyst confirm/correct actions and classifications.                                           |
| FR-12  | Audit              | Record ingestion, reconciliation, AI, analyst, and resolution events.                                |
| FR-13  | Reporting          | Show operational KPIs and ageing/impact summaries.                                                   |

# 7. Non-Functional Requirements

- Correctness: reconciliation rules are deterministic, unit tested,
  versioned, and reproducible.

- Traceability: every exception can be traced to source records, rule
  version, model version, and retrieved evidence.

- Performance: benchmark reconciliation throughput and API P95 latency.

- Reliability: idempotency, retries, resumable jobs, and dead-letter
  handling prevent duplicate financial effects.

- Security: RBAC, secrets management, audit logs, data minimization, and
  prompt-injection defenses.

- Scalability: ingestion, reconciliation, AI inference, and dashboard
  workloads are independently scalable.

- Explainability: AI output must point to database facts and retrieved
  document evidence.

# 8. Technology Stack — FINAL REFINED VERSION

The original stack intentionally listed many technologies so the project
could cover a realistic enterprise architecture. For implementation, do
not activate everything on day one. The table below is the approved
stack order.

| **Layer**           | **Use Now**                                     | **Add Later / Optional**                                | **Why**                                                         |
|---------------------|-------------------------------------------------|---------------------------------------------------------|-----------------------------------------------------------------|
| Backend             | Java 21 + Spring Boot + Spring Data JPA/JDBC    | Separate microservices only when boundaries are stable  | Strong transactional core and enterprise backend story          |
| Database            | PostgreSQL                                      | Read replicas/cloud managed DB later                    | System of record for financial facts                            |
| Messaging           | Kafka after synchronous core works              | Managed Kafka in cloud                                  | Event-driven ingestion and async processing                     |
| Cache / reliability | Redis after core APIs work                      | ElastiCache later                                       | Idempotency, cache, rate limits, short-lived state              |
| ML                  | Python 3.11+ + FastAPI + scikit-learn + XGBoost | LightGBM if benchmarked                                 | Exception classification and ML inference                       |
| RAG                 | pgvector + LangChain                            | Reranker/hybrid retrieval later                         | Keeps vector data close to the PostgreSQL system of record      |
| Agent               | LangGraph                                       | More complex multi-agent patterns only if justified     | Explicit, testable investigation workflow                       |
| LLM fine-tuning     | Not required for MVP                            | PyTorch + Transformers + PEFT/LoRA/QLoRA                | Only after enough labelled data exists and baseline is measured |
| Frontend            | React / Next.js                                 | UI library only if needed                               | Analyst operations dashboard                                    |
| Local packaging     | Docker + Docker Compose                         | Kubernetes later                                        | Reproducible local environment                                  |
| Observability       | Structured logs + basic metrics first           | Prometheus, Grafana, OpenTelemetry                      | Operational visibility after core is stable                     |
| CI/CD               | Git + GitHub Actions after tests exist          | Cloud deployment later                                  | Automated quality gates                                         |
| Cloud               | None required for first local MVP               | AWS RDS, MSK, ElastiCache, S3, ECR, ECS/EKS, CloudWatch | Deployment story after local system works                       |

Important: do not force Kubernetes, AWS, fine-tuning, OpenTelemetry,
reranking, or separate microservices into the first implementation. They
are evidence of progression only after the core workflow works.

# 9. Architecture

> Sources -\> Ingestion -\> Normalization -\> Matching -\> Deterministic
> Reconciliation -\> Exception/Case
>
> \|
>
> +-\> ML Exception Classifier
>
> +-\> RAG Knowledge Base
>
> +-\> LangGraph Investigation Agent
>
> +-\> Human Analyst Dashboard

Runtime separation: Spring Boot owns transactional financial-domain
operations; Python/FastAPI owns ML, retrieval, and AI orchestration;
PostgreSQL remains the system of record; Kafka is introduced only after
synchronous correctness is proven.

# 10. Service Responsibilities

| **Component**     | **Responsibility**                                            | **Owner**             |
|-------------------|---------------------------------------------------------------|-----------------------|
| Gateway/API       | Auth, routing, correlation IDs, API contracts                 | OpenCore              |
| Ingestion         | Read source records, validate, publish canonical data         | OpenCore              |
| Normalization     | Convert heterogeneous source schemas to canonical model       | OpenCore              |
| Reconciliation    | Matching, comparisons, variance calculations, rule versioning | OpenCore              |
| Exception/Case    | Case lifecycle, evidence, assignment, resolution, audit       | OpenCore              |
| Kafka integration | Events, retries, DLQ, consumer idempotency                    | OpenCore              |
| ML classifier     | Features, training, inference, evaluation                     | OMP                   |
| Knowledge/RAG     | Chunking, embeddings, retrieval, citations                    | OMP                   |
| AI Investigator   | LangGraph workflow, tool calls, structured explanation        | OMP                   |
| Frontend          | Case queue, evidence view, AI panel, analytics                | OpenCore              |
| Integration QA    | Cross-service wiring, contract tests, end-to-end tests        | OpenCore + OMP review |

# 11. Canonical Data Model

| **Entity**             | **Core Fields**                                                                                               |
|------------------------|---------------------------------------------------------------------------------------------------------------|
| payments               | payment_id, external_txn_id, customer_id, merchant_id, amount, currency, status, event_time                   |
| ledger_entries         | ledger_entry_id, payment_id, gross_amount, fee_amount, net_amount, currency, posting_status, posted_at        |
| settlements            | settlement_id, payment_id, settled_amount, fee_amount, currency, settlement_status, settlement_date, batch_id |
| reconciliation_runs    | run_id, source_set, started_at, completed_at, status, rule_version                                            |
| reconciliation_results | result_id, run_id, payment_id, match_status, mismatch_type, amount_difference, created_at                     |
| exceptions             | exception_id, result_id, category, severity, status, assigned_to, created_at, resolved_at                     |
| exception_evidence     | evidence_id, exception_id, source_type, source_record_id, field_name, expected_value, observed_value          |
| resolution_actions     | action_id, exception_id, action_type, actor_type, actor_id, notes, created_at                                 |
| policies               | policy_id, title, version, effective_from, effective_to, source_uri                                           |
| policy_chunks          | chunk_id, policy_id, chunk_text, metadata, embedding                                                          |
| ai_investigations      | investigation_id, exception_id, model_version, prompt_version, status, summary, confidence                    |
| ai_tool_calls          | tool_call_id, investigation_id, tool_name, arguments, result_hash, latency_ms                                 |
| ai_citations           | citation_id, investigation_id, source_type, source_id, location, quote_or_excerpt                             |
| analyst_feedback       | feedback_id, exception_id, analyst_id, original_value, corrected_value, reason, created_at                    |
| audit_logs             | audit_id, actor_type, actor_id, action, entity_type, entity_id, timestamp, metadata                           |

# 12. Reconciliation Engine Rules

- First attempt exact reference matching using stable transaction
  identifiers.

- Fallback matching must use constrained combinations such as merchant +
  amount + currency + time window; never perform broad fuzzy matching
  that can silently merge unrelated payments.

- Compare gross, fee, net, settled amount, currency, status, settlement
  date, and duplicate conditions.

- Calculate explicit variance objects: expected value, observed value,
  difference, tolerance, rule applied, and rule version.

- Every reconciliation result must be reproducible from stored source
  records and rule version.

- Financial arithmetic must remain deterministic; LLMs must never decide
  numeric truth.

# 13. Baseline ML Classifier

Purpose: classify an already detected exception into an operational
category. The classifier does not replace deterministic reconciliation
and does not decide whether money moved correctly.

| **Feature Group** | **Examples**                                                |
|-------------------|-------------------------------------------------------------|
| Amount            | gross, fee, net, settled amount, absolute/relative variance |
| Timing            | hours to settlement, lateness, posting lag                  |
| Status            | gateway/ledger/settlement status combinations               |
| Currency/FX       | currency equality, FX delta, tolerance breach               |
| Duplicate signals | settlement count, repeated settlement IDs                   |
| Matching          | match confidence/type, fallback-key usage                   |
| Merchant/context  | merchant segment, configured settlement window              |

Start with Logistic Regression as a transparent baseline; benchmark
XGBoost next. Report macro F1, per-class precision/recall, confusion
matrix, calibration where practical, and leakage checks.

# 14. RAG Knowledge Base

The knowledge base should contain simulated but realistic operational
documents: reconciliation SOPs, settlement policies, fee schedules, FX
policy, merchant agreements, exception procedures, escalation matrix,
and data dictionary.

Pipeline: documents -\> parse -\> chunk -\> metadata -\> embeddings -\>
PostgreSQL/pgvector -\> retrieval -\> optional reranking -\> cited
context.

- Every chunk must retain document ID, title, version, effective dates,
  and page/section metadata.

- Retrieved policy must be shown as evidence, not silently paraphrased
  without source linkage.

- Evaluation should test whether the correct document and relevant
  passage are retrieved for a fixed question set.

# 15. AI Investigator — LangGraph Workflow

> classify_exception
>
> -\> load_evidence -\> query_records -\> find_similar_exceptions -\>
> retrieve_policy
>
> -\> analyze_root_cause -\> draft_resolution -\>
> verify_evidence_and_citations
>
> -\> generate_case_summary -\> human_review

The agent must use tools for financial facts. It must not invent
transaction values, settlement status, fee rules, or policy
requirements. If evidence is insufficient or conflicting, the workflow
should return a controlled “insufficient evidence / manual review”
outcome.

# 16. Agent Tools

- get_payment

- get_ledger_entries

- get_settlement_records

- calculate_variance

- get_fee_rule

- get_fx_reference

- find_similar_exceptions

- search_policy

- get_case_history

- create_resolution_draft

- record_analyst_feedback

# 17. AI Output Contract

> {
>
> "exception_id": "...",
>
> "root_cause": "FEE_VARIANCE",
>
> "summary": "...",
>
> "recommended_action": "...",
>
> "confidence": 0.0,
>
> "evidence": \[
>
> {"source_type":"ledger","source_id":"...","field":"fee_amount"},
>
> {"source_type":"policy","document":"...","section":"..."}
>
> \]
>
> }

# 18. Security and Controls

- Roles: ANALYST, MANAGER, ADMIN, SERVICE.

- No raw card numbers or authentication secrets in the synthetic
  dataset.

- Use environment variables or managed secret storage.

- Audit case assignment, resolution, policy changes, model/prompt
  version changes, and AI tool calls.

- Mask PII in logs.

- Treat retrieved documents and user-entered text as untrusted content;
  defend against prompt injection and tool misuse.

# 19. Reliability and Idempotency

- Idempotency keys for important ingestion/write APIs.

- Kafka event IDs and consumer-side deduplication when Kafka is enabled.

- Retries with exponential backoff for transient dependencies.

- Dead-letter queue for malformed or persistently failing events.

- Resumable reconciliation runs.

- Persist rule/model/prompt versions with results.

# 20. Testing Strategy

| **Test Level** | **Required Coverage**                                                                                     |
|----------------|-----------------------------------------------------------------------------------------------------------|
| Unit           | Matching, rules, variance calculations, state transitions, classifier preprocessing, prompt/tool builders |
| Database       | Constraints, transactions, indexes, critical SQL queries                                                  |
| Integration    | REST contracts, Kafka events/retries/DLQ, idempotency                                                     |
| RAG            | Fixed questions with expected supporting documents/passages                                               |
| Agent          | Tool selection, argument validation, citation verification, insufficient-evidence behavior                |
| ML             | Macro F1, class metrics, leakage checks, regression set                                                   |
| End-to-end     | Ingest 3 sources -\> reconcile -\> exception -\> classify -\> investigate -\> resolve                     |

# 21. Observability

- reconciliation_records_processed

- reconciliation_match_rate

- exceptions_created

- exceptions_resolved

- exception_ageing

- classifier_latency

- classifier_confidence

- retrieval_latency

- LLM_latency

- LLM_tokens

- AI_citation_coverage

- Kafka_consumer_lag

- API_error_rate

# 22. Repository Structure

> finrecon-ai/
>
> services/ \# Spring Boot core
>
> gateway-service/
>
> ingestion-service/
>
> reconciliation-service/
>
> exception-service/
>
> reporting-service/
>
> ai-service/ \# Python/FastAPI
>
> classifier/ rag/ agent/ tools/ evaluation/ tests/
>
> frontend/ \# React/Next.js
>
> db/migrations/ db/seed/ data/synthetic/ data/evaluation/
>
> docs/PRD.md TRD.md ARCHITECTURE.md DATABASE.md EVENTS.md ML.md RAG.md
> AGENT.md SECURITY.md TESTING.md DECISIONS.md
>
> docker-compose.yml README.md .github/workflows/ci.yml

# 23. Detailed Build Plan — What Must Actually Be Done

| **Phase** | **Primary Agent** | **Workstream**                      | **What to do**                                                                                                                                                                                 | **Exit Criteria**                                                                                         |
|-----------|-------------------|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| P0        | OpenCore          | Project foundation                  | Create repository, module boundaries, local Docker/PostgreSQL, Java/Python/frontend skeletons, environment templates, README, branching rules, coding standards, health endpoints, initial CI. | Repo builds locally; services start; README explains how to run; no business logic yet.                   |
| P1        | OpenCore          | Domain model + database             | Freeze canonical schemas; create ERD; migrations; tables; constraints; indexes; seed data; source-record fixtures; audit primitives.                                                           | Three source records can be stored and queried; migration is reproducible.                                |
| P2        | OpenCore          | Ingestion + normalization           | Implement CSV and REST ingestion; validation; source-to-canonical mapping; idempotency; ingestion status; error reporting.                                                                     | Repeated ingestion does not duplicate records; canonical objects are produced.                            |
| P3        | OpenCore          | Deterministic reconciliation        | Implement exact/fallback matching, amount/fee/net/currency/status/time checks, variance object, rule versioning, mismatch taxonomy.                                                            | Given known fixtures, expected match/mismatch results are deterministic and fully tested.                 |
| P4        | OpenCore          | Exception + case management         | Create exception/evidence/case APIs; assignment; status transitions; resolution actions; audit history; evidence endpoints.                                                                    | Analyst can open an exception and trace it to source records.                                             |
| P5        | OpenCore          | Kafka + Redis integration           | Only after P1-P4 work. Add canonical topics, consumers, retry/DLQ, correlation IDs, Redis idempotency and caching where justified.                                                             | Async path reproduces synchronous results without duplicate effects.                                      |
| P6        | OMP               | ML dataset + baseline classifier    | Generate labelled exception cases from deterministic scenarios; create feature pipeline; train Logistic Regression; benchmark XGBoost; save model metadata.                                    | Classifier predicts categories with documented macro F1 and per-class metrics.                            |
| P7        | OMP               | RAG knowledge base                  | Create synthetic operational docs; build ingestion/chunking/metadata; embed into pgvector; implement retrieval; add evaluation set and citations.                                              | Fixed queries retrieve relevant evidence with traceable document metadata.                                |
| P8        | OMP               | LangGraph investigator              | Build tools against OpenCore APIs/DB views; create graph workflow; structured output; citation verification; insufficient-evidence path; agent tests.                                          | Agent investigation is reproducible and never fabricates financial facts in test cases.                   |
| P9        | OMP               | Optional fine-tuning                | Only if enough labelled examples exist. Fine-tune a small classifier/structured model with LoRA/QLoRA; compare against baseline and keep only if measured improvement is real.                 | Baseline-vs-fine-tuned report exists; decision is evidence-based.                                         |
| P10       | OpenCore          | Dashboard + product integration     | Build React/Next.js queue, filters, case detail, source comparison, evidence, AI panel, resolution controls, management KPIs.                                                                  | One analyst can perform the full workflow end-to-end from browser.                                        |
| P11       | OpenCore          | Security + CI/CD + observability    | RBAC, secret handling, masking, audit checks, structured logs, metrics, CI tests, Docker image builds, dependency/security checks.                                                             | Pull request quality gates and reproducible local deployment exist.                                       |
| P12       | OpenCore          | Cloud-ready packaging               | Document AWS mapping and deploy only selected services if needed; include environment separation, rollback and monitoring.                                                                     | Deployment documentation and smoke test exist; cloud is not a prerequisite for project completion.        |
| P13       | OMP + OpenCore    | Final evaluation and demo hardening | OMP evaluates ML/RAG/agent; OpenCore validates end-to-end APIs, performance, reliability and UI. Capture measured metrics and 5+ representative demos.                                         | Final benchmark report, reproducible demo script, resume metrics and architecture decisions are complete. |

# 24. Agent Collaboration Rules

- Order matters: OpenCore starts. OMP should not start by redesigning
  the core domain model.

- OpenCore owns Java/Spring/PostgreSQL/Kafka/Redis/frontend/integration
  infrastructure unless a later handoff explicitly changes ownership.

- OMP owns Python/FastAPI/ML/RAG/LangGraph/evaluation/model artifacts.

- Cross-agent integration uses documented API contracts and database
  views/schemas rather than direct uncontrolled edits.

- Every handoff must include: what changed, files/modules touched, API
  contracts, how to run, tests passed, known gaps, and exact next task.

- Never ask an agent to “build the whole project” in one prompt. Give
  one phase with explicit acceptance criteria.

- After each phase, run tests and commit before switching agents.

# 25. Definition of Done

- Gateway, ledger, settlement data ingest successfully.

- Records normalize into a canonical model.

- Reconciliation produces deterministic matches/mismatches.

- Exceptions include evidence and versioned rules.

- ML classification has measured performance.

- RAG retrieves relevant policy evidence with citations.

- AI investigator uses tools instead of guessing.

- Analyst can confirm/correct outcomes.

- Audit trail captures important operations.

- Unit/integration/e2e/ML/RAG/agent tests exist.

- Docker Compose starts the platform locally.

- At least five representative exception scenarios are reproducible in a
  demo.

# 26. MVP vs Advanced Scope

| **MVP / Required**           | **Advanced / Only After MVP** |
|------------------------------|-------------------------------|
| 3-source ingestion           | Kafka event-driven processing |
| Canonical model              | Redis cache/idempotency       |
| Deterministic reconciliation | Hybrid retrieval + reranking  |
| 5–9 exception categories     | LangGraph investigation agent |
| PostgreSQL + REST            | Similar-case retrieval        |
| Basic case queue             | Human feedback loop           |
| Baseline ML classifier       | Fine-tuned classifier         |
| Basic RAG                    | OpenTelemetry                 |
| Docker Compose               | Kubernetes                    |
| Measured tests and metrics   | AWS deployment                |

# 27. Final Metrics — Measure, Do Not Invent

- Reconciliation throughput (records/minute or TPS).

- Automatic match/reconciliation rate.

- Exception classifier macro F1 and per-class recall.

- RAG Recall@K/MRR and citation correctness.

- AI investigator success rate on a fixed evaluation set.

- P95 API/reconciliation latency.

- Kafka throughput/consumer lag.

- Inference latency and token/cost per AI investigation.

- Test count/coverage.

- Controlled simulated resolution-time change, only if actually
  measured.

# 28. Architecture Decision Records to Maintain

- ADR-001 PostgreSQL as source of truth

- ADR-002 Kafka introduction point

- ADR-003 Deterministic reconciliation before ML/LLM

- ADR-004 PostgreSQL + pgvector for RAG

- ADR-005 LangGraph workflow choice

- ADR-006 Fine-tune classifier vs main LLM

- ADR-007 Facts that must come from tools/database

- ADR-008 Rule/model/prompt versioning

# 29. Recommended Start — Exact First Build

Start with OpenCore, not OMP.

> OpenCore: P0 -\> P1 -\> P2 -\> P3 -\> P4

The first successful demo should be deliberately small: one gateway
record + one ledger record + one settlement record -\> canonical
normalization -\> deterministic reconciliation -\> exception + evidence.
Only after that path is correct should OMP be introduced for
ML/RAG/agent work.

This sequencing prevents the AI layer from hiding defects in the
financial domain layer and makes the system easier to explain, test, and
defend in interviews.

# 30. Agent Prompting Standard

Every task sent to either agent should contain: project context, exact
phase, allowed technologies, files/modules allowed to change, required
tests, acceptance criteria, and a required handoff summary. The short
handoff document provides ready-to-use phase prompts.
