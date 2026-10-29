-- Migration V9: Init auth and sessions schema per Document 19 and ADR-0011

CREATE SCHEMA IF NOT EXISTS aegis_auth;

-- 1. Users Table (email and mobile globally unique per ADR-0011)
CREATE TABLE IF NOT EXISTS aegis_auth.users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL UNIQUE,
    mobile VARCHAR(32) NOT NULL UNIQUE,
    full_name VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL, -- 'owner', 'admin', 'risk_manager', 'rule_author', 'risk_analyst', 'auditor', 'developer'
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'PENDING_ONBOARDING', 'ACTIVE', 'LOCKED', 'DEACTIVATED'
    totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    lockout_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Credentials Table (Argon2id Hashes)
CREATE TABLE IF NOT EXISTS aegis_auth.user_credentials (
    user_id UUID PRIMARY KEY REFERENCES aegis_auth.users(user_id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    password_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE
);

-- 3. TOTP MFA Secrets Table (Encrypted with AES-256-GCM)
CREATE TABLE IF NOT EXISTS aegis_auth.user_totp_secrets (
    user_id UUID PRIMARY KEY REFERENCES aegis_auth.users(user_id) ON DELETE CASCADE,
    secret_key_encrypted BYTEA NOT NULL,
    initialization_vector BYTEA NOT NULL,
    recovery_codes_hash TEXT[] NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. User Invites Table
CREATE TABLE IF NOT EXISTS aegis_auth.user_invites (
    invite_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    invited_by_user_id UUID NOT NULL REFERENCES aegis_auth.users(user_id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at TIMESTAMPTZ
);

-- 5. Verification OTPs Table
CREATE TABLE IF NOT EXISTS aegis_auth.auth_otp_codes (
    otp_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    recipient_identifier VARCHAR(255) NOT NULL,
    otp_purpose VARCHAR(32) NOT NULL,
    otp_hash VARCHAR(64) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. User Sessions Table
CREATE TABLE IF NOT EXISTS aegis_auth.user_sessions (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES aegis_auth.users(user_id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    ip_address INET NOT NULL,
    user_agent TEXT NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_tenant_role ON aegis_auth.users(tenant_id, role);
CREATE INDEX IF NOT EXISTS idx_sessions_user_active ON aegis_auth.user_sessions(user_id) WHERE is_revoked = FALSE;
CREATE INDEX IF NOT EXISTS idx_otp_lookup ON aegis_auth.auth_otp_codes(recipient_identifier, otp_purpose) WHERE consumed_at IS NULL;

-- 7. Dedicated DB Role for Auth Service (ADR-0011b)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_auth_user') THEN
        CREATE ROLE app_auth_user WITH LOGIN PASSWORD 'app_auth_user';
    END IF;
END $$;

GRANT USAGE ON SCHEMA aegis_auth TO app_auth_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA aegis_auth TO app_auth_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA aegis_auth TO app_auth_user;
GRANT SELECT ON TABLE public.tenants TO app_auth_user;
GRANT INSERT, SELECT ON TABLE public.audit_management_events TO app_auth_user;

-- 8. Row-Level Security Enable & Force (ADR-0011b)
ALTER TABLE aegis_auth.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE aegis_auth.users FORCE ROW LEVEL SECURITY;

ALTER TABLE aegis_auth.user_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE aegis_auth.user_credentials FORCE ROW LEVEL SECURITY;

ALTER TABLE aegis_auth.user_totp_secrets ENABLE ROW LEVEL SECURITY;
ALTER TABLE aegis_auth.user_totp_secrets FORCE ROW LEVEL SECURITY;

ALTER TABLE aegis_auth.user_invites ENABLE ROW LEVEL SECURITY;
ALTER TABLE aegis_auth.user_invites FORCE ROW LEVEL SECURITY;

ALTER TABLE aegis_auth.auth_otp_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE aegis_auth.auth_otp_codes FORCE ROW LEVEL SECURITY;

ALTER TABLE aegis_auth.user_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE aegis_auth.user_sessions FORCE ROW LEVEL SECURITY;

-- 9. RLS Policies (ADR-0011b)
-- Policies for app_auth_user (no BYPASSRLS) to perform pre-tenant resolution
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'aegis_auth' AND tablename = 'users' AND policyname = 'auth_user_pre_tenant_select') THEN
        CREATE POLICY auth_user_pre_tenant_select ON aegis_auth.users
            FOR SELECT TO app_auth_user
            USING (true);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'aegis_auth' AND tablename = 'users' AND policyname = 'auth_user_tenant_write') THEN
        CREATE POLICY auth_user_tenant_write ON aegis_auth.users
            FOR ALL TO app_auth_user
            USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid OR current_setting('app.current_tenant_id', true) IS NULL OR current_setting('app.current_tenant_id', true) = '');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'aegis_auth' AND tablename = 'user_credentials' AND policyname = 'auth_user_credentials_policy') THEN
        CREATE POLICY auth_user_credentials_policy ON aegis_auth.user_credentials
            FOR ALL TO app_auth_user
            USING (true) WITH CHECK (true);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'aegis_auth' AND tablename = 'user_totp_secrets' AND policyname = 'auth_user_totp_policy') THEN
        CREATE POLICY auth_user_totp_policy ON aegis_auth.user_totp_secrets
            FOR ALL TO app_auth_user
            USING (true) WITH CHECK (true);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'aegis_auth' AND tablename = 'auth_otp_codes' AND policyname = 'auth_user_otp_policy') THEN
        CREATE POLICY auth_user_otp_policy ON aegis_auth.auth_otp_codes
            FOR ALL TO app_auth_user
            USING (true) WITH CHECK (true);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'aegis_auth' AND tablename = 'user_invites' AND policyname = 'auth_user_invites_policy') THEN
        CREATE POLICY auth_user_invites_policy ON aegis_auth.user_invites
            FOR ALL TO app_auth_user
            USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid OR current_setting('app.current_tenant_id', true) IS NULL OR current_setting('app.current_tenant_id', true) = '');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'aegis_auth' AND tablename = 'user_sessions' AND policyname = 'auth_user_sessions_policy') THEN
        CREATE POLICY auth_user_sessions_policy ON aegis_auth.user_sessions
            FOR ALL TO app_auth_user
            USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid OR current_setting('app.current_tenant_id', true) IS NULL OR current_setting('app.current_tenant_id', true) = '');
    END IF;

    -- General tenant isolation policies for tenant session context
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'aegis_auth' AND tablename = 'users' AND policyname = 'tenant_isolation_users') THEN
        CREATE POLICY tenant_isolation_users ON aegis_auth.users
            USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'aegis_auth' AND tablename = 'user_invites' AND policyname = 'tenant_isolation_invites') THEN
        CREATE POLICY tenant_isolation_invites ON aegis_auth.user_invites
            USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'aegis_auth' AND tablename = 'user_sessions' AND policyname = 'tenant_isolation_sessions') THEN
        CREATE POLICY tenant_isolation_sessions ON aegis_auth.user_sessions
            USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
    END IF;
END $$;

