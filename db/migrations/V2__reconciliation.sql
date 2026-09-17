-- FinRecon AI P1 migration V2: deterministic reconciliation records.
-- Tables: reconciliation_runs, reconciliation_results (master #11).
-- One result per (run, payment) via UNIQUE(run_id, payment_id), so reruns
-- create a new run row and stay reproducible from stored source records
-- plus rule_version (master #12).

CREATE TABLE IF NOT EXISTS reconciliation_runs (
    run_id       UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    source_set   TEXT        NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ NULL CHECK (completed_at IS NULL OR completed_at >= started_at),
    status       TEXT        NOT NULL,
    rule_version TEXT        NOT NULL,
    CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS reconciliation_results (
    result_id         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id            UUID            NOT NULL REFERENCES reconciliation_runs (run_id) ON DELETE RESTRICT,
    payment_id        UUID            NOT NULL REFERENCES payments (payment_id) ON DELETE RESTRICT,
    match_status      TEXT            NOT NULL,
    mismatch_type     TEXT            NULL,
    amount_difference NUMERIC(18, 2) NULL,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (run_id, payment_id)
);

CREATE INDEX IF NOT EXISTS idx_recon_results_run ON reconciliation_results (run_id);
CREATE INDEX IF NOT EXISTS idx_recon_results_payment ON reconciliation_results (payment_id);
CREATE INDEX IF NOT EXISTS idx_recon_results_match_status ON reconciliation_results (match_status);
