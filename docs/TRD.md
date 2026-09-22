# TRD — Technical Requirements (extracted from `Doc/FINRECON_MASTER.md` #6–#8)

## FR → owning service (monolith packages + ai-service)

| FR | Owner | Location |
|---|---|---|
| FR-01/02 ingestion, normalization | finrecon-app ingestion | `services/finrecon-app/.../ingestion/` |
| FR-03/04 matching, reconciliation | finrecon-app reconciliation | `services/finrecon-app/.../reconciliation/` |
| FR-05/07/08/11/12 exceptions, cases, evidence, feedback, audit | finrecon-app exceptioncase | `services/finrecon-app/.../exceptioncase/` |
| FR-13 reporting | finrecon-app reporting | `services/finrecon-app/.../reporting/` |
| FR-06 classifier | ai-service | `ai-service/classifier/`, `evaluation/p6_metrics.json` |
| FR-09 RAG | ai-service | `ai-service/rag/`, `evaluation/p7*.json` |
| FR-10 investigator | ai-service | `ai-service/agent/` |
| Dashboard | frontend | `frontend/app/`, `components/`, `lib/` |

## Non-functional targets

- Correctness: reconciliation is deterministic, unit-tested, versioned
  (`RULE_VERSION 1.0.0`), reproducible from stored records.
- Traceability: every exception traces to source records, rule version,
  model version, and retrieved evidence.
- Performance: reconciliation throughput and API P95 measured where a
  live stack exists (`scripts/measure_performance.py`); unmeasured
  means "not measured" with a reason (`docs/METRICS.md`).
- Reliability: idempotent ingest (unique `external_txn_id`, exact-
  duplicate settlement drop), resumable runs, audited transitions.
- Security: one-origin CORS, `X-Request-Id` correlation, read-only AI
  surface, prompt-injection pin, hygiene scan (`docs/SECURITY.md`).
- Explainability: AI output cites database facts and document excerpts;
  every investigation needs human approval.

## Approved stack

| Layer | Use now | Deferred |
|---|---|---|
| Backend | Java 21, Spring Boot 3.2.5, Gradle 8.9 (monolith) | Microservice split only if boundaries demand it |
| Database | PostgreSQL 16 (+ pgvector image) via Flyway | Read replicas / managed DB later |
| Messaging/cache | None by default (broker-less, 8GB-friendly) | Kafka/Redis only where brokers exist; code path removed in monolith consolidation, P5 rationale kept |
| ML | Python FastAPI + scikit-learn + XGBoost | LightGBM if benchmarked |
| RAG | pgvector + LangChain, signed-hash embeddings | Reranker / hybrid later |
| Agent | LangGraph-style grounded workflow | Multi-agent only if justified |
| Fine-tuning | Not required | LoRA/QLoRA only on measured justification (deferred, `docs/ML.md`) |
| Frontend | Next.js + TypeScript, no UI library | UI library only if needed |
| Packaging | Docker + Compose (postgres-only) | Kubernetes later |
| Observability | Structured logs + Micrometer counters | Prometheus/Grafana/OpenTelemetry later |
| CI/CD | GitHub Actions quality gates | Cloud deploy later |
| Cloud | None required | AWS mapping later, not a prerequisite |
