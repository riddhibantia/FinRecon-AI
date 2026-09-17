# DATABASE — P1 canonical schema

Source of DDL truth: `db/migrations/`. Source of intent: `Doc/FINRECON_MASTER.md` #11.
Java mirror of the core tables: `services/ingestion-service/.../domain/`.

## Migrations

| Version | File | Tables |
|---|---|---|
| V1 | `db/migrations/V1__core_sources.sql` | payments, ledger_entries, settlements |
| V2 | `db/migrations/V2__reconciliation.sql` | reconciliation_runs, reconciliation_results |
| V3 | `db/migrations/V3__exceptions.sql` | exceptions, exception_evidence, resolution_actions |
| V4 | `db/migrations/V4__knowledge_ai_audit.sql` | policies, policy_chunks, ai_investigations, ai_tool_calls, ai_citations, analyst_feedback, audit_logs |

Applied by Flyway at service startup (`spring.flyway.locations=filesystem:db/migrations`).
Each version runs once per database, tracked in `flyway_schema_history`.
All DDL uses `IF NOT EXISTS` guards. Extensions: `pgcrypto` (UUIDs), `vector` (P7 RAG).

## ERD (P1)

```text
payments 1───* ledger_entries
payments 1───* settlements
payments 1───* reconciliation_results *───1 reconciliation_runs
reconciliation_results 1───* exceptions
exceptions 1───* exception_evidence
exceptions 1───* resolution_actions
exceptions 1───* ai_investigations
exceptions 1───* analyst_feedback
ai_investigations 1───* ai_tool_calls
ai_investigations 1───* ai_citations
policies 1───* policy_chunks
audit_logs (standalone append-only trail)
```

Every `*` side holds a `NOT NULL` FK with `ON DELETE RESTRICT`: financial
facts are never cascade-deleted. Retention deletes are a P4/P11 decision.

## Tables

`payments` — gateway record. PK `payment_id UUID DEFAULT gen_random_uuid()`.
`external_txn_id TEXT UNIQUE NOT NULL` (stable P3 match key), `customer_id`,
`merchant_id`, `amount NUMERIC(18,2) >= 0`, `currency CHAR(3)` ISO check,
`status TEXT`, `event_time TIMESTAMPTZ`, `created_at` default now().
Index on `(merchant_id, event_time)` for the P3 fallback key.

`ledger_entries` — ledger record. PK `ledger_entry_id`, FK `payment_id`,
`gross_amount` / `fee_amount` / `net_amount NUMERIC(18,2) >= 0`,
`currency`, `posting_status`, `posted_at`, `created_at`. Index on `payment_id`.

`settlements` — settlement record. PK `settlement_id`, FK `payment_id`,
`settled_amount` / `fee_amount`, `currency`, `settlement_status`,
`settlement_date DATE`, `batch_id`. Indexes on `payment_id`, `batch_id`,
`settlement_date`. Deliberately no uniqueness on `payment_id` so P3 can
observe duplicate and partial settlements.

`reconciliation_runs` — one row per deterministic run. `source_set`,
`started_at`, `completed_at >= started_at`, `status IN (STARTED, COMPLETED,
FAILED)`, `rule_version` (reproducibility per master #12).

`reconciliation_results` — one row per `(run_id, payment_id)` (UNIQUE).
FKs to runs and payments. `match_status`, nullable `mismatch_type`,
`amount_difference NUMERIC(18,2)`. Indexes on `run_id`, `payment_id`,
`match_status`.

`exceptions` — FK `result_id`. `category` CHECK over the 9 taxonomy values
(master #4). `status` CHECK over `OPEN, INVESTIGATING, RESOLVED, ESCALATED`
(master #2.3). `severity TEXT` (values not specified yet — P4). `assigned_to`
nullable, `created_at`, `resolved_at >= created_at`. Indexes on `result_id`,
`status`, `category`, `assigned_to`. No dedup constraint on `result_id`:
case assignment is P4 logic.

`exception_evidence` — FK `exception_id`. `source_type`, `source_record_id`,
nullable `field_name`, `expected_value`, `observed_value` (all TEXT so any
source field fits, master FR-08).

`resolution_actions` — FK `exception_id`. `action_type`, `actor_type`,
`actor_id`, nullable `notes`, `created_at`.

`policies` — `title`, `version`, `UNIQUE(title, version)`, `effective_from`,
`effective_to >= effective_from`, `source_uri`. Index via the unique pair.

`policy_chunks` — FK `policy_id`. `chunk_text`, `metadata JSONB default
'{}'`, `embedding vector(1536)` NULL until P7. Index on `policy_id`;
vector index arrives with P7 retrieval.

`ai_investigations` — FK `exception_id`. `model_version`, `prompt_version`
(versioning per master #19), `status`, nullable `summary`,
`confidence NUMERIC(5,4)` in [0,1].

`ai_tool_calls` — FK `investigation_id`. `tool_name`, `arguments JSONB`,
`result_hash`, `latency_ms >= 0`.

`ai_citations` — FK `investigation_id`. `source_type`, `source_id`,
nullable `location`, `quote_or_excerpt`.

`analyst_feedback` — FK `exception_id`. `analyst_id`, nullable
`original_value`, `corrected_value`, `reason`, `created_at`.

`audit_logs` — append-only. `actor_type`, `actor_id`, `action`,
`entity_type`, `entity_id`, `timestamp` default now(), `metadata JSONB`.
Indexes on `(entity_type, entity_id)`, `timestamp`, `actor_id`.

## Conventions

- PKs: `UUID DEFAULT gen_random_uuid()` everywhere.
- Money: `NUMERIC(18, 2)` with `>= 0` checks. Currency: `CHAR(3)` with
  `^[A-Z]{3}$` check. Timestamps: `TIMESTAMPTZ`, `created_at` defaults now().
- Status CHECKs exist only where the master fixes the values (exception
  category, exception status, run status). Source-record statuses stay open
  TEXT so P1 invents no workflow rules.
- Java entities (`Payment`, `LedgerEntry`, `Settlement` + Spring Data
  repositories) mirror V1 only. V2–V4 get their Java layer with P3/P4/P7.
  Runtime JPA uses `ddl-auto=validate`: migrations own the schema.

## Seed

`db/seed/p1_minimal_seed.sql` stores the master #3 example: payment
`TXN-P1-0001` INR 10,000 SUCCESS, ledger gross 10,000 / fee 250 / net 9,750
POSTED, settlement 9,750 SETTLED in `BATCH-P1-001`. Fixed UUIDs,
`WHERE NOT EXISTS` guards. Apply with
`psql "$DATABASE_URL" -f db/seed/p1_minimal_seed.sql`.

## Validation

- `db/tests/test_migrations.py` (11 tests): version order, all 15 tables,
  UUID PKs, gateway→ledger→settlement traceability, duplicate-friendly
  settlements, money/currency/status columns, taxonomy values, FK indexes,
  guarded DDL, seed chain, Java↔SQL table agreement.
- `DomainModelTest` (3 tests): Hibernate mapping model without a database.
- `HealthTest` on H2 (`create-drop`, Flyway off): entity wiring in context.
- Flyway-against-Postgres is proven where Docker exists
  (`docker compose up postgres`, then start ingestion-service). No Docker or
  Postgres is installed on this machine, so that step is deferred and
  reported, not simulated.

## P2/P3 compatibility

P2 writes through the same tables (unique `external_txn_id` gives the
idempotency anchor). P3 reads all three source tables by `payment_id`,
writes runs/results, and relies on duplicates being observable.
