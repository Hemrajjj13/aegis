-- V1__init_tenants_and_roles.sql
-- Database roles and tenants table specification (doc 07 §2, §3.1)

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_live_user') THEN
        CREATE ROLE app_live_user;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_replay_user') THEN
        CREATE ROLE app_replay_user;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_sealer_user') THEN
        CREATE ROLE app_sealer_user;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_outbox_relay') THEN
        CREATE ROLE app_outbox_relay;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_console_user') THEN
        CREATE ROLE app_console_user;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS tenants (
    tenant_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                VARCHAR(64) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, TERMINATED
    fail_policy         VARCHAR(16) NOT NULL,                  -- FAIL_OPEN, FAIL_CLOSED (doc 01 §4)
    kms_key_arn         VARCHAR(256),                          -- [interview-driven] Tenant-dedicated KMS key ARN
    sealer_interval_sec INT NOT NULL DEFAULT 30,               -- ADR-0004
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_tenants_slug ON tenants(slug);

-- Seed default Tier 0 development tenant organizations (doc 21 §5)
INSERT INTO tenants (tenant_id, slug, name, status, fail_policy, sealer_interval_sec)
VALUES 
    ('00000000-0000-0000-0000-000000000001', 'acme_pay', 'Acme Payments India', 'ACTIVE', 'FAIL_OPEN', 30),
    ('00000000-0000-0000-0000-000000000002', 'bharat_mart', 'Bharat Mart Enterprise', 'ACTIVE', 'FAIL_CLOSED', 30)
ON CONFLICT (tenant_id) DO NOTHING;
