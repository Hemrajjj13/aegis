# Data Model and Storage Specification

**Document:** 07  
**Status:** Draft-complete (docs v1.0, machine-checked, not yet reviewed)
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

> **Assumption note:** Database schemas, column choices, retention windows, and partitioning intervals in this document reflect Stage 1 architecture hypotheses. Any item marked `[interview-driven]` is subject to change based on pilot customer requirements and data volumes. Do not freeze this schema before customer pilot integration reviews.

---

## 1. Purpose and Architecture Principles

This document specifies the PostgreSQL 16+ relational schema, indexing strategy, multi-tenant isolation, role-based database privileges, and lifecycle data jobs for Project Aegis.

Every table and query adheres to the core system decisions:
1. **PostgreSQL is the single source of truth** for all transactions, decisions, rule configurations, and audit batches (ADR-0001, ADR-0002).
2. **Strict Multi-Tenancy via Row-Level Security (RLS):** Every multi-tenant table includes `tenant_id UUID NOT NULL`. RLS policies ensure cross-tenant queries are blocked at the database engine level.
3. **Idempotency and Exactly-Once Decision Records:** The `processed_events` table is locked and updated in the identical database transaction as the decision record (ADR-0002, ADR-0005).
4. **Decoupled Outbox Pattern:** Decisions emit Kafka events via a transactional `outbox` table, eliminating distributed 2PC transactions (ADR-0002).
5. **Batched Audit Sealing:** Decision records are written fast without hot-row serialization; a background sealer aggregates batches into Merkle trees chained into `decision_audit_batches` (ADR-0004).
6. **Isolated Replay Namespace:** Replay workers operate under a restricted database role (`app_replay_user`) with zero INSERT or UPDATE permissions on live operational tables (`decisions`, `processed_events`, `outbox`) (ADR-0003, ADR-0005).
7. **Zero Raw Card Numbers (No PAN):** No column accepts, stores, or logs raw card numbers (ISO 7812). Only tokenised identifiers, masked BIN/last4, or tenant-keyed HMACs are persisted (doc 01 §5.1).

---

## 2. Database Roles and Privilege Matrix

To enforce security boundaries in depth, PostgreSQL privileges are segregated across distinct service roles. Connection pools (HikariCP) in Spring Boot authenticate using the specific role for each component.

| Role Name | Used By | Allowed Operations | Restricted / Prohibited Operations |
|---|---|---|---|
| `app_migration_user` | Flyway / Liquibase CI | Full DDL (CREATE, ALTER, DROP), DML on all tables | Used only during CI/CD deployments; no runtime connections |
| `app_live_user` | `decision-api`, `stream-processor` | SELECT, INSERT on `decisions`, `processed_events`, `outbox`; SELECT on `ruleset_versions`, `tenants` | NO DELETE; NO UPDATE on `decisions` (except batch sealing); NO access to `replay_*` execution |
| `app_replay_user` | `replay-engine` (batch) | SELECT on `ruleset_versions`, `tenants`; SELECT, INSERT, UPDATE, DELETE on `replay_runs`, `replay_decisions` | **ABSOLUTE PROHIBITION:** NO INSERT, UPDATE, or DELETE on `decisions`, `processed_events`, `outbox`, or `audit_*` tables |
| `app_sealer_user` | Audit Sealer Daemon | SELECT, UPDATE (`sealed`, `batch_id`) on `decisions`; INSERT on `decision_audit_batches`, `audit_chain_heads` | NO INSERT on `decisions`; NO access to `outbox` |
| `app_outbox_relay` | Debezium / Outbox Poller | SELECT, DELETE (or UPDATE `published_at`) on `outbox` | NO access to `decisions` or `ruleset_versions` |
| `app_console_user` | Web Console Backend | SELECT on all operational tables (filtered by RLS); INSERT/UPDATE on `rulesets`, `ruleset_versions`, `maker_checker_audit`, `audit_management_events` | Cannot bypass maker-checker; subject to tenant isolation RLS |

### Privilege DDL Specification

