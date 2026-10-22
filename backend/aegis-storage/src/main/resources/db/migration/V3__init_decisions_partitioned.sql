-- V3__init_decisions_partitioned.sql
-- Monthly partitioned decisions table with RLS enforcement (doc 07 §3.3, §3.4, §7.1)

CREATE TABLE IF NOT EXISTS decisions (
    decision_id             UUID NOT NULL,
    tenant_id               UUID NOT NULL REFERENCES tenants(tenant_id),
    event_id                VARCHAR(128) NOT NULL,
    event_time              TIMESTAMPTZ NOT NULL,
    sequence_number         BIGINT NOT NULL DEFAULT 0,
    entity_id_hash          VARCHAR(128) NOT NULL,   -- Tenant-keyed hash of account_id/entity_id
    outcome                 VARCHAR(16) NOT NULL,    -- APPROVE, DECLINE, REVIEW
    score                   NUMERIC(5, 2) NOT NULL,  -- Clamped 0.00 to 100.00
    hard_rule_fired         BOOLEAN NOT NULL DEFAULT FALSE,
    ruleset_id              VARCHAR(64) NOT NULL,
    ruleset_version_hash    CHAR(64) NOT NULL,
    rules_fired             JSONB NOT NULL,          -- Array: [{rule_id, name, score_impact, reason_code}]
    features_snapshot       JSONB NOT NULL,          -- Velocity & entity state evaluated
    degraded                BOOLEAN NOT NULL DEFAULT FALSE,
    degraded_reason         VARCHAR(256),
    latency_ms              NUMERIC(6, 2) NOT NULL,
    sealed                  BOOLEAN NOT NULL DEFAULT FALSE, -- ADR-0004
    batch_id                UUID,                           -- FK to decision_audit_batches
    created_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (tenant_id, created_at, decision_id)
) PARTITION BY RANGE (created_at);

CREATE INDEX IF NOT EXISTS idx_decisions_tenant_event ON decisions (tenant_id, event_id);
CREATE INDEX IF NOT EXISTS idx_decisions_tenant_entity ON decisions (tenant_id, entity_id_hash, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_decisions_tenant_unsealed ON decisions (tenant_id, created_at) WHERE sealed = FALSE;
CREATE INDEX IF NOT EXISTS idx_decisions_ruleset_hash ON decisions (tenant_id, ruleset_version_hash);

-- Pre-provisioned monthly partitions for Q4 2026 and default partition fallback
CREATE TABLE IF NOT EXISTS decisions_y2026m10 PARTITION OF decisions
    FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS decisions_y2026m11 PARTITION OF decisions
    FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS decisions_y2026m12 PARTITION OF decisions
    FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS decisions_default PARTITION OF decisions DEFAULT;

ALTER TABLE decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE decisions FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'decisions' AND policyname = 'tenant_isolation_decisions'
    ) THEN
        CREATE POLICY tenant_isolation_decisions ON decisions
            USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
    END IF;
END $$;
