-- V4__init_transactional_outbox.sql
-- Transactional outbox table with RLS enforcement (doc 07 §3.4, §7.1, ADR-0002)

CREATE TABLE IF NOT EXISTS outbox (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),
    aggregate_type      VARCHAR(64) NOT NULL,     -- 'DECISION', 'RULESET', 'AUDIT'
    aggregate_id        VARCHAR(128) NOT NULL,
    event_type          VARCHAR(64) NOT NULL,     -- 'decision.evaluated', 'ruleset.published'
    payload             JSONB NOT NULL,           -- Avro or JSON serialized event
    headers             JSONB,                    -- Trace context, correlation IDs
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    published_at        TIMESTAMPTZ               -- NULL until successfully relayed to Kafka
);

CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed ON outbox (created_at) WHERE published_at IS NULL;

ALTER TABLE outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'outbox' AND policyname = 'tenant_isolation_outbox'
    ) THEN
        CREATE POLICY tenant_isolation_outbox ON outbox
            USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
    END IF;
END $$;
