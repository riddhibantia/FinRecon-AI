-- FinRecon AI P1 migration V1: core source records.
-- Tables: payments, ledger_entries, settlements (master #11).
-- Deterministic: fixed DDL, IF NOT EXISTS guards, rerunnable via Flyway
-- (each version applies once, tracked in flyway_schema_history).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Payment Gateway record. One row per gateway transaction.
-- external_txn_id is the stable transaction identifier used for exact
-- reference matching in P3 (master #12).
CREATE TABLE IF NOT EXISTS payments (
    payment_id      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    external_txn_id TEXT            NOT NULL UNIQUE,
    customer_id     TEXT            NOT NULL,
    merchant_id     TEXT            NOT NULL,
    amount          NUMERIC(18, 2)  NOT NULL CHECK (amount >= 0),
    currency        CHAR(3)         NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status          TEXT            NOT NULL,
    event_time      TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Internal Ledger record. Traces to payments via payment_id.
CREATE TABLE IF NOT EXISTS ledger_entries (
    ledger_entry_id UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      UUID            NOT NULL REFERENCES payments (payment_id) ON DELETE RESTRICT,
    gross_amount    NUMERIC(18, 2)  NOT NULL CHECK (gross_amount >= 0),
    fee_amount      NUMERIC(18, 2)  NOT NULL CHECK (fee_amount >= 0),
    net_amount      NUMERIC(18, 2)  NOT NULL CHECK (net_amount >= 0),
    currency        CHAR(3)         NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    posting_status  TEXT            NOT NULL,
    posted_at       TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Settlement System record. Traces to payments via payment_id.
-- No uniqueness on payment_id: P3 must be able to observe duplicate and
-- partial settlements, so the schema allows several rows per payment.
CREATE TABLE IF NOT EXISTS settlements (
    settlement_id     UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id        UUID            NOT NULL REFERENCES payments (payment_id) ON DELETE RESTRICT,
    settled_amount    NUMERIC(18, 2)  NOT NULL CHECK (settled_amount >= 0),
    fee_amount        NUMERIC(18, 2)  NOT NULL CHECK (fee_amount >= 0),
    currency          CHAR(3)         NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    settlement_status TEXT            NOT NULL,
    settlement_date   DATE            NOT NULL,
    batch_id          TEXT            NOT NULL,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_payments_merchant_time ON payments (merchant_id, event_time);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_payment ON ledger_entries (payment_id);
CREATE INDEX IF NOT EXISTS idx_settlements_payment ON settlements (payment_id);
CREATE INDEX IF NOT EXISTS idx_settlements_batch ON settlements (batch_id);
CREATE INDEX IF NOT EXISTS idx_settlements_date ON settlements (settlement_date);