```sql
-- Role definitions (Passwords supplied via environment variables / secret store: AWS Secrets Manager in Tier 1/2, local .env in Tier 0)
CREATE ROLE app_migration_user WITH LOGIN PASSWORD '${DB_MIGRATION_PASSWORD}';
CREATE ROLE app_live_user WITH LOGIN PASSWORD '${DB_LIVE_PASSWORD}';
CREATE ROLE app_replay_user WITH LOGIN PASSWORD '${DB_REPLAY_PASSWORD}';
CREATE ROLE app_sealer_user WITH LOGIN PASSWORD '${DB_SEALER_PASSWORD}';
CREATE ROLE app_outbox_relay WITH LOGIN PASSWORD '${DB_OUTBOX_PASSWORD}';
CREATE ROLE app_console_user WITH LOGIN PASSWORD '${DB_CONSOLE_PASSWORD}';

-- Revoke default public permissions
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO app_live_user, app_replay_user, app_sealer_user, app_outbox_relay, app_console_user;

-- Live user permissions
GRANT SELECT, INSERT ON TABLE processed_events, outbox TO app_live_user;
GRANT SELECT, INSERT ON TABLE decisions TO app_live_user;
GRANT SELECT ON TABLE ruleset_versions, tenants TO app_live_user;

-- Replay user permissions (Explicit isolation guarantee)
GRANT SELECT ON TABLE tenants, ruleset_versions TO app_replay_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE replay_runs, replay_decisions, dataset_imports TO app_replay_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE dataset_imports TO app_console_user;
REVOKE INSERT, UPDATE, DELETE ON TABLE decisions, processed_events, outbox, decision_audit_batches, audit_management_events FROM app_replay_user;

-- Sealer permissions
GRANT SELECT, UPDATE (sealed, batch_id) ON TABLE decisions TO app_sealer_user;
GRANT SELECT, INSERT, UPDATE ON TABLE decision_audit_batches, audit_chain_heads TO app_sealer_user;

-- Outbox relay permissions
GRANT SELECT, UPDATE (published_at) ON TABLE outbox TO app_outbox_relay;
```

---

## 3. Operational Live Tables

### 3.1 `tenants`

Stores tenant configuration, billing tier, cryptographic key references, and global runtime parameters.

```sql
CREATE TABLE tenants (
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

CREATE INDEX idx_tenants_slug ON tenants(slug);
```

### 3.2 `processed_events`

Enforces physical exactly-once idempotency across synchronous API retries and asynchronous Kafka redeliveries.

```sql
CREATE TABLE processed_events (
    tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),
    event_id            VARCHAR(128) NOT NULL,
    decision_id         UUID NOT NULL,
    event_time          TIMESTAMPTZ NOT NULL,
    processed_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    payload_hash        CHAR(64) NOT NULL, -- SHA-256 of canonical input JSON
    PRIMARY KEY (tenant_id, event_id)
);

-- Index for temporal queries and retention cleanup
CREATE INDEX idx_processed_events_cleanup ON processed_events (processed_at);
```

*Operational Invariant:* Inserted within the exact same database transaction as `decisions` and `outbox`. If an event is re-submitted with the same `(tenant_id, event_id)` but differing `payload_hash`, the API aborts with HTTP 409 (Conflict).

### 3.3 `decisions` (Partitioned by Month)

System of record for every fraud evaluation. Written synchronously before the caller receives a verdict (ADR-0002).

```sql
CREATE TABLE decisions (
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

-- Indexes defined on the partitioned table (automatically created on child partitions)
CREATE INDEX idx_decisions_tenant_event ON decisions (tenant_id, event_id);
CREATE INDEX idx_decisions_tenant_entity ON decisions (tenant_id, entity_id_hash, created_at DESC);
CREATE INDEX idx_decisions_tenant_unsealed ON decisions (tenant_id, created_at) WHERE sealed = FALSE;
CREATE INDEX idx_decisions_ruleset_hash ON decisions (tenant_id, ruleset_version_hash);
```

#### Monthly Partition Example DDL
```sql
-- Partition for October 2026
CREATE TABLE decisions_y2026m10 PARTITION OF decisions
    FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');

-- Partition for November 2026
CREATE TABLE decisions_y2026m11 PARTITION OF decisions
    FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');
```

### 3.4 `outbox`

Enables reliable transactional event publishing to Kafka without distributed transactions (ADR-0002).

