# FinRecon AI — Payment Reconciliation & Exception Resolution

![CI](https://github.com/riddhibantia/FinRecon-AI/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F?logo=springboot&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.12-blue?logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-009688?logo=fastapi&logoColor=white)
![Next.js](https://img.shields.io/badge/Next.js-16-black?logo=next.js&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)

> Deterministic reconciliation of gateway, ledger, and settlement records; exception cases with evidence; advisory ML classification, policy retrieval, and tool-grounded AI investigation drafts that a human must approve.

## Features

- **Deterministic matching** — gateway ↔ ledger ↔ settlement batches, `BigDecimal` money, `X-Request-Id` on every response, Flyway V1–V5 schema.
- **Exception queue** — cases with evidence diffs, assign / resolve / escalate, analyst feedback loop.
- **Advisory AI only** — classifier + policy RAG + LangGraph investigation agent; every draft carries `human_approval_required: true`.
- **Analyst dashboard** — `/`, `/runs`, `/cases`, `/metrics` with KPI cards, ageing reports, table fallback for every chart.
- **Honest metrics** — 52 Java + 75 AI + 17 repo + 16 frontend tests; unmeasured stays "not measured" (`docs/METRICS.md`).

## Tech stack

| Layer | Tech |
|---|---|
| Backend `:8080` | Spring Boot 3.2.5, Java 21, Gradle 8.9 (`services/finrecon-app`) |
| AI service `:8000` | FastAPI, classifier + RAG + LangGraph agent (`ai-service/`) |
| Frontend `:3000` | Next.js 16.3.5, React 19, recharts, lucide-react |
| Data | PostgreSQL 16 + pgvector (Docker, postgres-only compose), Flyway, synthetic demo scenarios |
| Ops | GitHub Actions (Java + Python + frontend + compose + Docker), JaCoCo + pytest-cov, pip/npm audit |

```
Next.js dashboard → Spring Boot monolith (:8080, system of record)
                  → FastAPI ai-service (:8000, advisory only) → Postgres 16 + pgvector
```

## Screenshots

Live captures are produced by the `screenshots` CI job (`dashboard-screenshots` artifact) — see `docs/screenshots/README.md` for regenerate steps:

```
docs/screenshots/home.png         # hero, KPI cards, category chart
docs/screenshots/cases.png        # exception queue
docs/screenshots/case-detail.png  # evidence tabs + sticky rail
docs/screenshots/metrics.png      # charts + table fallback
```

> TODO for demo: commit 3–4 PNGs here (or link the CI artifact publicly) + 60-sec Loom so recruiters see it without running the stack.

## Prerequisites

- Java 21 (Gradle 8.9 provisions the toolchain; `gradle --version`)
- Python 3.10+ (`python --version`; CI uses 3.12 for ai-service)
- Node 18+ (`node --version`), npm 9+
- Docker optional, only for `docker compose up postgres`

## Setup

```powershell
Copy-Item .env.template .env
# Edit .env only for local passwords. Never commit .env.
```

## Run locally (8GB-friendly: postgres in Docker, apps as processes)

```powershell
docker compose up postgres
gradle :services:finrecon-app:bootRun     # :8080
cd ai-service; python -m uvicorn app:app --port 8000  # :8000
cd frontend; npm install; npm run dev     # :3000
```

Flyway migrates on startup; seed with
`psql "$DATABASE_URL" -f db/seed/p1_minimal_seed.sql`.

## Endpoints

| Service | URL |
|---|---|
| finrecon-app health | `http://localhost:8080/api/health`, `/actuator/health` (+ `/liveness`, `/readiness`) |
| Ingest (JSON batch / CSV) | `POST /api/ingest/payments`, `/ledger-entries`, `/settlements` (+ `/csv`) |
| Reconcile | `POST /api/reconcile?sourceSet=NAME`, `GET /api/reconcile/runs/{id}[/results]` |
| Cases | `POST /api/cases/sync`, `GET /api/cases`, `GET /api/cases/{id}`, `.../assign`, `.../resolve`, `.../escalate`, `.../feedback` |
| Reports | `GET /api/reports/kpis`, `GET /api/reports/ageing` |
| ai-service | `http://localhost:8000/health`, `POST /classify`, `POST /investigate` (advisory; `human_approval_required: true`) |
| Dashboard | `http://localhost:3000` — `/`, `/runs`, `/runs/[runId]`, `/cases`, `/cases/[id]`, `/metrics` |

Every Java response carries `X-Request-Id`. Money is `BigDecimal` /
JSON strings; the UI repeats backend values verbatim.

## Gates (all green 2026-09-22)

```powershell
gradle :services:finrecon-app:test        # 52 Java tests
cd ai-service; python -m pytest -q        # 75 tests
python -m pytest db/tests tests -q        # 17 passed, 1 skipped
cd frontend; npm test                     # tsc + 16 tests
cd frontend; npm run build                # production build
docker compose config --quiet             # compose validation
```

Live end-to-end (needs the stack up; skipped by default):

```powershell
python scripts/demo.py                    # 6 scenarios, PASS/FAIL each
$env:FINRECON_E2E=1; python -m pytest tests/e2e -q; Remove-Item Env:FINRECON_E2E
```

Measure live performance (needs the stack up):

```powershell
python scripts/measure_performance.py     # writes data/evaluation/p13_performance.json
cd ai-service; python -m agent.evaluate   # writes evaluation/p13_agent_metrics.json
```

## Project structure

```text
services/finrecon-app/   # monolith: ingestion, reconciliation, exceptioncase, reporting, shared
ai-service/              # classifier/, rag/, agent/, tools/, evaluation/, tests/
frontend/                # app/, components/, lib/
db/migrations/ db/seed/  # Flyway-owned schema
data/demo/               # 6 tracked scenarios (synthetic/ is gitignored)
scripts/                 # demo.py, measure_performance.py
docs/                    # PRD, TRD, ARCHITECTURE, DATABASE, EVENTS, ML, RAG, AGENT, SECURITY, TESTING, METRICS, DECISIONS, adr/
```

## Conventions

- Java/Python 4 spaces, frontend 2 spaces; UTF-8, LF, final newline.
- Spring Boot 3.2.5, Java 21, Gradle 8.9 — do not bump without an ADR.
- Synthetic data only. Secrets in `.env`, never committed.
- Never invent a metric: unmeasured stays "not measured" with a reason.

## For contributors

Spec: `Doc/FINRECON_MASTER.md` is authoritative.
Execution order: `Doc/AGENT_HANDOFF.md`.
Measured numbers: `docs/METRICS.md`. Decisions: `docs/DECISIONS.md`
plus `docs/adr/`.
