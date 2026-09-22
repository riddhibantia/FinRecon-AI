# Contacts — FinRecon AI

Who to contact for what in this repository, and how ownership is split.
No personal phone numbers or private emails are listed here on purpose.
Use Git history and GitHub for direct reach-out. See `docs/SECURITY.md`
for secret-handling rules (never commit `.env` or real credentials).

## Maintainer

- Current maintainer (sole committer per `git shortlog -sne --all`):
  `riddhi` — see `git log --format="%an <%ae> %ad %s"` for history.
- No `CODEOWNERS`, `MAINTAINERS.md`, or remote URL is configured in this
  checkout (`git remote -v` is empty). Add a remote and `CODEOWNERS` when
  the project moves to shared hosting.

## Component owners

Ownership follows `Doc/AGENT_HANDOFF.md`: one owner per file set at a time.
OpenCore owns deterministic facts and product; OMP owns ML/RAG/agent.

| Area | Paths | Owner |
| --- | --- | --- |
| Gateway / ingestion / reconciliation / exceptions / reporting | `services/*` | OpenCore |
| Canonical schema, migrations, seeds | `db/migrations`, `db/seed`, `db/tests` | OpenCore |
| Messaging / reliability (Kafka, Redis) | `services/ingestion-service/src/**/messaging`, `docker-compose.yml` | OpenCore (P5) |
| Baseline classifier (dataset, features, training) | `ai-service/classifier`, `ai-service/evaluation/p6_metrics.json` | OMP (P6) |
| Policy knowledge base / retrieval | `ai-service/rag`, `ai-service/data/policies`, `ai-service/evaluation/p7*.json` | OMP (P7) |
| Investigation agent (LangGraph workflow, tools) | `ai-service/agent`, `ai-service/tools` | OMP (P8) |
| Analyst dashboard | `frontend/app`, `frontend/components`, `frontend/lib` | OpenCore (P10) |
| Security boundaries, hygiene scan | `docs/SECURITY.md`, `tests/test_repo_hygiene.py`, `ai-service/tests/test_api_boundaries.py` | OpenCore (P11) |
| Operations, observability, CI/CD, Docker | `docs/OPERATIONS.md`, `.github/workflows/ci.yml`, `*/Dockerfile`, `docker-compose.yml` | OpenCore (P12) |
| Spec and execution order | `Doc/FINRECON_MASTER.md`, `Doc/AGENT_HANDOFF.md` | Shared source of truth |

## Functional roles (from spec)

From `Doc/FINRECON_MASTER.md` #2.2 and
`ai-service/data/policies/escalation_matrix.md` (synthetic demo data only):

- Reconciliation Analyst — owns a case until a human accepts handoff.
- Payment Operations — missing batches, duplicates, delivery delays.
- Treasury — historical FX-rate questions.
- Contract Owner — disputed merchant terms.
- Operations Manager — discrepancies at/above demo threshold
  (INR 100000.00 in synthetic matrix) or cases ageing past two calendar
  days. Attach source IDs and last action; escalation never authorizes a
  write-off.
- System Administrator — local env (`.env.template`), Postgres/Kafka/Redis,
  Flyway migrations, Docker builds.
- ML/AI Engineer — classifier, RAG, and agent evaluation artifacts.

AI output is advisory only (`human_approval_required: true`); every
investigation needs human review. See `docs/AGENT.md` and `docs/RAG.md`.

## How to get help

1. Reproduce locally per `README.md` and `docs/OPERATIONS.md` health table
   (finrecon-app `:8080`, ai-service `:8000`, frontend `:3000`).
2. Include the `X-Request-Id` response header and the failing command plus
   the relevant gate: `gradle build`, `python -m pytest tests -q` and
   `db/tests`, `npm test` in `frontend`, or the CI job in
   `.github/workflows/ci.yml`.
3. For data issues, state the Flyway version (`db/migrations/V*`) and
   whether `db/seed/p1_minimal_seed.sql` was applied.
4. Security-sensitive reports: do not open a public issue with payloads or
   logs containing PII. Redact to IDs and counts first (logs carry no
   payloads by construction).

## Updating this file

Keep this file free of secrets, card-like numbers, and private keys —
`tests/test_repo_hygiene.py` scans every tracked file. When ownership or
escalation policy changes, update this table together with
`Doc/FINRECON_MASTER.md` and `docs/DECISIONS.md`.