```sql
CREATE TABLE outbox (
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

-- Highly optimized partial index for outbox poller
CREATE INDEX idx_outbox_unprocessed ON outbox (id ASC) WHERE published_at IS NULL;
```

### 3.5 `cases`

Operational table backing the Risk Analyst review queue (ADR-0009). When an evaluation returns `REVIEW`, a case record is generated or linked to the decision.

```sql
CREATE TABLE cases (
    case_id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(tenant_id),
    decision_id             UUID NOT NULL,
    decision_created_at     TIMESTAMPTZ NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'OPEN', -- OPEN, ASSIGNED, RESOLVED, CLOSED
    priority                VARCHAR(16) NOT NULL DEFAULT 'MEDIUM', -- LOW, MEDIUM, HIGH, CRITICAL
    assigned_to             VARCHAR(128),                          -- User ID or email of analyst
    assigned_at             TIMESTAMPTZ,
    resolved_at             TIMESTAMPTZ,
    resolution              VARCHAR(32),                           -- CONFIRMED_FRAUD, FALSE_POSITIVE, ESCALATED, EXPIRED
    resolution_notes        TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_cases_tenant_decision UNIQUE (tenant_id, decision_id),
    FOREIGN KEY (tenant_id, decision_created_at, decision_id) REFERENCES decisions(tenant_id, created_at, decision_id) ON DELETE RESTRICT
);

CREATE INDEX idx_cases_tenant_status ON cases (tenant_id, status, created_at DESC);
CREATE INDEX idx_cases_tenant_assignee ON cases (tenant_id, assigned_to) WHERE status IN ('OPEN', 'ASSIGNED');
```

### 3.6 `case_events`

Immutable chronological audit log of all case workflow transitions, analyst assignments, comments, and resolution actions (ADR-0009).

```sql
CREATE TABLE case_events (
    event_id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(tenant_id),
    case_id                 UUID NOT NULL REFERENCES cases(case_id) ON DELETE CASCADE,
    actor_id                VARCHAR(128) NOT NULL,
    actor_role              VARCHAR(64) NOT NULL,
    action                  VARCHAR(32) NOT NULL, -- CREATED, ASSIGNED, COMMENTED, RESOLVED, REOPENED
    notes                   TEXT,
    metadata                JSONB,                -- Captured transition metadata or diff
    created_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_case_events_case ON case_events (tenant_id, case_id, created_at ASC);
```


---

## 4. Audit and Cryptographic Integrity Tables

Implements the two-tier audit log architecture defined in ADR-0004: synchronously chained management events and Merkle-batched decision logs.

### 4.1 `decision_audit_batches`

Records sealed batches of decision rows produced by the background batch sealer.

```sql
CREATE TABLE decision_audit_batches (
    batch_id                    UUID NOT NULL,
    tenant_id                   UUID NOT NULL REFERENCES tenants(tenant_id),
    first_decision_id           UUID NOT NULL,
    last_decision_id            UUID NOT NULL,
    first_event_time            TIMESTAMPTZ NOT NULL,
    last_event_time             TIMESTAMPTZ NOT NULL,
    batch_size                  INT NOT NULL,
    merkle_root                 CHAR(64) NOT NULL,      -- Hex SHA-256 Merkle root
    previous_batch_chain_hash   CHAR(64) NOT NULL,      -- Previous batch's batch_chain_hash
    batch_chain_hash            CHAR(64) NOT NULL,      -- SHA-256(previous_batch_chain_hash || merkle_root)
    s3_anchor_uri               TEXT,                   -- s3://aegis-audit-worm/{tenant_id}/{date}/{batch_id}.json
    sealed_at                   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (tenant_id, batch_id)
);

CREATE INDEX idx_decision_batches_chain ON decision_audit_batches (tenant_id, sealed_at ASC);
```

### 4.2 `audit_chain_heads`

Tracks the monotonic head hash for each tenant's audit streams to guarantee linear hash continuity without race conditions.

```sql
CREATE TABLE audit_chain_heads (
    tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),
    stream_name         VARCHAR(64) NOT NULL, -- 'MANAGEMENT_EVENTS', 'DECISION_BATCHES'
    last_hash           CHAR(64) NOT NULL,
    sequence_number     BIGINT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (tenant_id, stream_name)
);
```

### 4.3 `audit_management_events`

