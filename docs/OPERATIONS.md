# OPERATIONS — P12 runbook

How to run, configure, observe, and ship FinRecon AI. All values below
are local defaults; production overrides them through the environment.

## Services and ports

| Service | Port | Health | Readiness |
|---|---|---|---|
| gateway-service | 8080 | `/actuator/health` | skeleton (no probes) |
| ingestion-service | 8081 | `/actuator/health` | `/actuator/health/readiness` |
| reconciliation-service | 8082 | `/actuator/health` | `/actuator/health/readiness` |
| exception-service | 8083 | `/actuator/health` | `/actuator/health/readiness` |
| reporting-service | 8084 | `/actuator/health` | skeleton (no probes) |
| ai-service | 8000 | `/health` | n/a (advisory; 503 without model/case API) |
| frontend | 3000 | `/api/health` | n/a |

Liveness (`/actuator/health/liveness`) means the process runs; readiness
adds the database and disk. Orchestrators must gate traffic on readiness.

## Configuration

All services read the environment (see `.env.template`); nothing secret is
baked into images:

- `POSTGRES_*`, `DATABASE_URL` — PostgreSQL connection.
- `FRONTEND_ORIGIN` — the one browser origin allowed by CORS.
- `FINRECON_*_API_URL` — dashboard-to-backend origins (server-side only).
- `FINRECON_MODEL_DIR`, `FINRECON_CASE_API_URL` — ai-service wiring.
- `finrecon.messaging.enabled` + `KAFKA_BOOTSTRAP_SERVERS` — async path.
- `finrecon.redis.enabled` + `REDIS_HOST/REDIS_PORT` — Redis dedupe
  (also set `management.health.redis.enabled=true` when enabled).

## Logging and metrics

- Console logs carry `[requestId]` on every request (`logback-spring.xml`
  per service). Logs contain identifiers and counts, never payloads or PII.
- Counters (Micrometer, scraped from `/actuator/prometheus` where the
  Prometheus registry is added, else via `/actuator/metrics`):
  `finrecon.ingest.accepted/rejected{sourceType}`,
  `finrecon.recon.runs{status}`, `finrecon.recon.results{matchStatus}`,
  `finrecon.cases.opened{category}`, `finrecon.cases.transitions{action}`.

## Local deployment

```powershell
Copy-Item .env.template .env
docker compose up --build postgres kafka redis
docker compose up --build ingestion-service reconciliation-service exception-service ai-service frontend
```

Flyway migrates on service startup; seed with
`psql "$DATABASE_URL" -f db/seed/p1_minimal_seed.sql`. App images were
authored without a local Docker daemon: CI builds every image on each
push (see `.github/workflows/ci.yml`), and no built image is claimed
working until that job is green.

## CI quality gates

`gradle clean build` (69+ Java tests) · full ai-service pytest under
Python 3.12 · repo Python checks (`db/tests`, `tests/`) · frontend
`tsc + tests + production build` · `docker compose config` · Docker
build of all seven images. A phase merges only with every gate green.
