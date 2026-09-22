# OPERATIONS — runbook

How to run, configure, observe, and ship FinRecon AI. All values are
local defaults; production overrides them through the environment.

## Services and ports

| Service | Port | Health | Readiness |
|---|---|---|---|
| finrecon-app (monolith) | 8080 | `/api/health`, `/actuator/health` | `/actuator/health/readiness` |
| ai-service | 8000 | `/health` | n/a (advisory; 503 without model/case API) |
| frontend | 3000 | `/api/health` | n/a |
| postgres | 5432 | `pg_isready` | n/a |

Liveness means the process runs; readiness adds the database and
disk. Gate orchestrator traffic on readiness.

## Configuration

All services read the environment (see `.env.template`); nothing
secret is baked into images:

- `POSTGRES_*`, `DATABASE_URL` — PostgreSQL connection.
- `FINRECON_PORT`, `AI_SERVICE_PORT`, `FRONTEND_PORT` — local ports.
- `FRONTEND_ORIGIN` — the one browser origin allowed by CORS.
- `FINRECON_API_URL`, `FINRECON_AI_API_URL` — dashboard-to-backend
  origins (server-side only; the browser calls `/api/*`).
- `FINRECON_MODEL_DIR` — ai-service model dir (read-only mount).

## Logging and metrics

- Console logs carry `[requestId]` on every request
  (`logback-spring.xml`). No payloads or PII by construction.
- Counters (Micrometer via `/actuator/metrics`, Prometheus registry
  where added): `finrecon.ingest.accepted/rejected{sourceType}`,
  `finrecon.recon.runs{status}`, `finrecon.recon.results{matchStatus}`,
  `finrecon.cases.opened{category}`,
  `finrecon.cases.transitions{action}`,
  `finrecon.cases.feedback` (FR-11 corrections).
- Reports: `GET /api/reports/kpis` (runs, results, auto-match rate,
  cases by status/category/severity, unresolved impact, feedback
  totals) and `GET /api/reports/ageing` (2-calendar-day boundary).

## Local deployment (8GB)

```powershell
Copy-Item .env.template .env
docker compose up postgres          # only infra in Docker
gradle :services:finrecon-app:bootRun
cd ai-service; python -m uvicorn app:app --port 8000
cd frontend; npm install; npm run dev
```

Flyway migrates on service startup; seed with
`psql "$DATABASE_URL" -f db/seed/p1_minimal_seed.sql`.
Demo: `python scripts/demo.py` (needs the stack).
Measure: `python scripts/measure_performance.py`,
`cd ai-service; python -m agent.evaluate`.

## CI quality gates

`gradle clean build` (52 Java tests + JaCoCo XML/HTML) · full
ai-service pytest (75) · repo Python checks (`db/tests`, `tests/`)
· frontend `tsc + tests + production build` · `docker compose
config` · three image builds (`finrecon-app`, `finrecon-ai`,
`finrecon-frontend`) · `coverage` summary job · `security` job
(`pip-audit`, `npm audit --omit=dev --audit-level=high`).
Java OWASP scan deferred (needs an NVD API key).