Synchronously hash-chained audit log for human actions, rule modifications, and permission shifts.

```sql
CREATE TABLE audit_management_events (
    event_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),
    actor_id            VARCHAR(128) NOT NULL,
    actor_role          VARCHAR(64) NOT NULL,
    client_ip           INET,
    event_type          VARCHAR(64) NOT NULL, -- 'RULE_DRAFT', 'RULE_APPROVAL', 'LOGIN', 'KEY_ROTATION'
    target_entity_type  VARCHAR(64) NOT NULL,
    target_entity_id    VARCHAR(128) NOT NULL,
    diff_payload        JSONB,                -- Structured before/after diff
    previous_hash       CHAR(64) NOT NULL,
    chain_hash          CHAR(64) NOT NULL,    -- SHA-256(previous_hash || canonical_payload)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_audit_mgmt_tenant_created ON audit_management_events (tenant_id, created_at DESC);
```

---

## 5. Rules and Maker-Checker Configuration Tables

### 5.1 `rulesets`

Defines high-level rule groups per tenant.

```sql
CREATE TABLE rulesets (
    ruleset_id          VARCHAR(64) NOT NULL,
    tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),
    name                VARCHAR(128) NOT NULL,
    description         TEXT,
    active_version_hash CHAR(64),             -- Currently live version hash
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (tenant_id, ruleset_id)
);
```

### 5.2 `ruleset_versions`

Immutable, cryptographically hashed versions of rule definitions (ADR-0003, doc 09).

```sql
CREATE TABLE ruleset_versions (
    version_hash        CHAR(64) NOT NULL,    -- SHA-256 canonical JSON of AST + parameters
    tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),
    ruleset_id          VARCHAR(64) NOT NULL,
    version_number      INT NOT NULL,
    dsl_content         TEXT NOT NULL,        -- Raw CEL DSL source text
    ast_json            JSONB NOT NULL,       -- Compiled AST representation
    lifecycle_state     VARCHAR(32) NOT NULL, -- DRAFT, SIMULATED, SUBMITTED, APPROVED, SHADOW, CANARY, LIVE, REVERTED
    canary_percentage   INT NOT NULL DEFAULT 0,
    author_id           VARCHAR(128) NOT NULL,
    approver_id         VARCHAR(128),         -- Maker-checker enforced (author_id != approver_id)
    submitted_at        TIMESTAMPTZ,
    approved_at         TIMESTAMPTZ,
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (tenant_id, version_hash),
    CONSTRAINT chk_maker_checker CHECK (
        lifecycle_state NOT IN ('APPROVED', 'SHADOW', 'CANARY', 'LIVE') 
        OR (author_id <> approver_id AND approver_id IS NOT NULL)
    )
);

CREATE INDEX idx_ruleset_versions_lookup ON ruleset_versions (tenant_id, ruleset_id, version_number DESC);
```

### 5.3 `maker_checker_audit`

Logs all approval transitions with cryptographic verification.

```sql
CREATE TABLE maker_checker_audit (
    audit_id            BIGSERIAL PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),
    ruleset_id          VARCHAR(64) NOT NULL,
    version_hash        CHAR(64) NOT NULL,
    action              VARCHAR(32) NOT NULL, -- SUBMIT, APPROVE, REJECT, DEPLOY, REVERT
    actor_id            VARCHAR(128) NOT NULL,
    actor_role          VARCHAR(64) NOT NULL,
    comment             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_maker_checker_version ON maker_checker_audit (tenant_id, version_hash);
```

---

## 6. Replay Engine Tables

Replay operations run completely isolated from operational tables. Results are written exclusively to `replay_*` tables namespaced by `run_id` (ADR-0003, ADR-0005).

### 6.1 `replay_runs`

Metadata and aggregate metrics comparing two ruleset versions.

