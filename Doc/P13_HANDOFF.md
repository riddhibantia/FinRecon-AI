# P13 Handoff — monolith consolidation + completion (2026-09-22)

## What changed (5 commits on `master`)

1. `f427085` monolith: 5 Spring services → `services/finrecon-app`
   on `:8080` (~600 MB vs ~2.5 GB). P5 messaging/Redis code removed;
   compose runs PostgreSQL only.
2. `f44c7ad` agent: monolith wiring + fixed 6-case offline set
   (`agent/evaluate.py`, `agent_cases.json`, `p13_agent_metrics.json`).
3. `c578515` dashboard: monolith proxies + `FeedbackForm`,
   `/metrics` page, reports proxies.
4. `bdc0f4e` demo: 6 monolith scenarios, `measure_performance.py`,
   e2e gate, CONTACTS + handoff docs.
5. `9cf12df` docs: PRD/TRD/ARCHITECTURE extracted, 8 ADRs,
   `METRICS.md`, README retitle, OPERATIONS refresh.

## Gates (green 2026-09-22, this machine)

- Java: 52 tests, 0 failures (`gradle :services:finrecon-app:test`)
- ai-service: 75 passed (`python -m pytest -q`)
- Repo: 17 passed, 1 skipped (`db/tests tests`; skip = live e2e)
- Frontend: tsc clean + 16 tests (`npm test`), production build OK
  (routes `/`, `/cases`, `/cases/[id]`, `/metrics`, `/runs`, `/runs/[runId]`)
- Hygiene: 4 passed, 1 skipped (`python -m pytest tests -q`)

## Not run here (no PostgreSQL/Docker in this environment)

- `python scripts/demo.py`, `FINRECON_E2E=1` e2e,
  `python scripts/measure_performance.py`, `docker compose config`.
  P95/throughput/match rate recorded as not measured with rerun
  commands (`docs/METRICS.md`). Nothing estimated.

## Known gaps / next

- Kafka: historical artifact only (`p13_kafka.json`, pre-monolith);
  reintroduce behind flags if brokers exist (ADR-002).
- `DESIGN.md`, `design-mockups.html` (unrelated Nike commerce) stay
  untracked — delete or move out when convenient.
- No remote configured (`git remote -v` empty). To publish:

```powershell
gh repo create FinRecon-AI --private --source=. --push
# or:
git remote add origin <url>; git push -u origin master --follow-tags
```

## Stack (8GB)

Monolith `:8080` + ai-service `:8000` + frontend `:3000` as local
processes; only PostgreSQL in Docker. CI builds 3 images and runs
coverage + `pip-audit` / `npm audit` gates.
