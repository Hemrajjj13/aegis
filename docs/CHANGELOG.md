# Project Aegis Documentation Suite — Changelog

**Status:** Draft-complete (docs v1.0, machine-checked, not yet reviewed)  
**Maintained by:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last Updated:** October 2026  

All releases and architectural specification increments follow Semantic Versioning (`vMAJOR.MINOR.PATCH`).

---

## [docs-v1.0.2] — 2026-10-22

### Summary
Pre-build schema, contract, and architectural alignment release governed by [ADR-0009](file:///c:/dev/aegis/docs/adr/0009-pre-build-schema-and-contract-corrections.md). Standardized relational foreign keys, introduced missing analyst review case queue tables with Row-Level Security, published machine-readable canonical OpenAPI 3.1 contract (`/api/openapi.yaml`), reordered backlog build sequence to prioritize the end-to-end vertical slice, disambiguated `EventClock` SPI port, softened unmeasured latency claims, and added the canonical Redis Lua reference implementation to ADR-0005 Appendix A.

### Changed & Added
- **Accepted ADR-0009:** Formally registered and accepted [ADR-0009: Pre-build Schema and Contract Corrections](file:///c:/dev/aegis/docs/adr/0009-pre-build-schema-and-contract-corrections.md) per AGENTS.md Law 2 before applying changes to protected specifications.
- **Relational Schema & FK Corrections (Doc 07, Doc 19):**
  - Replaced all `REFERENCES tenants(id)` foreign keys in [docs/19-identity-and-auth-spec.md](file:///c:/dev/aegis/docs/19-identity-and-auth-spec.md) with canonical `REFERENCES tenants(tenant_id)`.
  - Added `cases` and `case_events` DDL to [docs/07-data-model.md §3.5-3.6](file:///c:/dev/aegis/docs/07-data-model.md) with composite foreign key to `decisions(tenant_id, created_at, decision_id)` and uniqueness constraint `(tenant_id, decision_id)`.
  - Added `ALTER TABLE ... ENABLE ROW LEVEL SECURITY`, `FORCE ROW LEVEL SECURITY`, and `tenant_isolation` policies for `cases`, `case_events`, `audit_chain_heads`, and `maker_checker_audit` in [docs/07-data-model.md §7.1](file:///c:/dev/aegis/docs/07-data-model.md).
  - Updated Flyway migration order in [docs/17-backend-design.md §7](file:///c:/dev/aegis/docs/17-backend-design.md) with `V10__cases_and_analyst_queue.sql`.
  - Added full Case Review Queue task `TSK-032` (8 pts) to Milestone M7 in [docs/20-build-backlog.md](file:///c:/dev/aegis/docs/20-build-backlog.md).
  - Corrected table mapping in [docs/audit/readiness-report.md](file:///c:/dev/aegis/docs/audit/readiness-report.md) BR-04 (removing non-existent `rules` and `decision_rule_matches` stubs, accurately reflecting all 14 active tables).
- **Cross-Tenant Security Test Specification (Docs 08, 11):**
  - Updated Abuse Case 3 in [docs/08-threat-model.md §4.3](file:///c:/dev/aegis/docs/08-threat-model.md) to explicitly enumerate all 14 multi-tenant tables.
  - Added Section 4.5 `CrossTenantIsolationSecurityTest` in [docs/11-test-and-performance-strategy.md §4.5](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md) auditing unqualified queries, targeted cross-tenant queries, mutations, and unset session context.
- **OpenAPI 3.1 Single Source of Truth (/api/openapi.yaml, Doc 06):**
  - Created machine-readable [/api/openapi.yaml](file:///c:/dev/aegis/api/openapi.yaml) covering all 46 endpoint paths defined in Document 18 & 19 (including `POST /v1/decisions`, `GET /v1/events/stream` SSE, cases triage, ruleset simulation and maker-checker promote/rollback, replay reports, dataset import, and identity auth/session endpoints).
  - Removed embedded raw YAML block from [docs/06-api-and-event-schemas.md §2](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md) and replaced it with a pointer to `/api/openapi.yaml`.
  - Implemented [tools/check-openapi.mjs](file:///c:/dev/aegis/tools/check-openapi.mjs) using `@apidevtools/swagger-parser` to continuously validate the contract.
- **SQL Extraction & Verification Tooling (/tools/check-sql.mjs):**
  - Created [tools/check-sql.mjs](file:///c:/dev/aegis/tools/check-sql.mjs) extracting 108 discrete SQL statements from docs 07 and 19, resolving topological dependency order, verifying FK targets and RLS enforcement, and applying them to a throwaway `postgres:16` container when Docker is present (or performing static DDL AST validation).
  - Wired both `check-openapi.mjs` and `check-sql.mjs` into [tools/check_docs.js](file:///c:/dev/aegis/tools/check_docs.js) as automated checks 8 and 9.
- **Vertical Slice Task Reordering & Measurement Baseline (Doc 20):**
  - Reordered "The First 10 Tasks" in [docs/20-build-backlog.md §5](file:///c:/dev/aegis/docs/20-build-backlog.md) to build the end-to-end vertical slice first: `TSK-001`, `002`, `003`, `005`, `009`, `004` (with stub evaluator), `006`, `007`, `008`, followed by Milestone M2 and M3 tasks.
  - Removed the arbitrary `< 20 ms` pass/fail criterion from `TSK-004`; recorded latency as an empirical baseline measurement, locating formal performance regression gating in Milestone M9 (`TSK-040`).
- **EventClock SPI Port Disambiguation:**
  - Renamed custom clock SPI `com.aegis.core.Clock` to `com.aegis.core.EventClock` across [AGENTS.md §3.2](file:///c:/dev/aegis/AGENTS.md), [ADR-0003](file:///c:/dev/aegis/docs/adr/0003-event-time-for-deterministic-replay.md), [docs/11-test-and-performance-strategy.md](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md), [docs/17-backend-design.md](file:///c:/dev/aegis/docs/17-backend-design.md), and [docs/20-build-backlog.md](file:///c:/dev/aegis/docs/20-build-backlog.md) to eliminate collisions with standard `java.time.Clock`.
- **Claims Discipline Standard:**
  - Replaced unmeasured phrases ("evaluates in microseconds", "microsecond p99") in [docs/03-prd.md §5](file:///c:/dev/aegis/docs/03-prd.md), [docs/09-rule-dsl-spec.md §1, §7](file:///c:/dev/aegis/docs/09-rule-dsl-spec.md), and [docs/15-go-to-market.md §2](file:///c:/dev/aegis/docs/15-go-to-market.md) with "measured by benchmark (see doc 11)".
- **Redis Lua Velocity Reference Script (ADR-0005 Appendix A):**
  - Appended the normative sliding-window reference Lua script as Appendix A of [ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md), requiring `TSK-012` to start from it.
  - Recorded Redis 7.0 benchmark validation (100 runs of 50 concurrent calls at limit 5, 0 failures).
  - Documented the ungraceful failover edge condition (state loss prior to replication + `processed_events` constraint protection + offline reconciliation bounding).

---

## [docs-v1.0.1] — 2026-10-22

### Summary
Documentation alignment and verification fixes across Project Aegis specifications and `/tools`. Standardized canonical role model, replaced unenforceable microsecond timeouts with static AST limits and per-ruleset deadlines, softened regulatory and performance claims, cleaned historical stubs, ported verification tooling to Node.js, and standardized document status lines to `Draft-complete`.

### Changed & Fixed
- **Role Alignment:** Reconciled role definitions across docs 01, 03, 08, 10, 18, 19, 20, 21, and `glossary.md` to one canonical 7-role list (`owner`, `admin`, `risk_manager`, `rule_author`, `risk_analyst`, `auditor`, `developer`). Added `CANONICAL_ROLES` enforcement to `tools/check_docs.js` and `tools/check_docs.py`.
- **Evaluation Budget & Static Limits:** Replaced unenforceable 50 µs per-rule JVM timeout (`DR-DSL-04`) with a per-ruleset evaluation budget in milliseconds (`[hypothesis: 10 ms total evaluation deadline, to validate by benchmark]`) backed by primary static complexity limits (max 250 AST nodes per rule, max AST depth 15, RE2/linear regex restrictions, max 250 rules per ruleset) in docs 08, 09, 11, and `decision-register.md`.
- **Claims Discipline & Overclaims Softening:** Reworded `ASM-LEG-02` ("may satisfy; counsel to confirm") and `ASM-LEG-03` ("assessor to determine applicable SAQ/ROC type"). Softened overclaims ("satisfies", "ensures compliance", "guarantees") across `assumptions-register.md`, `decision-register.md` (`DR-SEC-03`, `DR-ADR-01`, `DR-DES-03`), `02-discovery-plan.md`, `07-data-model.md`, `15-go-to-market.md`, `16-case-studies.md`, `adr/0003`, and `adr/0004`.
- **Precursor Stub Cleanup:** Truncated `docs/02-customer-market-discovery-plan.md` to a concise pointer stub referencing authoritative `docs/02-discovery-plan.md`.
- **Tooling Ported to Node.js:** Ported `tools/check_docs.py` and `tools/contrast.py` to Node.js (`tools/check_docs.js`, `tools/contrast.js`) for environments without Python 3 on PATH. Documented execution commands in `README.md`.
- **Color Contrast Figure:** Corrected light-theme primary button contrast ratio figure in doc 10, `DESIGN.md`, and `consistency-report.md` to measured `6.79:1` (white on `#3849D6`).
- **Standardized Status Lines:** Updated all specification status lines across docs, `00-index.md`, `CHANGELOG.md`, and `check_docs` to `Draft-complete (docs v1.0, machine-checked, not yet reviewed)` (formal approval requires human review).

---

## [docs-v1.0] — 2026-10-22

### Summary
Initial authoritative specification baseline for Project Aegis: a production-grade, real-time transaction decision engine and deterministic backtesting platform built for high-throughput payment aggregators and fintechs. Authored as a comprehensive, assumption-based engineering contract for a bootstrapped founder pairing with autonomous AI coding agents.

### Added Specifications
- **Governance & Prime Directives:**
  - [/AGENTS.md](file:///c:/dev/aegis/AGENTS.md): Established the 7 prime directives for AI coding agents, protected documentation invariants (Docs 06, 07, 09), human-only review zones (auth, cryptography, velocity Lua, database migrations), determinism constraints, and Conventional Commits.
  - [docs/00-decisions/decision-register.md](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): Formally resolved all historical open questions into Decided architectural standards or Deferred triggers (Decisions D1–D9).
  - [docs/00-decisions/assumptions-register.md](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md): Formal tracking of 22 product, legal, regulatory, and performance assumptions governed by Validation Gates G1 (pre-customer data) and G2 (pre-commercial contract).
- **Discovery, Product & Pilot Contracts:**
  - [docs/01-production-readiness-discovery.md](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md): Technical definitions of production-readiness, SLO definitions, and the 35-item launch gate checklist (§12).
  - [docs/02-discovery-plan.md](file:///c:/dev/aegis/docs/02-discovery-plan.md): 200-company target list sourcing methodology, cold outreach templates (email, LinkedIn, follow-up), Free Backtest Offer one-pager, and interview logging protocols.
  - [docs/03-prd.md](file:///c:/dev/aegis/docs/03-prd.md): Product Requirements Document v1.0 covering 5 personas, Stage 1 in/out scope, User Stories `US-001` through `US-014`, Tier 0/1 success metrics, and v1.1 review trigger.
  - [docs/04-sow-templates/customer-pilot-sow.md](file:///c:/dev/aegis/docs/04-sow-templates/customer-pilot-sow.md): Design partner shadow pilot statement of work and Master Pilot Agreement outline (§11).
- **Architecture & System Contracts:**
  - [docs/05-architecture/overview.md](file:///c:/dev/aegis/docs/05-architecture/overview.md): Container topology, synchronous/asynchronous/replay paths, and data boundaries.
  - [docs/adr/](file:///c:/dev/aegis/docs/adr/): Architecture Decision Records 0001 through 0008 (Modular Monolith, Durable Persistence, Event-Time Replay, Batched Audit Sealing, Redis Velocity Lua Idempotency, Deployment Tiers, Identity SPI Port, and Dataset Import Pipeline).
  - [docs/06-api-and-event-schemas.md](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md): OpenAPI 3.1 specification, Kafka ingest schemas, and streaming dataset import contracts.
  - [docs/07-data-model.md](file:///c:/dev/aegis/docs/07-data-model.md): PostgreSQL 16 schema, tenant row-level security (RLS), NVMe retention, and S3 cold storage.
  - [docs/08-threat-model.md](file:///c:/dev/aegis/docs/08-threat-model.md): STRIDE threat analysis per container, trust boundaries, abuse cases, and RBAC matrix.
  - [docs/09-rule-dsl-spec.md](file:///c:/dev/aegis/docs/09-rule-dsl-spec.md): Google CEL dialect, sandboxed AST limits ($\le 250$ nodes), and score contribution explainability.
  - [docs/10-design-system-and-ux.md](file:///c:/dev/aegis/docs/10-design-system-and-ux.md): Design tokens, WCAG 2.1 AA/AAA contrast verification, Compact (28px) vs. Comfortable (44px) density modes, keyboard shortcuts (`j`/`k`/`a`/`d`), and wireframes.
  - [docs/11-test-and-performance-strategy.md](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md): Test pyramid, Testcontainers harnesses, 50-thread concurrency tests, and velocity equivalence tests.
- **Operations, Compliance & Infrastructure:**
  - [docs/12-slos-runbooks-dr.md](file:///c:/dev/aegis/docs/12-slos-runbooks-dr.md): Operational SLOs, alert runbooks, RPO 5m / RTO 30m disaster recovery, and game-day chaos drills.
  - [docs/13-compliance-posture.md](file:///c:/dev/aegis/docs/13-compliance-posture.md): Non-legal control mapping designed to align with PCI DSS, DPDP Act 2023, and RBI localisation; formal Legal Review Brief for counsel at Gate G1 (§4).
  - [docs/14-deployment-and-iac.md](file:///c:/dev/aegis/docs/14-deployment-and-iac.md): Multi-tier infrastructure definitions (Tier 0 local, Tier 1 pilot shadow mode, Tier 2 enterprise multi-AZ) and Terraform layout for AWS Mumbai (`ap-south-1`).
- **Commercial Go-To-Market & Proofs:**
  - [docs/15-go-to-market.md](file:///c:/dev/aegis/docs/15-go-to-market.md): Direct sales motion without paid ads, Free Backtest wedge, Design Partner program terms, DPA and Security Whitepaper outlines, sales metrics, and objection handling.
  - [docs/16-case-studies.md](file:///c:/dev/aegis/docs/16-case-studies.md): Strict claims discipline proofs (concurrency limit bypass, duplicate Kafka delivery, deterministic replay) and pilot study templates with customer anonymization rules.
- **Software Design & Engineering Backlog:**
  - [docs/17-backend-design.md](file:///c:/dev/aegis/docs/17-backend-design.md): Gradle 10-module layout, Java 21 SPI ports (`EvaluationCore`, `VelocityStore`, `Clock`, `RulesetRepository`, `DecisionRepository`, `AuditSealer`, `IdentityProvider`, `DatasetImporter`), RFC 7807 error model, Flyway migrations `V1` to `V9`, and sequence diagrams.
  - [docs/18-frontend-architecture.md](file:///c:/dev/aegis/docs/18-frontend-architecture.md): React 18+ SPA architecture, role routing, state boundaries, generated OpenAPI client, SSE live telemetry with 2,000-event ring buffer, and complete screen-to-endpoint mapping.
  - [docs/19-identity-and-auth-spec.md](file:///c:/dev/aegis/docs/19-identity-and-auth-spec.md): Authentication state machines, `V9__init_auth_and_sessions.sql` DDL, Argon2id parameters, RFC 6238 TOTP, rate limits, `LocalMailboxNotificationService`, and Tier 2 SSO extension.
  - [docs/20-build-backlog.md](file:///c:/dev/aegis/docs/20-build-backlog.md): Milestones M0 to M9 (M1 vertical slice, M9 pilot readiness), 46 story-point-estimated tasks (214 points total), Definition of Done, and the First 10 Tasks.
  - [docs/21-local-dev-environment.md](file:///c:/dev/aegis/docs/21-local-dev-environment.md): Tier 0 Docker Compose topology with profiles (`core`, `observability`, `tools`), memory limits capped at $\le 3,072\text{ MB}$ within the $\le 3.89\text{ GB}$ budget on measured 7.79 GB hardware, startup ordering, and seed data.
  - [docs/22-product-roadmap.md](file:///c:/dev/aegis/docs/22-product-roadmap.md): Stages 0 to 4 evolution (Foundation, Wedge, Operations, Adjacencies, Platform) with entry prerequisites, metrics, and risks.
- **Auditing & Tooling:**
  - [tools/check_docs.py](file:///c:/dev/aegis/tools/check_docs.py): Automated documentation and architectural integrity test suite verifying relative links, status lines, resolved open questions, index coverage, canonical glossary enums, compliance phrasing discipline, and assumptions register fields.
  - [docs/audit/readiness-report.md](file:///c:/dev/aegis/docs/audit/readiness-report.md): Formal Build Readiness (7/7 passed) and Pilot Readiness (Gates G1/G2 tracked) evaluation.

### Changed & Deprecated
- Marked [docs/04-sow-templates/internal-build-sow.md](file:///c:/dev/aegis/docs/04-sow-templates/internal-build-sow.md) as formally **SUPERSEDED** by Document 20.
- Consolidated [docs/02-customer-market-discovery-plan.md](file:///c:/dev/aegis/docs/02-customer-market-discovery-plan.md) into Document 02.
- Updated all core specification status lines to `Approved (docs v1.0, assumption-based)`.
