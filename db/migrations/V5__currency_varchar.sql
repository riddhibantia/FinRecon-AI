-- FinRecon AI migration V5: currency columns CHAR(3) -> VARCHAR(3).
--
-- Why: PostgreSQL implements CHAR(3) as bpchar (blank-padded), while the
-- JPA mapping resolves Java String to VARCHAR and Hibernate schema
-- validation compares the two strictly. The V1 CHECK constraint
-- (currency ~ '^[A-Z]{3}$') already guarantees the 3-letter shape, so for
-- real values bpchar and varchar(3) are identical; VARCHAR(3) simply
-- matches the ORM mapping. V1-V4 are untouched; existing databases
-- migrate in place (no truncation: stored values satisfy the CHECK).
-- Validated live: H2 (create-drop) never caught this; PostgreSQL did.

ALTER TABLE IF EXISTS payments
    ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE IF EXISTS ledger_entries
    ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE IF EXISTS settlements
    ALTER COLUMN currency TYPE VARCHAR(3);
