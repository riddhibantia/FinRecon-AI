-- FinRecon AI P1 migration V4: knowledge base, AI investigators, feedback, audit.
-- Tables: policies, policy_chunks, ai_investigations, ai_tool_calls,
-- ai_citations, analyst_feedback, audit_logs (master #11).
-- The vector extension serves P7 RAG; the embedding column stays NULL
-- until P7 writes vectors. Audit primitives land here for P4/P11 use.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS policies (
    policy_id      UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    title          TEXT    NOT NULL,
    version        TEXT    NOT NULL,
    effective_from DATE    NOT NULL,
    effective_to   DATE    NULL CHECK (effective_to IS NULL OR effective_to >= effective_from),
    source_uri     TEXT    NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (title, version)
);

CREATE TABLE IF NOT EXISTS policy_chunks (
    chunk_id   UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id  UUID    NOT NULL REFERENCES policies (policy_id) ON DELETE RESTRICT,
    chunk_text TEXT    NOT NULL,
    metadata   JSONB   NOT NULL DEFAULT '{}',
    embedding  vector(1536) NULL
);

CREATE TABLE IF NOT EXISTS ai_investigations (
    investigation_id UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    exception_id     UUID            NOT NULL REFERENCES exceptions (exception_id) ON DELETE RESTRICT,
    model_version    TEXT            NOT NULL,
    prompt_version   TEXT            NOT NULL,
    status           TEXT            NOT NULL,
    summary          TEXT            NULL,
    confidence       NUMERIC(5, 4)   NULL CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ai_tool_calls (
    tool_call_id     UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id UUID    NOT NULL REFERENCES ai_investigations (investigation_id) ON DELETE RESTRICT,
    tool_name        TEXT    NOT NULL,
    arguments        JSONB   NOT NULL DEFAULT '{}',
    result_hash      TEXT    NULL,
    latency_ms       INTEGER NULL CHECK (latency_ms IS NULL OR latency_ms >= 0)
);

CREATE TABLE IF NOT EXISTS ai_citations (
    citation_id      UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id UUID    NOT NULL REFERENCES ai_investigations (investigation_id) ON DELETE RESTRICT,
    source_type      TEXT    NOT NULL,
    source_id        TEXT    NOT NULL,
    location         TEXT    NULL,
    quote_or_excerpt TEXT    NULL
);

CREATE TABLE IF NOT EXISTS analyst_feedback (
    feedback_id    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    exception_id   UUID        NOT NULL REFERENCES exceptions (exception_id) ON DELETE RESTRICT,
    analyst_id     TEXT        NOT NULL,
    original_value TEXT        NULL,
    corrected_value TEXT       NULL,
    reason         TEXT        NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_type  TEXT        NOT NULL,
    actor_id    TEXT        NOT NULL,
    action      TEXT        NOT NULL,
    entity_type TEXT        NOT NULL,
    entity_id   TEXT        NOT NULL,
    timestamp   TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata    JSONB       NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_policy_chunks_policy ON policy_chunks (policy_id);
CREATE INDEX IF NOT EXISTS idx_investigations_exception ON ai_investigations (exception_id);
CREATE INDEX IF NOT EXISTS idx_tool_calls_investigation ON ai_tool_calls (investigation_id);
CREATE INDEX IF NOT EXISTS idx_citations_investigation ON ai_citations (investigation_id);
CREATE INDEX IF NOT EXISTS idx_feedback_exception ON analyst_feedback (exception_id);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_logs (timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_logs (actor_id);
