# OPERATIONS — P12 runbook

How to run, configure, observe, and ship FinRecon AI. All values below
are local defaults; production overrides them through the environment.

## Services and ports

| Service | Port | Health | Readiness |
|---|---|---|---|
| finrecon-app | 8080 | `/actuator/health` | `/actuator/health/readiness` |
| ai-service | 8000 | `/health` | n/a (advisory; 503 without model/case API) |
| frontend | 3000 | `/api/health` | n/a |

Liveness (`/actuator/health/liveness`) means the process runs; readiness
adds the database and disk. Orchestrators must gate traffic on readiness.

## Configuration

All services read the environment (see `.env.template`); nothing secret is
baked into images:

- `POSTGRES_*`, `DATABASE_URL` — PostgreSQL connection.
- `FRONTEND_ORIGIN` — the one browser origin allowed by CORS.
- `FINRECON_API_URL` — dashboard-to-backend origin (server-side only).
- `FINRECON_MODEL_DIR`, `FINRECON_API_URL` — ai-service wiring.

## Logging and metrics

- Console logs carry `[requestId]` on every request (`logback-spring.xml`
  in `services/finrecon-app`). Logs contain identifiers and counts, never
  payloads or PII.
- Counters (Micrometer, scraped from `/actuator/prometheus` where the
  Prometheus registry is added, else via `/actuator/metrics`):
  `finrecon.ingest.accepted/rejected{sourceType}`,
  `finrecon.recon.runs{status}`, `finrecon.recon.results{matchStatus}`,
  `finrecon.cases.opened{category}`, `finrecon.cases.transitions{action}`.

## Local deployment

```powershell
Copy-Item .env.template .env
docker compose up postgres  # optional; only if you want a real DB
gradle :services:finrecon-app:bootRun
cd ai-service; python -m uvicorn app:app --port 8000
cd frontend; npm run dev
```

Flyway migrates on service startup; seed with
`psql "$DATABASE_URL" -f db/seed/p1_minimal_seed.sql`.

## CI quality gates

`gradle clean build` (52+ Java tests) · full ai-service pytest under
Python 3.12 · repo Python checks (`db/tests`, `tests/`) · frontend
`tsc + tests + production build` · `docker compose config`.
