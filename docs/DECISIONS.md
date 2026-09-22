# DECISIONS

ADRs to maintain per `Doc/FINRECON_MASTER.md` #28:

- ADR-001 PostgreSQL as source of truth (P1)
- ADR-002 Kafka introduction point (P5)
- ADR-003 Deterministic reconciliation before ML/LLM (P3)
- ADR-004 PostgreSQL + pgvector for RAG (P7)
- ADR-005 LangGraph workflow choice (P8)
- ADR-006 Fine-tune classifier vs main LLM (P9)
- ADR-007 Facts that must come from tools/database (P8)
- ADR-008 Rule/model/prompt versioning (P3+)

P0 decision log:

- Build tool: Gradle 8.9 (available locally) instead of Maven (not installed). Same Java 21 + Spring Boot 3.2.5 runtime. Maven can be added in P11 without changing code.
- Compose runs PostgreSQL only. Kafka and Redis are deferred to P5 per spec.
- Python 3.10 used locally; CI pins 3.11 per spec. Code is version-agnostic for P0.

P1 decision log:

- Flyway (via Spring Boot) applies `db/migrations` at startup. No new
  runtime tech: Spring Data JPA/JDBC is approved Use-Now stack.
- One shared schema for all services in P1; Java entities live with their
  owning service. Only V1 tables get entities now (P1 goal); V2-V4 get
  theirs with P3/P4/P7.
- Flyway loads `filesystem:db/migrations` so the SQL has one canonical
  home; ingestion-service tests run with `workingDir = rootDir`.
- `ddl-auto=validate` at runtime: migrations own the schema, Hibernate
  never alters it. Tests use H2 `create-drop` with Flyway off, so H2 never
  validates the PostgreSQL DDL (see DATABASE.md).
- `ON DELETE RESTRICT` on all FKs; retention deletes deferred to P4/P11.
- No uniqueness on `settlements.payment_id` so P3 duplicate/partial
  detection can observe multiple rows. `UNIQUE(run_id, payment_id)` keeps
  reruns reproducible.
- CHECKs only where the master fixes values (taxonomy, case lifecycle,
  run status, currency shape, non-negative money). Source statuses stay
  open TEXT; P3/P4 may constrain them.

P3 decision log:

- `match_status` is MATCHED/MISMATCHED and `mismatch_type` reuses the
  master taxonomy vocabulary so P4 can map results to cases 1:1.
- Fixed first-failure check order (duplicate, missing, amount, unknown,
  fee, net/partial, FX, status, late). Same input always yields the same
  outcome; reruns write a new run row with identical result content.
- `RULE_VERSION = 1.0.0` is recorded on every run for traceability.
- P3-defined parameters (no store exists in P1): settlement window of
  2 days for LATE_SETTLEMENT; 24h window inside the constrained fallback
  matcher (merchant + amount + currency + time, never fuzzy).
- Fee check compares observed ledger fee vs observed settlement fee; the
  rule-based expected-fee check waits for fee schedules (P7+).
- Status check is normalized equality across the three systems; a shared
  lifecycle mapping is deferred. Multiple ledger rows for one payment map
  to UNKNOWN_EXCEPTION (ambiguous books, needs a human).
- Fallback matcher is specified, constrained, and unit-tested; the live
  pipeline resolves exactly via FK linkage, so fallback is a documented
  extension point for reference-less feeds, not on the hot path.
- Reconciliation duplicates the three source-table mappings as read-only
  views; ingestion remains the sole writer (bounded-context views, no
  shared module, no P0 boundary change).

P4 decision log:

- Entity `ReconException` maps table `exceptions`; `Exception` alone would
  shadow `java.lang.Exception`.
- Case category copies the result `mismatch_type` 1:1 (shared taxonomy
  vocabulary). Severity is a documented placeholder: |amountDifference| > 0
  -> HIGH else MEDIUM — the master fixes no severity values.
- Lifecycle enforced in the entity (OPEN -> INVESTIGATING -> RESOLVED;
  OPEN/INVESTIGATING -> ESCALATED; terminal states immutable). Every
  transition writes a resolution_actions row and an audit_logs row.
- Evidence rows quote stored values only (expected vs observed per
  category); absence is recorded as "absent", never invented.
- `audit_logs.metadata` uses Hibernate's built-in JSON type code (no new
  library) so Postgres jsonb and H2 JSON both accept it.
- Sync is idempotent: results that already have cases are skipped and
  counted; only MISMATCHED results open cases.

P5 decision log:

- One canonical topic (`finrecon.ingest.v1`, keyed by external_txn_id);
  the consumer replays through the unchanged P2 store path, so async
  reproduces sync with no duplicate effects (eventId dedupe + store
  idempotency, two independent guards).
- All messaging beans are conditional and OFF by default: no broker, no
  connection attempts, earlier phases boot untouched. Enable with
  `finrecon.messaging.enabled` / `finrecon.redis.enabled`.
- Publishing is best-effort (logged, never thrown): a broker outage must
  not fail synchronous ingestion.
- Validation rejections ack without retry; unexpected failures retry 3x
  with backoff then DLT. No new tables: dedupe lives in Redis (TTL) or a
  bounded in-memory map, never in Postgres.
- Compose adds single-node Kafka (KRaft) + Redis for local runs; managed
  equivalents stay a cloud-later concern. No Kubernetes/AWS in P5.

P10 decision log:

- No new dependencies: native fetch, App Router server components, plain
  CSS. No UI library (not needed for this workflow).
