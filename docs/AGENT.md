# P8 investigator

P8 uses a real LangGraph `StateGraph` with deterministic Python nodes. It does not call an LLM or need an LLM API key. Financial facts come from P4 case tools; policy text comes from the P7 retriever. The bundled policy corpus and test cases are synthetic, not production financial advice.

## Workflow

`classify_exception -> load_evidence -> query_records -> find_similar -> retrieve_policy -> analyze_root_cause -> draft_resolution -> verify_evidence_and_citations -> generate_case_summary -> human_review -> END`

- Classification consumes the P6 `Predictor.predict(Snapshot)` interface through an injected source snapshot loader. A P4 case summary is not a P6 snapshot. Missing snapshots/artifacts leave confidence null with a reason; no source values are guessed.
- `load_evidence` takes P4 compared fields, not free-form source summaries. Source-only rows without a compared field remain source references, not fabricated financial evidence.
- `query_records` loads payment, ledger, and settlement projections and calculates differences only for numeric comparison fields. Explicit request tolerance is optional; absent tolerance yields no within-tolerance judgment. A tolerance does not override P3 classification or imply that a case is resolved.
- Similar cases use the P4 category-filtered queue, excluding the current case. Similarity is not a probability or a vector search.
- Root cause is the P3 `mismatchType`, falling back to the stored deterministic category. A classifier prediction never replaces it. Classifier/category disagreement, missing category-specific fields, contradictory values for the same record/field, invalid tool results, or failed verification require manual review.
- Drafts contain a review recommendation and citations. Fee/FX cases require manual review because there is no executable fee-rule or historical FX-rate store.
- Every included citation must match a retrieved hit and an independently re-chunked file in the operator-configured P7 corpus: exact excerpt, chunk identity, document/version, section/page, URI, source hash, line range, and effective dates. Hit-provided URIs are never opened. Replaced or unavailable source files invalidate citations. Every included evidence row must match the case tool's source type, source ID, field, expected value, and observed value.
- The injectable summary generator may select/reorder already-grounded sentences only. Added statements are rejected and force manual review. This deliberately is not unrestricted LLM generation.

## Tools

All tools are registered in `agent.tools.ToolRegistry` and invoked with `invoke(name, **arguments)`. Bad inputs or transport/result errors raise `ToolError`; the graph converts these to controlled review outcomes.

| Tool | Inputs and behavior |
| --- | --- |
| `get_payment` | `exception_id`; P4 `payment_gateway` sources and compared evidence. No parsing amounts from display summaries. |
| `get_ledger_entries` | `exception_id`; ledger sources and evidence. |
| `get_settlement_records` | `exception_id`; settlement sources and evidence, including explicit absent-record evidence. |
| `calculate_variance` | A tool-provided `evidence` row and optional decimal-string `tolerance`; Decimal observed minus expected, absolute difference, and optional inclusive tolerance comparison. Rejects floats, nonfinite values, and nonnumeric evidence. |
| `get_fee_rule` | `query`, optional `as_of`; cited policy text, `rule_verified=false`. Does not calculate fees. |
| `get_fx_reference` | `query`, optional `as_of`; cited policy text, `rule_verified=false`. Does not invent rates. |
| `find_similar_exceptions` | `exception_id`, `category`; GET category-filtered P4 queue, excluding current case. |
| `search_policy` | `query`, optional `as_of`; P7 top-three search results with original provenance. |
| `get_case_history` | `exception_id`; P4 `caseActions` and `auditTrail`. |
| `create_resolution_draft` | `exception_id`, `action`, `citations`; in-memory DRAFT with `human_approval_required=true`. No HTTP call. |
| `record_analyst_feedback` | `exception_id`, `actor_id`, `notes`; by default returns `{status_code: 501, status: NOT_IMPLEMENTED, reason: ...}` without a request or persistence. |

Internal fact tools `get_case` and `get_classifier_snapshot` supply the case envelope and optional validated P6 snapshot. Within one investigation, record/history tools receive the already-fetched case to avoid inconsistent repeated reads.

`CaseTransport` accepts an `httpx.Client`, so tests use `httpx.MockTransport`. The only built-in network operations are `GET /api/cases/{exceptionId}` and `GET /api/cases?category=...`. P4 has no feedback endpoint. An operator may inject `feedback_sender(**payload)` once a real backend contract exists; that adapter owns its endpoint, authentication, POST, and response mapping. P8 neither invents a URL nor claims feedback was saved. The workflow never invokes feedback automatically.

## API and output

`POST /investigate` accepts:

```json
{"exceptionId":"11111111-1111-4111-8111-111111111111","as_of":"2026-06-01","tolerance":"0.01"}
```

Only `exceptionId` is required. `as_of` defaults to today's date. The caller must supply the applicable policy date and any known tolerance; neither is inferred from display strings.

The response always contains:

