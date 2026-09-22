# SECURITY — P11 integration boundaries

Threat model: synthetic financial data, local-first deployment, browser
dashboard, optional brokers. The rules below are implemented and tested;
full identity/RBAC termination stays a deployment concern (see Deferred).

## Trust boundaries

- Browser → dashboard (`/api/*` proxies) → Java services / ai-service.
- The proxies forward to an explicit per-route target map only
  (`frontend/lib/backend.ts`); unknown case actions resolve to null and
  the route returns 404. Unit-tested (`test/backend.test.mjs`).
- Browsers may call `/api/**` from one configured origin only
  (`finrecon.frontend.origin`, default `http://localhost:3000`), GET/POST
  only, no credentials. Live preflight tests per service prove the
  dashboard origin passes and foreign origins get 403.
- Service-to-service calls are server-side (ai-service `CaseTransport`
  GETs P4 case endpoints) and unaffected by CORS.

## Correlation

Every Java response carries `X-Request-Id` (generated UUID when the caller
sends none; `CorrelationFilter`, tested per service) and the id is in log
MDC. The dashboard proxy generates one per proxied call when absent and
returns the upstream value otherwise.

## AI safety (the human-review boundary)

- ai-service exposes read-only advisory routes only: `GET /health`,
  `POST /classify`, `POST /investigate`. Pinned by
  `ai-service/tests/test_api_boundaries.py` (no PUT/DELETE/PATCH; POST set
  is exactly those two).
- Every investigation result requires `human_approval_required: true`
  (Pydantic `Literal[True]`, pinned by the same test file).
- The agent has no resolve/escalate transport and never mutates cases
  (P8 suite). The dashboard reaches the AI only through
  `POST /api/ai/investigate`; `/classify` is deliberately unexposed
  because the UI cannot build validated snapshots.
- The AI layer cannot bypass deterministic reconciliation or database
  controls: it reads P4 evidence and P7 excerpts, writes nothing.
- Prompt-injection text inside retrieved policies or case notes is data, not
  instruction. `ai-service/tests/test_prompt_injection.py` pins that injected
  text cannot add claims to the summary, cannot change the draft action, and
  cannot bypass `human_approval_required` — the grounded-summary contract
  plus citation source verification hold even against a forged policy hit.

## Input validation and auditability

- P2 rejects malformed rows with per-row errors; P4 enforces the case
  lifecycle in the entity (illegal moves are 422); ai-service validates
  snapshots/payloads with Pydantic (422 on violation).
- Every case transition writes a `resolution_actions` row and an
  `audit_logs` row (P4 tests). Reconciliation runs record `rule_version`.

## Secrets and repo hygiene

- Real secrets live in `.env` (never committed); only `.env.template`
  with local placeholders is tracked. All Spring datasource passwords are
  `${...}` placeholders; compose defaults are documented local values.
- `tests/test_repo_hygiene.py` scans tracked files: no private keys, no
  Luhn-valid card-like numbers, no `.env`, password values restricted to
  placeholders/local defaults.

## Deferred (deployment-time)

- Identity provider, RBAC role enforcement (`ANALYST/MANAGER/ADMIN/
  SERVICE`), rate limiting, and TLS termination belong at the
  gateway-service / ingress layer, which remains a skeleton. Until then,
  do not expose these services beyond localhost or a trusted network.
- PII masking in logs beyond request-ID correlation (P12 structured logs
  carry no payloads by construction).
