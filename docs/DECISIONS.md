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