```sql
CREATE TABLE replay_runs (
    run_id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(tenant_id),
    initiator_id            VARCHAR(128) NOT NULL,
    ruleset_a_hash          CHAR(64) NOT NULL,
    ruleset_b_hash          CHAR(64) NOT NULL,
    dataset_source          TEXT NOT NULL,        -- 'POSTGRES_HISTORY' or 's3://bucket/path'
    dataset_start_time      TIMESTAMPTZ NOT NULL,
    dataset_end_time        TIMESTAMPTZ NOT NULL,
    status                  VARCHAR(32) NOT NULL, -- QUEUED, RUNNING, COMPLETED, FAILED
    total_events            BIGINT NOT NULL DEFAULT 0,
    processed_events        BIGINT NOT NULL DEFAULT 0,
    divergence_count        BIGINT NOT NULL DEFAULT 0, -- ADR-0003 live-vs-replay divergence
    divergence_pct          NUMERIC(5, 4) NOT NULL DEFAULT 0.0000,
    p50_latency_micros_a    INT,
    p95_latency_micros_a    INT,
    p99_latency_micros_a    INT,
    p50_latency_micros_b    INT,
    p95_latency_micros_b    INT,
    p99_latency_micros_b    INT,
    error_message           TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    completed_at            TIMESTAMPTZ
);

CREATE INDEX idx_replay_runs_tenant ON replay_runs (tenant_id, created_at DESC);
```

### 6.2 `replay_decisions`

Individual evaluation records emitted by replay workers. Note that this table does NOT alter or touch `decisions`.

```sql
CREATE TABLE replay_decisions (
    run_id                  UUID NOT NULL REFERENCES replay_runs(run_id) ON DELETE CASCADE,
    tenant_id               UUID NOT NULL REFERENCES tenants(tenant_id),
    event_id                VARCHAR(128) NOT NULL,
    event_time              TIMESTAMPTZ NOT NULL,
    variant                 CHAR(1) NOT NULL,      -- 'A' or 'B'
    outcome                 VARCHAR(16) NOT NULL,  -- APPROVE, DECLINE, REVIEW
    score                   NUMERIC(5, 2) NOT NULL,
    rules_fired             JSONB NOT NULL,
    live_outcome            VARCHAR(16),           -- Outcome recorded in production
    is_divergent            BOOLEAN NOT NULL DEFAULT FALSE, -- variant outcome != live_outcome
    evaluation_micros       INT NOT NULL,
    PRIMARY KEY (run_id, variant, event_id)
);

CREATE INDEX idx_replay_decisions_divergent ON replay_decisions (run_id, is_divergent) WHERE is_divergent = TRUE;
```

### 6.3 `dataset_imports` (Decision D8)

Tracks uploaded prospective customer backtest datasets, schema mapping configurations, validation status, and automated retention windows for the free backtest offer.

```sql
CREATE TABLE dataset_imports (
    import_id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(tenant_id),
    initiator_id            VARCHAR(128) NOT NULL,
    filename                VARCHAR(255) NOT NULL,
    file_format             VARCHAR(16) NOT NULL,         -- 'CSV', 'PARQUET'
    file_size_bytes         BIGINT NOT NULL,
    s3_storage_uri          TEXT NOT NULL,                -- s3://aegis-backtest-data/{tenant_id}/{import_id}/raw
    status                  VARCHAR(32) NOT NULL,         -- 'PENDING_UPLOAD', 'VALIDATING', 'READY', 'FAILED', 'PURGED'
    column_mapping          JSONB NOT NULL,               -- Map raw columns -> Aegis event fields
    validation_report       JSONB,                        -- Summary of validation pass/fail counts and warnings
    total_rows              BIGINT NOT NULL DEFAULT 0,
    valid_rows              BIGINT NOT NULL DEFAULT 0,
    invalid_rows            BIGINT NOT NULL DEFAULT 0,
    min_event_time          TIMESTAMPTZ,
    max_event_time          TIMESTAMPTZ,
    retention_expires_at    TIMESTAMPTZ NOT NULL,         -- Default: created_at + INTERVAL '30 days'
    error_message           TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    completed_at            TIMESTAMPTZ
);

CREATE INDEX idx_dataset_imports_tenant ON dataset_imports (tenant_id, created_at DESC);
CREATE INDEX idx_dataset_imports_expiry ON dataset_imports (retention_expires_at) WHERE status != 'PURGED';
```

---

## 7. Row-Level Security (RLS) Implementation

To ensure cross-tenant leaks are physically impossible via application bugs, Postgres Row-Level Security is enabled and forced on all tenant tables.

### 7.1 RLS Activation and Policies

