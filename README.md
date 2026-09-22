# FinRecon AI — P0 Project Foundation

P0 only. Skeletons, health endpoints, and local run docs. No business logic.

Spec: `Doc/FINRECON_MASTER.md` is authoritative. `Doc/AGENT_HANDOFF.md` is the execution order. `docs/` holds phase placeholders that get filled in later phases.

## What P0 contains

- 1 Spring Boot monolith (Java 21, Spring Boot 3.2.5, Gradle): `services/finrecon-app` — ingestion, reconciliation, exception/case, and reporting APIs on port 8080. Exposes `GET /api/health` and Actuator `GET /actuator/health`.
- 1 FastAPI skeleton (`ai-service/app.py`): `GET /health`. Subpackages `classifier`, `rag`, `agent`, `tools`, `evaluation` are empty placeholders.
- 1 Next.js skeleton (`frontend/`): home page plus `GET /api/health`.
- `docker-compose.yml`: PostgreSQL only (pgvector image for future P7, RAG not enabled).
- `db/migrations`, `db/seed`, `data/synthetic`, `data/evaluation`: empty placeholders.
- `.env.template`, `.github/workflows/ci.yml`, `.editorconfig`, `.gitignore`.

## What P0 does NOT contain

No domain tables, migrations, canonical models, ingestion, matching, reconciliation rules, exception/case APIs, ML, RAG, LangGraph, fine-tuning, Kafka, Redis, Kubernetes, AWS, or dashboard logic. See `docs/DECISIONS.md`.

## Prerequisites

- Java 21 (`java -version`)
- Gradle 8.9 (`gradle --version`). Maven is not required for P0.
- Python 3.10+ (`python --version`). CI uses 3.11 per spec; P0 code runs on both.
- Node 18+ (`node --version`), npm 9+.
- Docker optional. Only needed for `docker compose up postgres`. Not required for P0 health checks.

## Setup

```powershell
Copy-Item .env.template .env
# Edit .env only for local passwords. Never commit .env.
```

Install and verify (Windows PowerShell from repo root):

```powershell
# Java builds + tests
gradle build

# Python health test (uses installed fastapi, pytest)
cd ai-service
python -m pytest tests -v
cd ..

# Frontend skeleton test (no install needed)
cd frontend
npm test
cd ..
```

Full frontend check (downloads Next.js, takes a few minutes):

```powershell
cd frontend
npm install
npm run build
cd ..
```

## Run locally (monolith)

One Java app now serves every backend API on port 8080. No Docker needed
except optionally for PostgreSQL.

```powershell
# Terminal 1 (Java monolith — ingestion + reconciliation + cases + reporting)
gradle :services:finrecon-app:bootRun

# Terminal 2 (Python AI service — optional, only when testing the classifier)
cd ai-service
python -m uvicorn app:app --port 8000
cd ..

# Terminal 3 (frontend)
cd frontend
npm install
npm run dev
cd ..
```

Postgres (optional, needs Docker):

```powershell
docker compose up postgres
```

## Health endpoints

| Service | URL |
|---|---|
| finrecon-app | http://localhost:8080/api/health and /actuator/health |
| ai-service | http://localhost:8000/health |
| frontend | http://localhost:3000/api/health |

Expected shape: `{"status":"UP","service":"<name>"}`.