- Same-origin /api/* proxies with an explicit per-route target map (no
  generic forwarder); the closed CASE_ACTIONS set is unit-tested so the UI
  cannot invent P4 transitions.
- Server components fetch backends directly; only forms/islands are client
  components. Correlation IDs pass through the proxy (generated if absent).
- AI panel uses POST /investigate only. /classify is deliberately not
  exposed: the UI cannot build validated P6 snapshots from case display
  data, and fabricating them is forbidden.
- Unreachable backends render labeled error states; the 502 proxy path and
  the stopped-backend pages were verified against a live dev server.

P11 decision log:

- CorrelationFilter (OncePerRequestFilter + MDC) on the three real
  services; gateway/reporting skeletons excluded until they serve traffic.
- CORS allowlist is one dashboard origin with GET/POST on /api/** only;
  proven by live preflight tests, not registry unit tests (the registry
  accessor is protected and the live test is stronger anyway).
- No auth system invented: master names roles but specifies no mechanism,
  so P11 pins boundaries (CORS, proxy allowlist, read-only AI surface,
  hygiene scan) and defers identity/RBAC to gateway deployment.
- AI write-guard is a route-shape test, not a review checklist: any future
  PUT/DELETE/PATCH or extra POST on ai-service fails the build.

P12 decision log:

- Seven Dockerfiles (5 Java, ai-service, frontend); gateway/reporting
  included despite skeleton status so every module builds uniformly.
  Flyway services COPY db/migrations (filesystem location resolves at
  WORKDIR). No local Docker daemon: images are reviewed here and built by
  the CI docker job, never claimed working otherwise.
- Compose runs the working system (infra + 3 Java services + ai +
  frontend); gateway/reporting join when they serve traffic.
- Structured logs via logback-spring.xml per real service with
  %X{requestId}; no payloads by construction. Counters only (no timers
  yet): ingest accepted/rejected, recon runs/results, cases
  opened/transitions. Probes: liveness + readiness(db,diskSpace).
- CI mirrors local gates 1:1 (gradle, uv/3.12 ai suite, repo pytest,
  npm test+build) plus compose config and image builds.
- P12 debugging note: test application.properties shadows main wholesale
  (same classpath location), so probe flags repeat in test resources;
  also fixed a self-matching hygiene regex and a duplicated properties
  tail, both caught by tests before commit.

Live-Docker decision log (machine with Docker, first real deployment):

- Kafka (apache/kafka:3.8.0 KRaft) needed three compose fixes: listener
  security protocol map, controller listener names, inter-broker listener
  name; healthcheck uses /opt/kafka/bin/kafka-broker-api-versions.sh
  (no 'cub' in this image); single-node requires offsets/txn RF=1 or the
  group coordinator never comes up (found via FIND_COORDINATOR timeout).
- Dual listeners: INTERNAL kafka:29092 for containers, EXTERNAL
  localhost:9092 for host tools; single-advertise breaks one side.
- Java images build FROM gradle:8.9-jdk21 (temurin-jdk has no gradle).
- V5 currency CHAR(3)->VARCHAR(3): live Postgres boot proved bpchar
  fails Hibernate validation; mapping-only workarounds
  (columnDefinition, JdbcTypeCode CHAR) do not satisfy the validator.
  H2 never catches this class of mismatch.
- Rebuilds must be verified by image age + migration log lines: an
  'up -d --build' with empty output left stale images running twice.
  Trust 'Migrating schema to version N', not the exit code.

P13 decision log (2026-09-22):
- Coverage gates: JaCoCo XML/HTML per Gradle build; ai-service line coverage 87% via pytest-cov (dev-only, never in the image).
- Dependency security gate: pip-audit + npm audit (high) in CI. Remediated: fastapi 0.141.1/starlette 1.6.0, aiohttp 3.14.3, anyio 4.14.2, cryptography 50.0.1, h2 4.4.1, pygments 2.20.0, requests 2.33.0, urllib3 2.7.0, pytest 9.0.3; ecdsa/python-jose removed (unused). Frontend: Next.js 14.2.5 to 16.3.5 + React 19 (only fix line for the 2026 Next advisory set; params/searchParams are now awaited Promises; lint script moved from removed `next lint` to direct `eslint`). torch (+cpu local wheel) is not auditable on PyPI - noted, local dev only.
- OWASP Java dependency scan deferred: the plugin needs an NVD API key; revisit when a key is provisioned.

P13 monolith decision log (2026-09-22, 8GB footprint):

- Consolidated the five Spring services into `services/finrecon-app`
  (one Spring Boot app on :8080, ~600 MB vs ~2.5 GB). Packages
  `ingestion/`, `reconciliation/`, `exceptioncase/`, `reporting/`,
  `shared/` preserve the bounded contexts; `settings.gradle` includes
  only the monolith and CI builds three images (app, ai, frontend).
- Broker-less by default: P5 messaging/Redis code removed in
  consolidation; `docker-compose.yml` runs PostgreSQL only and apps run
  as local processes. `data/evaluation/p13_kafka.json` is kept as a
  historical pre-monolith measurement, marked as such in
  `docs/METRICS.md` (ADR-002).
- Live performance/demo not re-measured in this session (no
  PostgreSQL/Docker in this environment): P95, throughput, and match
  rate are recorded as not measured with the exact rerun commands;
  nothing is estimated (`scripts/measure_performance.py` targets the
  monolith :8080).
- Completed the P13 doc set: extracted PRD/TRD/ARCHITECTURE from the
  master, eight `docs/adr/` files, `docs/METRICS.md`, README retitle,
  OPERATIONS refresh. Unrelated `DESIGN.md` / `design-mockups.html`
  (Nike commerce) stay untracked and out of git.

