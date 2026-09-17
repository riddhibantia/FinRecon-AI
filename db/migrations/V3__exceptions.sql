-- FinRecon AI P1 migration V3: exception, evidence, and resolution records.
-- Tables: exceptions, exception_evidence, resolution_actions (master #11).
-- Category values come from the exception taxonomy (master #4).
-- Status values come from the case lifecycle (master #2.3).
-- No dedup constraint on result_id: assigning and de-duplicating cases
-- is P4 logic; the schema must not silently drop rows.

CREATE TABLE IF NOT EXISTS exceptions (
    exception_id UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    result_id    UUID        NOT NULL REFERENCES reconciliation_results (result_id) ON DELETE RESTRICT,
    category     TEXT        NOT NULL CHECK (category IN (
        'MISSING_SETTLEMENT', 'AMOUNT_MISMATCH', 'FEE_VARIANCE',
        'FX_VARIANCE', 'DUPLICATE_SETTLEMENT', 'PARTIAL_SETTLEMENT',
        'STATUS_MISMATCH', 'LATE_SETTLEMENT', 'UNKNOWN_EXCEPTION'
    )),
    severity     TEXT        NOT NULL,
    status       TEXT        NOT NULL CHECK (status IN (
        'OPEN', 'INVESTIGATING', 'RESOLVED', 'ESCALATED'
    )),
    assigned_to  TEXT        NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ NULL CHECK (resolved_at IS NULL OR resolved_at >= created_at)
);

-- Exact comparison values behind a case (master FR-08). Values stay TEXT
-- so any source field can be recorded without type coercion.
CREATE TABLE IF NOT EXISTS exception_evidence (
    evidence_id      UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    exception_id     UUID    NOT NULL REFERENCES exceptions (exception_id) ON DELETE RESTRICT,
    source_type      TEXT    NOT NULL,
    source_record_id TEXT    NOT NULL,
    field_name       TEXT    NULL,
    expected_value   TEXT    NULL,
    observed_value   TEXT    NULL
);

CREATE TABLE IF NOT EXISTS resolution_actions (
    action_id    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    exception_id UUID        NOT NULL REFERENCES exceptions (exception_id) ON DELETE RESTRICT,
    action_type  TEXT        NOT NULL,
    actor_type   TEXT        NOT NULL,
    actor_id     TEXT        NOT NULL,
    notes        TEXT        NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_exceptions_result ON exceptions (result_id);
CREATE INDEX IF NOT EXISTS idx_exceptions_status ON exceptions (status);
CREATE INDEX IF NOT EXISTS idx_exceptions_category ON exceptions (category);
CREATE INDEX IF NOT EXISTS idx_exceptions_assigned ON exceptions (assigned_to);
CREATE INDEX IF NOT EXISTS idx_evidence_exception ON exception_evidence (exception_id);
CREATE INDEX IF NOT EXISTS idx_resolution_exception ON resolution_actions (exception_id);
