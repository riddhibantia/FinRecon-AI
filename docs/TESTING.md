# TESTING

Strategy lives in `Doc/FINRECON_MASTER.md` #20. This file records what is
actually wired and how to run each gate.

## Gates

| Gate | Command | Scope |
|---|---|---|
| Java build + tests | `gradle build` | All 5 Spring services; domain, controller, CORS, observability, feedback, reporting |
| ai-service unit + agent | `cd ai-service; python -m pytest tests -q` | 73 tests: classifier, RAG, agent graph, API boundaries, evaluation harness |
| Repo hygiene | `python -m pytest tests -q` (repo root) | No secrets / card-like data in tracked files |
| DB migrations | `python -m pytest db/tests -q` | Flyway migration shape and constraints |
| Frontend | `cd frontend; npm test` | `tsc` strict + 16 contract tests (`node --test`) |
| Frontend lint | `cd frontend; npm run lint` | ESLint, zero warnings |
| Live e2e (opt-in) | `$env:FINRECON_E2E=1; python -m pytest tests/e2e -q` | Needs the full stack up; skipped by default |

## P13 agent evaluation (fixed, offline)

`ai-service/evaluation/agent_cases.json` is a fixed six-case set replayed
through `httpx.MockTransport` — no live backend, no factory, no default, so
the score cannot be inflated by a running system. Run:

```powershell
cd ai-service
python -m pytest tests/test_agent_evaluation.py -q
python -m agent.evaluate   # writes evaluation/p13_agent_metrics.json
```

Latest measured result (`p13_agent_metrics.json`): **task_success_rate 1.0,
citation_correctness 1.0, manual_review_rate 0.6667, zero failure codes.**

Read these numbers honestly:

- `task_success_rate` = cases whose status, draft boundary, citations, and
  summary matched the recorded expectation.
- `manual_review_rate` counts top-level `HUMAN_REVIEW` outcomes; the two
  `MANUAL_REVIEW` cases are the FEE_VARIANCE case (no executable fee-rule
  store by design) and the no-evidence case — both are *intended* safe
  outcomes, and the set asserts them.
- Agent draft + citation behavior **is** measured. Classifier confidence on
  the fixed set **is not claimed** — the harness runs with no predictor, so
  `confidence` stays `None` and `confidence_reason` explains why.

If a case ever fails, fix the case expectation or the recorded payload —
never weaken the scorer.
