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
