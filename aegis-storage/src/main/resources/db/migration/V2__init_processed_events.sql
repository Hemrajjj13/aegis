-- V2__init_processed_events.sql
-- Physical deduplication table with RLS enforcement (doc 07 §3.2, §7.1)

CREATE TABLE IF NOT EXISTS processed_events (
    tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),
    event_id            VARCHAR(128) NOT NULL,
    decision_id         UUID NOT NULL,
    event_time          TIMESTAMPTZ NOT NULL,
    processed_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    payload_hash        CHAR(64) NOT NULL, -- SHA-256 of canonical input JSON
    PRIMARY KEY (tenant_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_cleanup ON processed_events (processed_at);

ALTER TABLE processed_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE processed_events FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'processed_events' AND policyname = 'tenant_isolation_processed_events'
    ) THEN
        CREATE POLICY tenant_isolation_processed_events ON processed_events
            USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
    END IF;
END $$;
