-- Migration V5: Create audit_management_events table per Document 07
CREATE TABLE IF NOT EXISTS audit_management_events (
    event_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),
    actor_id            VARCHAR(128) NOT NULL,
    actor_role          VARCHAR(64) NOT NULL,
    client_ip           INET,
    event_type          VARCHAR(64) NOT NULL,
    target_entity_type  VARCHAR(64) NOT NULL,
    target_entity_id    VARCHAR(128) NOT NULL,
    diff_payload        JSONB,
    previous_hash       CHAR(64),
    chain_hash          CHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_audit_mgmt_tenant_created ON audit_management_events (tenant_id, created_at DESC);

ALTER TABLE audit_management_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_management_events FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'public' 
          AND tablename = 'audit_management_events' 
          AND policyname = 'tenant_isolation_audit_mgmt'
    ) THEN
        CREATE POLICY tenant_isolation_audit_mgmt ON audit_management_events
            USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
    END IF;
END $$;

