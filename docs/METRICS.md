# METRICS — every master #27 metric, measured or honestly unmeasured

Measured 2026-09-22 on this monolith tree unless noted. No invented numbers.

## Test counts (green, 2026-09-22)

| Layer | Value | How |
|---|---|---|
| Java (`services/finrecon-app`) | 52 tests, 0 failures/errors | `gradle :services:finrecon-app:test`; XML in `services/finrecon-app/build/test-results/test/` (10 suites) |
| ai-service | 75 passed | `python -m pytest -q` in `ai-service` |
| Repo hygiene + migrations | 17 passed, 1 skipped | `python -m pytest db/tests tests -q` (e2e skipped without `FINRECON_E2E=1`) |
| Frontend | tsc clean + 16 tests | `npm test` in `frontend` (`tsc --noEmit`, `tsconfig.test.json`, `node --test`) |
| Java line coverage | JaCoCo XML/HTML per build | `gradle build` (`jacocoTestReport`, root `build.gradle`) |
| ai-service line coverage | 87% via pytest-cov | dev-only (`requirements-dev.txt`), never in the image |

## Reconciliation

| Metric | Value | Source |
|---|---|---|
| Throughput (records/s) | not measured | needs live stack: start postgres + monolith, run `python scripts/measure_performance.py` |
| Automatic match rate | not measured | same command; artifact `data/evaluation/p13_performance.json` (absent) |
| Rule version | `1.0.0` on every run | `ReconciliationService` / `GET /api/reconcile/runs/{id}` |

## Classifier (P6, synthetic — not operational accuracy)

| Metric | Value | Source |
|---|---|---|
| Macro F1 (validation / heldout) | 1.0 / 1.0, both models | `ai-service/evaluation/p6_metrics.json` |
| Per-class precision/recall | 1.0 all nine classes | same artifact (`heldout.logistic_regression.per_class`) |
| Heldout log loss / Brier (LR) | 0.008936 / 0.000427 | same artifact |
| Leakage checks | pass (groups disjoint, train-only fit, no label fields) | same artifact (`leakage_checks`) |
| Limitation | saturated synthetic baseline; no headroom, no generalization claim | `docs/ML.md` |

## RAG (P7, fixed 12-question synthetic set)

| Metric | Value | Source |
|---|---|---|
| Recall@3 / MRR@3 (memory) | 1.0 / 1.0 | `ai-service/evaluation/p7_metrics.json` |
| Recall@3 / MRR@3 (postgres) | 1.0 / 1.0 | `ai-service/evaluation/p7_postgres_metrics.json` |
| Citation correctness | verified (`citations_verified: true`) | both artifacts |
| Corpus | 8 docs, 24 chunks | same artifacts |

## Agent (P13, fixed 6-case offline set, MockTransport — no network)

| Metric | Value | Source |
|---|---|---|
| Task success rate | 1.0 (6/6, zero failure codes) | `ai-service/evaluation/p13_agent_metrics.json` (`python -m agent.evaluate`) |
| Citation correctness | 1.0 | same artifact |
| Manual review rate | 0.6667 (4 `HUMAN_REVIEW` with drafts + 2 intended `MANUAL_REVIEW`: fee-variance with no fee-rule store, no-evidence case) | same artifact |
| Classifier confidence on the set | not claimed (`confidence: null` — harness runs with no predictor by design) | `docs/TESTING.md` |
| Prompt injection | pinned: injected text cannot change status/summary/draft/approval | `ai-service/tests/test_prompt_injection.py` |

## Latency / Kafka / cost

| Metric | Value | Source |
|---|---|---|
| P95 per endpoint | not measured | needs live stack: `python scripts/measure_performance.py` (monolith `:8080`) |
| Kafka produce throughput / consumer lag | not measured on this tree | historical only: `data/evaluation/p13_kafka.json` (2026-09-18, pre-monolith microservices: 2000 produced, 128.01 msg/s sync ingest, lag 0, unexplained 0). Monolith default is broker-less (ADR-002); no `measure_kafka.py` exists for this tree |
| Inference latency / tokens / cost | not applicable | summary generator is scripted; no external LLM is called |
| Resolution-time change | not measured | no controlled operational measurement exists |

## Demo

6/6 scenarios in `data/demo/scenarios.json` via `python scripts/demo.py`
(requires the live stack); live gate `tests/e2e/test_demo_flow.py` is
skipped unless `FINRECON_E2E=1`. Neither was run live in this session
(no PostgreSQL/Docker in this environment).
