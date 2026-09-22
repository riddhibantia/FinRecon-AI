# ARCHITECTURE (extracted from `Doc/FINRECON_MASTER.md` #9–#10, as built)

> Sources -> Ingestion -> Normalization -> Matching -> Deterministic
> Reconciliation -> Exception/Case
>
> +- ML Exception Classifier (advisory)
> +- RAG Knowledge Base (evidence with citations)
> +- Investigation Agent (tool-grounded drafts, human approval required)
> +- Analyst Dashboard (queue, evidence, AI panel, metrics)

## Runtime split

- **finrecon-app** (`:8080`): owns all transactional financial facts —
  ingest (JSON batch + CSV), deterministic reconciliation
  (`RULE_VERSION 1.0.0`), cases/evidence/feedback/audit, read-only
  KPI/ageing/impact projections. One Spring Boot monolith (~600 MB)
  replaces the five P0–P5 microservices; packages under
  `services/finrecon-app/src/main/java/com/finrecon/` are
  `ingestion/`, `reconciliation/`, `exceptioncase/`, `reporting/`,
  `shared/` (domain + web filters).
- **ai-service** (`:8000`): owns intelligence — `/classify`,
  `/investigate` (both read-only advisory; every result carries
  `human_approval_required: true`). Never writes cases, never decides
  numeric truth.
- **PostgreSQL 16** (+ pgvector image): system of record. Schema owned
  by `db/migrations` via Flyway (`ddl-auto=validate`); H2 serves tests
  only. RAG vectors live beside the facts (ADR-004).
- **frontend** (`:3000`): analyst dashboard. Browser calls same-origin
  `/api/*` proxies only; server components hold backend origins.

## Messaging topology

Broker-less by default. The P5 Kafka/Redis path (canonical topic
`finrecon.ingest.v1`, eventId dedupe, 3x retry + DLT) is a documented
deferral for this tree: the messaging code was removed in the monolith
consolidation for the 8GB footprint, and `docker-compose.yml` runs
PostgreSQL only. See ADR-002 and `docs/METRICS.md` (Kafka rows).

## Cross-cutting rules

- **Correlation**: every Java response carries `X-Request-Id`
  (`CorrelationFilter`); the dashboard proxy forwards it end to end.
- **CORS**: one browser origin (`FRONTEND_ORIGIN`, default
  `http://localhost:3000`), GET/POST on `/api/**` only.
- **Money**: `BigDecimal` / JSON strings end to end; the UI repeats
  values verbatim via `display()` and recomputes nothing.
- **AI boundary**: agent reads P4 evidence + P7 excerpts through tools;
  insufficient evidence yields `HUMAN_REVIEW`/`MANUAL_REVIEW`, never a
  guess. Prompt-injection text is data (`test_prompt_injection.py`).
- **Versioning**: rule version on every run, model version on every
  classification, prompt/tool versions on investigations (ADR-008).

## Skeleton / deploy notes

- No gateway service exists in this tree; identity/RBAC termination
  stays a deployment concern (`docs/SECURITY.md` Deferred).
- Reporting lives inside the monolith (`.../reporting/`), not as a
  separate service. CI builds three images: `finrecon-app`,
  `finrecon-ai`, `finrecon-frontend`.
- `docker compose up postgres` is the only infra needed; Java/Python/
  frontend run as local processes for the smallest RAM footprint.