```sql
-- Enable and force RLS on all tenant-isolated tables
ALTER TABLE processed_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE processed_events FORCE ROW LEVEL SECURITY;

ALTER TABLE decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE decisions FORCE ROW LEVEL SECURITY;

ALTER TABLE outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox FORCE ROW LEVEL SECURITY;

ALTER TABLE decision_audit_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE decision_audit_batches FORCE ROW LEVEL SECURITY;

ALTER TABLE audit_management_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_management_events FORCE ROW LEVEL SECURITY;

ALTER TABLE rulesets ENABLE ROW LEVEL SECURITY;
ALTER TABLE rulesets FORCE ROW LEVEL SECURITY;

ALTER TABLE ruleset_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE ruleset_versions FORCE ROW LEVEL SECURITY;

ALTER TABLE replay_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE replay_runs FORCE ROW LEVEL SECURITY;

ALTER TABLE replay_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE replay_decisions FORCE ROW LEVEL SECURITY;

ALTER TABLE dataset_imports ENABLE ROW LEVEL SECURITY;
ALTER TABLE dataset_imports FORCE ROW LEVEL SECURITY;

ALTER TABLE audit_chain_heads ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_chain_heads FORCE ROW LEVEL SECURITY;

ALTER TABLE maker_checker_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE maker_checker_audit FORCE ROW LEVEL SECURITY;

ALTER TABLE cases ENABLE ROW LEVEL SECURITY;
ALTER TABLE cases FORCE ROW LEVEL SECURITY;

ALTER TABLE case_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE case_events FORCE ROW LEVEL SECURITY;

-- Standard tenant isolation policy
CREATE POLICY tenant_isolation_processed_events ON processed_events
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_decisions ON decisions
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_outbox ON outbox
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_audit_batches ON decision_audit_batches
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_audit_mgmt ON audit_management_events
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_rulesets ON rulesets
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_ruleset_versions ON ruleset_versions
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_replay_runs ON replay_runs
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_replay_decisions ON replay_decisions
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_dataset_imports ON dataset_imports
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_audit_chain_heads ON audit_chain_heads
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_maker_checker ON maker_checker_audit
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_cases ON cases
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_case_events ON case_events
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
```

### 7.2 Connection Pooling Session Guard

In Spring Boot, connections retrieved from the HikariCP pool must set the session variable before running any queries, and clear it upon release:

```java
// Spring Boot DataSource interceptor pattern (conceptual architecture)
public void setTenantContext(Connection connection, UUID tenantId) throws SQLException {
    try (PreparedStatement stmt = connection.prepareStatement("SET LOCAL app.current_tenant_id = ?")) {
        stmt.setString(1, tenantId.toString());
        stmt.execute();
    }
}
```

*Note on Superuser Bypass:* Postgres superusers bypass RLS by default. Application roles (`app_live_user`, `app_replay_user`, etc.) must **never** be granted SUPERUSER or BYPASSRLS permissions. `FORCE ROW LEVEL SECURITY` ensures table owners are also bound by RLS.

---

## 8. Partitioning Strategy and Maintenance

### 8.1 Partitioning Model

The `decisions` table is partitioned on `created_at` using monthly range boundaries (`PARTITION BY RANGE (created_at)`).

**Why monthly partitioning?**
- At 2,000 TPS sustained, 1 month generates ~5.18 billion records. At ~1 KB per row (including indexes), one monthly partition is ~5 TB.
- Query performance: queries with time-range filters immediately prune non-matching partitions, maintaining index performance in memory.
- Instant, zero-overhead deletion: data retention expirations simply drop old partitions (`DROP TABLE decisions_y2025m01`), avoiding high-write vacuum overhead.

### 8.2 Partition Maintenance Daemon

Partitions are provisioned 2 months in advance via `pg_partman` or an internal scheduled task.

```sql
-- Conceptual maintenance automation
CREATE OR REPLACE PROCEDURE maintain_monthly_partitions()
LANGUAGE plpgsql
AS $$
DECLARE
    v_start_date DATE := date_trunc('month', CURRENT_DATE + INTERVAL '1 month');
    v_end_date   DATE := v_start_date + INTERVAL '1 month';
    v_part_name  TEXT := 'decisions_y' || to_char(v_start_date, 'YYYY') || 'm' || to_char(v_start_date, 'MM');
BEGIN
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF decisions FOR VALUES FROM (%L) TO (%L)',
                   v_part_name, v_start_date, v_end_date);
END;
$$;
```

