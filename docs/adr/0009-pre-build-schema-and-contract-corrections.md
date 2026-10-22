# ADR-0009: Pre-build Schema and Contract Corrections

**Status:** Accepted  
**Date:** October 2026  
**Deciders:** Founder / Lead Architect  
**Supersedes:** Amends ADR-0002, ADR-0003, ADR-0004, ADR-0008  
**Superseded by:** —  

---

## Context

Prior to beginning Stage 1 application code implementation, a formal consistency audit of the Project Aegis architectural contracts and specifications revealed several contract, schema, and syntax issues across protected documents [06-api-and-event-schemas.md](../06-api-and-event-schemas.md), [07-data-model.md](../07-data-model.md), and [09-rule-dsl-spec.md](../09-rule-dsl-spec.md) (governed by [AGENTS.md Law 2](../../AGENTS.md#law-2-protected-documents-never-edit-without-an-accepted-adr)):

1. **Foreign Key Identifier Mismatch:** In [docs/19-identity-and-auth-spec.md](../19-identity-and-auth-spec.md), the `V9__init_auth_and_sessions.sql` migration referenced `tenants(id)`. However, the canonical PostgreSQL primary key in [docs/07-data-model.md](../07-data-model.md) is `tenant_id` (UUID). All foreign key constraints must consistently reference `tenants(tenant_id)`.
2. **Missing Case Review Queue Tables:** [docs/03-prd.md](../03-prd.md), [docs/10-design-system-and-ux.md](../10-design-system-and-ux.md), and [docs/18-frontend-architecture.md](../18-frontend-architecture.md) establish the manual review case queue as a primary operational interface for Risk Analysts. However, [docs/07-data-model.md](../07-data-model.md) omitted the relational schemas for `cases` and `case_events`.
3. **RLS Policy Gaps in Audit Tables:** In [docs/07-data-model.md](../07-data-model.md), both `audit_chain_heads` and `maker_checker_audit` carry `tenant_id` columns but lacked `ALTER TABLE ... ENABLE ROW LEVEL SECURITY`, `FORCE ROW LEVEL SECURITY`, and tenant isolation policies.
4. **OpenAPI Contract Formatting and Scope:** The OpenAPI block embedded in [docs/06-api-and-event-schemas.md](../06-api-and-event-schemas.md) contained invalid YAML syntax (raw markdown links embedded within description strings) and response objects missing required description fields. Furthermore, it did not cover all screens specified in [docs/18-frontend-architecture.md](../18-frontend-architecture.md) (e.g., SSE live event stream, case queue operations, dataset import, and authentication endpoints).
5. **Claims Discipline Standard:** [docs/09-rule-dsl-spec.md](../09-rule-dsl-spec.md), [docs/03-prd.md](../03-prd.md), and [docs/15-go-to-market.md](../15-go-to-market.md) contained unmeasured phrases ("evaluates in microseconds", "microsecond p99") violating Decision D9 and AGENTS.md §3.4.
6. **Clock SPI Naming Collision:** The custom temporal abstraction `com.aegis.core.Clock` clashed with standard `java.time.Clock`.

---

## Decision

We make the following normative architectural and contract corrections across the Aegis specifications:

### 1. Relational Schema & Foreign Key Corrections (Doc 07, Doc 19)
- All foreign key references to `tenants` across `aegis_auth.users`, `aegis_auth.api_keys`, `aegis_auth.audit_events`, and related tables must strictly reference `tenants(tenant_id)`.
- Introduce `cases` and `case_events` tables in [docs/07-data-model.md](../07-data-model.md):
  - `cases`: Keyed by `(tenant_id, case_id UUID)`, referencing `decisions(tenant_id, decision_id)`, with `status` (`OPEN`, `ASSIGNED`, `RESOLVED`, `CLOSED`), `priority`, `assigned_to`, `created_at`, `updated_at`.
  - `case_events`: Keyed by `event_id UUID`, tracking analyst assignment, state transitions, notes, and actions.
  - Both tables must enforce PostgreSQL Row-Level Security (`ENABLE ROW LEVEL SECURITY` and `FORCE ROW LEVEL SECURITY`) with `tenant_isolation` policies.
  - Ensure `decisions` table provides a composite unique key on `(tenant_id, decision_id)` to satisfy the composite foreign key constraint.
- Enable RLS and `FORCE ROW LEVEL SECURITY` on `audit_chain_heads` and `maker_checker_audit` with the canonical tenant isolation policy:
  `USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID)`.
- Update migration sequence in [docs/17-backend-design.md](../17-backend-design.md) to add `V10__cases_and_analyst_queue.sql`.
- Add task `TSK-039` in Milestone M7 in [docs/20-build-backlog.md](../20-build-backlog.md).

### 2. Machine-Readable OpenAPI Single Source of Truth (Doc 06, /api/openapi.yaml)
- Create `/api/openapi.yaml` as the canonical, machine-validated OpenAPI 3.1 contract.
- The contract must cover 100% of the endpoints defined in the screen-to-endpoint mapping of [docs/18-frontend-architecture.md §7](../18-frontend-architecture.md#7-screen-to-api-endpoint-mapping):
  - Ingestion & Decisions: `POST /v1/decisions`
  - Real-Time Feeds: `GET /v1/events/stream` (Server-Sent Events)
  - Case Review Queue: `GET /v1/cases`, `GET /v1/cases/{case_id}`, `POST /v1/cases/{case_id}/assign`, `POST /v1/cases/{case_id}/resolve`
  - Rules & Simulation: `GET /v1/rulesets`, `GET /v1/rulesets/{id}`, `POST /v1/rulesets`, `POST /v1/rulesets/{id}/simulate`, `POST /v1/rulesets/{id}/submit`, `POST /v1/rulesets/{id}/approve`, `POST /v1/rulesets/{id}/promote`, `POST /v1/rulesets/{id}/rollback`
  - Replay & Backtest: `POST /v1/replays`, `GET /v1/replays/{id}`, `GET /v1/replays/{id}/report`
  - Dataset Import: `POST /v1/datasets/import`, `GET /v1/datasets/import/{id}`
  - Audit & Verification: `GET /v1/audit/chains`, `GET /v1/audit/verify`
  - Identity & Sessions: `POST /v1/auth/login`, `POST /v1/auth/mfa/challenge`, `POST /v1/auth/mfa/verify`, `POST /v1/auth/refresh`, `POST /v1/auth/logout`, `GET /v1/auth/me`, `POST /v1/auth/password/reset-request`, `POST /v1/auth/password/reset`, `GET /v1/auth/users`, `POST /v1/auth/users/invite`, `GET /v1/auth/api-keys`, `POST /v1/auth/api-keys`
- All response objects must contain a valid `description` field.
- Remove all markdown hyperlinks inside YAML descriptions.
- Replace the raw embedded OpenAPI code block in [docs/06-api-and-event-schemas.md](../06-api-and-event-schemas.md) with a clear architectural pointer to `/api/openapi.yaml`.
- Author `/tools/check-openapi.mjs` to validate `/api/openapi.yaml` during automated docs testing.

### 3. Claims Discipline Rewording (Doc 03, Doc 09, Doc 15)
- Replace unmeasured phrases ("evaluates in microseconds", "microsecond p99") with "measured by benchmark (see doc 11)".

### 4. EventClock SPI Port Disambiguation
- Rename `com.aegis.core.Clock` to `com.aegis.core.EventClock` across [ADR-0003](./0003-event-time-for-deterministic-replay.md), [docs/11-test-and-performance-strategy.md](../11-test-and-performance-strategy.md), [docs/17-backend-design.md](../17-backend-design.md), and [AGENTS.md](../../AGENTS.md).

### 5. Vertical Slice Task Reordering (Doc 20)
- Reorder "The First 10 Tasks" in [docs/20-build-backlog.md](../20-build-backlog.md) to prioritize shipping the end-to-end vertical slice:
  `TSK-001`, `TSK-002`, `TSK-003`, `TSK-005`, `TSK-009`, `TSK-004` (using a stub evaluator), `TSK-006`, `TSK-007`, `TSK-008`, followed by M2 and M3 tasks.
- In `TSK-004`, remove the arbitrary pass/fail criterion `< 20 ms`; treat latency as an empirical recorded measurement, with formal performance regression gating located in M9.

### 6. Velocity Script Reference & Idempotency Edge (ADR-0005)
- Append the canonical Redis Lua sliding-window reference script to [ADR-0005](./0005-velocity-script-event-id-idempotency.md) as Appendix A.
- Note benchmark results (Redis 7.0, 100 runs of 50 concurrent calls at limit 5, 0 failures).
- Document the known failover edge: if Redis state is lost during ungraceful master crash before replication, a redelivered transaction that previously failed could pass. `processed_events` prevents duplicate decision row insertion, and the nightly PostgreSQL reconciliation job detects and bounds divergence.

---

## Consequences

### Positive
- **Machine Verifiable:** PostgreSQL DDL and OpenAPI contracts are strictly validated by automated test scripts (`check-sql.mjs`, `check-openapi.mjs`).
- **Zero Ambiguity:** Risk Analyst case management tables and routes are fully specified with identical multi-tenant isolation guarantees.
- **Strict Compliance:** Adheres to AGENTS.md Law 2 by formally authoring and accepting this ADR prior to updating protected documents.