- `exceptionId`: case UUID string (P4 naming convention).
- `root_cause`: stored mismatch/category, or null if the case is unavailable.
- `summary`: sentences built solely from tool evidence and verified policy excerpts.
- `recommended_action`: human review recommendation, or null for manual-review failures.
- `confidence`: P6 winning-class probability in [0,1], or null. This is classification confidence, not resolution certainty.
- `confidence_reason`: reason when confidence is unavailable or withheld.
- `evidence`: `{source_type, source_id, field, expected, observed}` objects. Source values retain their original text/null representation.
- `citations`: `{document, version, section, page, excerpt, score}` objects. Invalid citations are dropped and noted in `reasons`.
- `status`: `HUMAN_REVIEW` when a verified draft is available; `MANUAL_REVIEW` on insufficient/conflicting/invalid evidence.
- `reasons`: explicit verification or availability failures.
- `draft`: in-memory approval-required draft, or null for manual review.
- `human_approval_required`: always true.

The master sketch uses `exception_id`; this API deliberately uses the assigned P8/P4 `exceptionId` contract. No compatibility alias is exposed.

### Human approval boundary

Both terminal statuses require a person. The graph ends at human review; it has no resume-to-resolution edge, resolve/escalate tool, or automatic case mutation. An analyst must use the existing P4 controls separately. Drafts are not persisted or submitted to P4.

### Running locally

From `ai-service`, install/run with the repo's Python 3.12 environment:

```powershell
py -3.14 -m uv run --python 3.12 --no-project --with-requirements requirements.txt python -m uvicorn app:app --port 8000
```

For the default HTTP route, set `FINRECON_API_URL` to the monolith origin (default `http://localhost:8080`). Set `FINRECON_MODEL_DIR` to an operator-trusted P6 model directory, or use the default `ai-service/artifacts/p6/models`. `/investigate` returns HTTP 503 when model artifacts or the case API configuration are absent. `/health` remains available. The default route uses the local in-memory P7 retriever and does not connect to Postgres. The Python API accepts an injected retriever and `SourceVerifier(policy_dir=...)` for another trusted corpus.

Default P4 records lack the full P6 numeric/timestamp snapshot. Thus the default HTTP integration reports null classifier confidence even when a model is installed. To use classification, construct `ToolRegistry(transport, retriever, snapshot_loader=...)` with an actual source adapter, then `Investigator(tools, predictor)`. `create_app(..., investigator=...)` supports this configured investigator without adding a public endpoint that accepts untrusted source facts.

LangGraph is pinned to `1.2.11`; its `langchain-core>=1.4.7` dependency requires upgrading the former P7 core pin to `1.4.7`. Versions were read from [PyPI LangGraph metadata](https://pypi.org/pypi/langgraph/json) and [core metadata](https://pypi.org/pypi/langchain-core/1.4.7/json).

## Verification

Tests use synthetic cases, local P7 policy files, fake P6 predictors, and HTTP mock transports. No external network, model artifact, LLM, or database is required by `test_agent.py`.

```powershell
# From ai-service: P8 acceptance only
py -3.14 -m uv run --python 3.12 --no-project --with-requirements requirements.txt python -m pytest tests/test_agent.py -q
# Full Python regression: P6, P7, P8
py -3.14 -m uv run --python 3.12 --no-project --with-requirements requirements.txt python -m pytest -q
```

Coverage includes all named tools, Decimal tolerance boundaries, missing evidence, conflicting evidence, invalid backend results, unavailable transport, independent rejection of fabricated source excerpts, citation metadata mismatch, classifier disagreement, unsupported summary claims, missing snapshots/artifacts, structured output, endpoint injection, and absence of resolve/escalate transport calls.

Measured targeted result: `tests/test_agent.py` passed **30 tests in 2.71s**, with one upstream Starlette/AnyIO `BlockingPortal` deprecation warning. This run exercised the real graph, injected HTTP route, failure paths, and human-review boundary without model artifacts or network. Main full regression (post-commit): **66 passed, 1 deprecation warning in 9.07s** — 30 agent, 15 classifier, 9 case-features, 11 RAG, 1 health. No accuracy, resolution success rate, or production-readiness measurement is claimed; live P4/P6/P7 integration still requires a configured backend.

## Limitations

- Deterministic scripted reasoning is the authorized deviation from an LLM investigator. No LLM is connected.
- The source API provides evidence and display references, not full transaction records. Variances use compared expected/observed evidence only. There is no direct database access or fabricated reconstruction.
- Policy corpus is synthetic. Fee and FX policy retrieval is not a commercial fee calculator or historical rate feed.
- Provenance matching detects changed/mismatched evidence and citations; it does not prove that the upstream source data itself is true.
- P4 has no analyst-feedback persistence endpoint. The 501-style response is deliberate and honest.
- No graph checkpoint persistence, durable draft store, automatic resolution, or new backend authentication layer is added in P8.