---

## 9. Retention, Archival, and Deletion Jobs

### 9.1 Retention Policies

| Data Category | Target Retention Period | Storage Location | Purge Mechanism |
|---|---|---|---|
| Live Decision Records | 90 days in hot Postgres `decisions` [interview-driven] | Postgres NVMe | Partition detachment & drop |
| Cold Decision Archives | 7 years [interview-driven] (designed to align with RBI guidelines) | AWS S3 Parquet (Glacier Instant Retrieval) | S3 Lifecycle transition to Deep Archive |
| `processed_events` | 14 days [interview-driven] (2× Kafka 7-day retention) | Postgres | Continuous batch delete via index |
| `outbox` records | 3 days after `published_at` confirmation | Postgres | Hourly batch delete |
| `replay_*` runs & decisions | 30 days after run completion | Postgres | CASCADE delete on `replay_runs` |
| Imported Backtest Datasets | 30 days maximum (Decision D8) | S3 encrypted + Postgres metadata | Automated purge daemon + S3 lifecycle |
| Synchronous Audit Logs | 7 years [interview-driven] (designed to align with RBI guidelines) | Postgres + Daily S3 WORM Object Lock | S3 Object Lock retention policy |

### 9.2 Data Purge Procedures

#### Processed Events Purge
```sql
-- Deletes idempotency markers older than 14 days in batches to prevent transaction log bloat
DELETE FROM processed_events 
WHERE ctid IN (
    SELECT ctid FROM processed_events 
    WHERE processed_at < NOW() - INTERVAL '14 days' 
    LIMIT 10000
);
```

#### Outbox Cleanup
```sql
DELETE FROM outbox 
WHERE published_at IS NOT NULL 
  AND published_at < NOW() - INTERVAL '3 days';
```

### 9.3 Tenant Offboarding & Cryptographic Deletion

Under the DPDP Act 2023 and customer DPA terms, when a tenant terminates service:
1. Operational tables are deleted immediately: `DELETE FROM tenants WHERE tenant_id = :id` (cascading across unpartitioned tables).
2. For partitioned tables (`decisions`), records matching `tenant_id` are purged, or if tenant-dedicated partitions are used in enterprise tiers, dropped.
3. Cryptographic deletion (Crypto-shredding): The tenant's KMS master key (`kms_key_arn`) is scheduled for deletion in AWS KMS (7-day holding window). Without this key, all S3 Parquet backups and cold archives for that tenant become permanently unrecoverable.
4. A cryptographically signed **Certificate of Deletion** is generated containing the last audit batch chain hash and KMS destruction confirmation.

---

## 10. Decision Register References (formerly Open Questions)

All data model and storage questions have been resolved or classified per Decision **D3** in the [Decision Register](file:///c:/dev/aegis/docs/00-decisions/decision-register.md):

| # | Decision ID | Question / Topic | Status | Decision / Assumed Value |
|---|---|---|---|---|
| 1 | [DR-DM-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Hot retention duration:** Primary Postgres storage window. | Decided | 90 days in primary PostgreSQL `decisions` table; older partitions exported to S3 Parquet Glacier. |
| 2 | [DR-API-05](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Entity identifier hashing:** Account/entity ID velocity format. | Decided | Tenant-salted SHA-256 hash (`account_id_hash`) generated caller-side. Cleartext account numbers never accepted. |
| 3 | [DR-DM-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Partition granularity:** Table partitioning interval for `decisions`. | Decided | Monthly partitioning for `decisions` table at launch (5.18M rows/mo at 2,000 TPS). |
| 4 | [DR-DM-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Separate database schemas per tenant:** Shared schema vs dedicated. | Decided | Shared schema with PostgreSQL Row-Level Security (`FORCE ROW LEVEL SECURITY`) and tenant context session guards. |
| 5 | [DR-DM-04](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Replay decision retention:** `replay_decisions` row cleanup lifecycle. | Decided | Summary metrics stored permanently in `replay_runs`; granular `replay_decisions` purged after 30 days. |

