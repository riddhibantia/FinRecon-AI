# DECISIONS - P0 placeholder

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