Quick check:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://localhost:8000/health
Invoke-RestMethod http://localhost:3000/api/health
```

## P2 ingestion (finrecon-app :8080, needs PostgreSQL at runtime)

JSON batch endpoints (`application/json` array, 200 with counts; 400 on
empty/unparseable body):

- `POST /api/ingest/payments`
- `POST /api/ingest/ledger-entries`
- `POST /api/ingest/settlements`

CSV upload endpoints (`multipart/form-data`, field `file`, header row required):

- `POST /api/ingest/payments/csv` — columns:
  `external_txn_id,customer_id,merchant_id,amount,currency,status,event_time`
- `POST /api/ingest/ledger-entries/csv` — columns:
  `external_txn_id,gross_amount,fee_amount,net_amount,currency,posting_status,posted_at`
- `POST /api/ingest/settlements/csv` — columns:
  `external_txn_id,settled_amount,fee_amount,currency,settlement_status,settlement_date,batch_id`

Ledger/settlement rows reference the payment by `external_txn_id`; unknown
references are rejected. Every response is a `BatchResult`
(`requestId,sourceType,status,accepted,duplicates,rejected,errors[]`) and
carries `X-Request-Id`. Repeats are idempotent (payments via
`UNIQUE(external_txn_id)`; ledger/settlement via exact-duplicate match).

## P3 reconciliation (finrecon-app :8080, needs PostgreSQL at runtime)

- `POST /api/reconcile?sourceSet=NAME` — runs the deterministic engine
  (rule version `1.0.0`) over all known payments; returns run summary
  (`runId,status,ruleVersion,total,matched,mismatched`).
- `GET /api/reconcile/runs/{runId}` and `.../results` — read a run and its
  per-payment results (`MATCHED` or `MISMATCHED` + taxonomy `mismatch_type`
  + `amountDifference`). Unknown runs are 404.
- Checks in fixed order: duplicate, missing, amount, unknown, fee, net,
  FX, status, late settlement. No LLM/ML decides numeric truth.

## P4 cases (finrecon-app :8080, needs PostgreSQL at runtime)

- `POST /api/cases/sync {"runId":"..."}` — opens one case per MISMATCHED
  result (idempotent; matched results never become cases).
- `GET /api/cases?status=&category=&assignedTo=` — analyst queue filters.
- `GET /api/cases/{id}` — detail: exception, evidence, source records,
  resolution actions, audit trail, rule version.
- `POST /api/cases/{id}/assign`, `/resolve`, `/escalate` — lifecycle
  transitions; illegal moves are 422 with `ILLEGAL_TRANSITION`.
- `POST /api/cases/{id}/feedback` — FR-11 append-only analyst
  confirm/correct; audited and shown on the case detail.

## FR-13 reporting (finrecon-app :8080, needs PostgreSQL at runtime)

- `GET /api/reports/kpis` — runs, results, auto-match rate, case counts by
  status/category/severity, unresolved impact, feedback totals. Read-only
  projections over the shared schema; nothing is recomputed downstream.
- `GET /api/reports/ageing` — unresolved counts against the two-calendar-day
  escalation boundary and the oldest unresolved case.

## P5 async path (same service, needs Kafka/Redis only when enabled)

- Sync ingest also publishes one event per accepted row to
  `finrecon.ingest.v1` (best-effort; sync results stand without a broker).
- The consumer dedupes by `eventId` and replays through the idempotent
  store path; 3x backoff retry then DLT; validation failures ack, no retry.
- Enable where brokers exist: `finrecon.messaging.enabled=true` with
  `KAFKA_BOOTSTRAP_SERVERS`, and `finrecon.redis.enabled=true` for Redis
  dedupe (in-memory otherwise). `docker compose up kafka redis` locally.
- Contracts: `docs/EVENTS.md`. Live broker validation is out of scope on
  machines without Docker; broker-free tests cover envelope, dedupe,
  replay, and bean conditionality.

## P10 dashboard (frontend :3000, needs backends for data)

```powershell
cd frontend
npm install
npm test      # tsc + 14 contract tests
npm run build # production build
npm run dev   # http://localhost:3000
```

- Origins come from `FINRECON_*_API_URL` (server-side only, see
  `.env.template`); the browser calls same-origin `/api/*` proxies.
- Pages: `/` service status, `/runs` start-a-run + `/runs/[runId]` results,
  `/cases` filterable queue, `/cases/[id]` evidence, sources, cited AI
  investigation, lifecycle actions, analyst corrections (FR-11), audit trail,
  `/metrics` operational KPIs and ageing (FR-13).
- The UI repeats backend facts verbatim and computes no financial truth.
  Unreachable backends render honest error panels (verified live against
  stopped backends); the AI panel reports 503/unreachable without guessing.
- AI access is read-only (`POST /investigate` via proxy); the dashboard
  cannot train models or mutate cases through the AI service.

## P11 boundaries (all services)

- Every Java response carries `X-Request-Id` (generated when absent);
  the dashboard proxy forwards it end to end.
- Browsers may call `/api/**` from `FRONTEND_ORIGIN`
  (default `http://localhost:3000`) with GET/POST only.
- ai-service exposes read-only advisory routes only (`/health`,
  `/classify`, `/investigate`); every investigation requires human
  approval. See `docs/SECURITY.md`.
- `python -m pytest tests -q` (repo root) scans tracked files for
  secrets and card-like data.

## P12 operations (see docs/OPERATIONS.md for the runbook)

- CI (`.github/workflows/ci.yml`): Gradle build, full ai-service suite,
  repo Python checks, frontend test+build, compose validation, and Docker
  builds of all seven images. Merge only when green.
- Logs carry `[requestId]` on every request; counters
  (`finrecon.ingest.*`, `finrecon.recon.*`, `finrecon.cases.*`) expose
  outcomes per category.
- Liveness/readiness probes at `/actuator/health/liveness|readiness`;
  gate orchestrator traffic on readiness.
- `docker compose up --build` runs infra plus the working services where
  Docker exists. Images are built by CI, not claimed working otherwise.

## Project structure

```text
settings.gradle / build.gradle / gradle.properties
services/gateway-service, ingestion-service, reconciliation-service, exception-service, reporting-service
ai-service/app.py, classifier/, rag/, agent/, tools/, evaluation/, tests/
frontend/app/, test/
db/migrations, db/seed, data/synthetic, data/evaluation
docs/PRD.md, TRD.md, ARCHITECTURE.md, DATABASE.md, EVENTS.md, ML.md, RAG.md, AGENT.md, SECURITY.md, TESTING.md, DECISIONS.md
docker-compose.yml, .env.template, .github/workflows/ci.yml
Doc/FINRECON_MASTER.md, Doc/AGENT_HANDOFF.md
```

## Conventions (P0)

- Java: 4 spaces, UTF-8, `Application` + `HealthController` per service. `gradle build` must stay green.
- Python: 4 spaces, FastAPI + pytest. `python -m pytest tests -v` must stay green.
- Frontend: 2 spaces, TypeScript, App Router. `npm test` must stay green.
- `.editorconfig` enforces charset, LF, final newline, trailing-whitespace trim.
- Branching: one phase per branch, e.g. `p0/foundation`. Commit after each green phase before handoff.
- Secrets: `.env` only, never committed. Synthetic data only.
- One owner per file set at a time (OpenCore for P0-P5, OMP for P6-P9).

## P0 exit check

- [ ] `gradle build` passes (10 Java health tests)
- [ ] `python -m pytest tests -v` passes (1 test)
- [ ] `npm test` passes (2 tests)
- [ ] Each service answers its health endpoint locally
- [ ] `docker-compose.yml` defines postgres only (run manually where Docker exists)
- [ ] No domain logic, migrations, ML, RAG, agent, Kafka, or Redis code present

## Next step

P1 only: canonical domain model, PostgreSQL migrations, constraints, indexes, seed data, and source fixtures. Do not start P2-P13.
