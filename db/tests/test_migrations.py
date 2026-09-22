"""P1 validation for versioned PostgreSQL migrations and seed data.

File-level checks only: they prove the DDL is versioned, deterministic,
and matches FINRECON_MASTER.md #11 without needing a live PostgreSQL
server. Runtime migration runs (Flyway against real Postgres) are a
separate step documented in docs/DATABASE.md.
"""

import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
MIGRATIONS = REPO / "db" / "migrations"
SEED = REPO / "db" / "seed" / "p1_minimal_seed.sql"

EXPECTED_FILES = [
    "V1__core_sources.sql",
    "V2__reconciliation.sql",
    "V3__exceptions.sql",
    "V4__knowledge_ai_audit.sql",
    "V5__currency_varchar.sql",
]

# Every canonical entity from FINRECON_MASTER.md #11 must exist.
EXPECTED_TABLES = [
    "payments",
    "ledger_entries",
    "settlements",
    "reconciliation_runs",
    "reconciliation_results",
    "exceptions",
    "exception_evidence",
    "resolution_actions",
    "policies",
    "policy_chunks",
    "ai_investigations",
    "ai_tool_calls",
    "ai_citations",
    "analyst_feedback",
    "audit_logs",
]


def _sql() -> dict:
    return {p.name: p.read_text(encoding="utf-8") for p in MIGRATIONS.glob("V*.sql")}


def test_migration_versions_are_ordered_and_complete():
    names = sorted(p.name for p in MIGRATIONS.glob("V*.sql"))
    assert names == EXPECTED_FILES


def test_all_canonical_tables_exist():
    bodies = " ".join(_sql().values())
    for table in EXPECTED_TABLES:
        assert re.search(
            rf"CREATE TABLE IF NOT EXISTS {table}\s*\(", bodies
        ), f"missing table {table}"


def test_every_table_has_uuid_primary_key():
    for name, body in _sql().items():
        for match in re.finditer(
            r"CREATE TABLE IF NOT EXISTS (\w+)\s*\((.*?)\n\);", body, re.DOTALL
        ):
            table, ddl = match.group(1), match.group(2)
            assert re.search(r"PRIMARY KEY", ddl), f"{name}: {table} has no PK"
            assert re.search(r"UUID", ddl), f"{name}: {table} PK is not UUID"


def test_source_records_trace_to_payments():
    v1 = _sql()["V1__core_sources.sql"]
    assert "REFERENCES payments (payment_id)" in v1
    assert v1.count("REFERENCES payments (payment_id)") == 2  # ledger + settlement


def test_settlements_allow_multiple_rows_per_payment():
    v1 = _sql()["V1__core_sources.sql"]
    block = re.search(
        r"CREATE TABLE IF NOT EXISTS settlements\s*\((.*?)\n\);", v1, re.DOTALL
    ).group(1)
    assert "UNIQUE" not in block  # P3 duplicate detection needs visible dupes


def test_gateway_reference_is_unique():
    v1 = _sql()["V1__core_sources.sql"]
    assert re.search(r"external_txn_id\s+TEXT\s+NOT NULL UNIQUE", v1)


def test_money_currency_and_status_columns():
    bodies = " ".join(_sql().values())
    assert "NUMERIC(18, 2)" in bodies
    assert "CHAR(3)" in bodies
    assert "currency ~ '^[A-Z]{3}$'" in bodies
    assert bodies.count("CHECK (") >= 10


def test_exception_taxonomy_and_lifecycle_match_master():
    v3 = _sql()["V3__exceptions.sql"]
    for category in [
        "MISSING_SETTLEMENT", "AMOUNT_MISMATCH", "FEE_VARIANCE",
        "FX_VARIANCE", "DUPLICATE_SETTLEMENT", "PARTIAL_SETTLEMENT",
        "STATUS_MISMATCH", "LATE_SETTLEMENT", "UNKNOWN_EXCEPTION",
    ]:
        assert category in v3, f"missing category {category}"
    for status in ["OPEN", "INVESTIGATING", "RESOLVED", "ESCALATED"]:
        assert f"'{status}'" in v3, f"missing status {status}"


def test_indexes_cover_foreign_keys():
    bodies = " ".join(_sql().values())
    for column in ["payment_id", "exception_id", "investigation_id", "batch_id"]:
        assert re.search(
            rf"CREATE INDEX IF NOT EXISTS \w+ ON \w+ \([^\n]*{column}", bodies
        ), f"no index covering {column}"


def test_ddl_is_guarded_and_rerunnable():
    for name, body in _sql().items():
        bare_tables = [
            line for line in body.splitlines()
            if re.match(r"\s*CREATE TABLE\s+\w+", line)
            and "IF NOT EXISTS" not in line
        ]
        assert not bare_tables, f"{name}: unguarded DDL {bare_tables}"
        bare_alters = [
            line for line in body.splitlines()
            if re.match(r"\s*ALTER TABLE\s+", line)
            and "IF EXISTS" not in line
        ]
        assert not bare_alters, f"{name}: unguarded ALTER {bare_alters}"
        creates = [line for line in body.splitlines()
                   if re.match(r"\s*CREATE (TABLE|INDEX)", line)]
        if creates:
            assert "CREATE INDEX IF NOT EXISTS" in body, f"{name}: unguarded index"


def test_v5_alters_only_currency_to_varchar():
    v5 = _sql()["V5__currency_varchar.sql"]
    assert "CREATE TABLE" not in v5  # additive alter, no new tables
    assert v5.count("ALTER TABLE IF EXISTS") == 3  # payments + ledger + settlement
    assert "TYPE VARCHAR(3)" in v5
    assert "currency" in v5


def test_seed_stores_three_linked_source_records():
    lines = [
        line for line in SEED.read_text(encoding="utf-8").splitlines()
        if not line.lstrip().startswith("--")
    ]
    seed = "\n".join(lines)
    assert "TXN-P1-0001" in seed
    payment_id = "11111111-1111-4111-8111-111111111111"
    assert seed.count(payment_id) == 3  # payment + ledger link + settlement link
    assert "10000.00" in seed and "9750.00" in seed and "250.00" in seed
    assert seed.count("WHERE NOT EXISTS") == 3  # safe to rerun


def test_java_entities_match_migration_tables():
    domain = REPO / "services" / "finrecon-app" / "src" / "main" / "java"
    mapping = {
        "Payment.java": "payments",
        "LedgerEntry.java": "ledger_entries",
        "Settlement.java": "settlements",
    }
    v1 = _sql()["V1__core_sources.sql"]
    for java_file, table in mapping.items():
        source = next(domain.rglob(java_file)).read_text(encoding="utf-8")
        assert f'@Table(name = "{table}")' in source, java_file
        assert re.search(
            rf"CREATE TABLE IF NOT EXISTS {table}\s*\(", v1
        ), table
