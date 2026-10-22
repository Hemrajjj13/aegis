# Engineering Build Backlog and Milestone Specification

**Document:** 20  
**Status:** Draft-complete (docs v1.0, machine-checked, not yet reviewed)
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

---

## 1. Purpose and Engineering Methodology

This document defines the complete product build backlog for Project Aegis from inception to Pilot Readiness (Gate G1 / Stage 1 Exit). It establishes the milestone hierarchy, epic decomposition, estimation framework, Definition of Done (DoD), and execution sequence for a bootstrapped single founder pairing with AI coding agents.

### 1.1 Methodology and Backlog Principles
1. **Estimation in Story Points, Never Calendar Dates:** All tasks are estimated using a modified Fibonacci scale ($1, 2, 3, 5, 8, 13\text{ points}$). Calendar dates are excluded; milestones are gated strictly on automated verification, test suites, and operational criteria.
2. **Every Task Traced to Specification:** Every epic and task explicitly cross-references corresponding sections in backend architecture ([Doc 17](file:///c:/dev/aegis/docs/17-backend-design.md)), API schemas ([Doc 06](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md)), data model ([Doc 07](file:///c:/dev/aegis/docs/07-data-model.md)), security/threats ([Doc 08](file:///c:/dev/aegis/docs/08-threat-model.md)), test strategy ([Doc 11](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md)), and identity ([Doc 19](file:///c:/dev/aegis/docs/19-identity-and-auth-spec.md)).
3. **M1 Vertical Slice First:** Rather than building bottom-up infrastructure in isolation, Milestone M1 forces an end-to-end vertical slice: one live HTTP transaction ingested, evaluated against a stub rule, written to PostgreSQL, and displayed in real-time on the console.
4. **Agent-Executable Atomic Tasks:** Tasks are sized to be autonomously executable by an AI coding agent operating under the constraints of [/AGENTS.md](file:///c:/dev/aegis/AGENTS.md).

---

## 2. Definition of Done (DoD)

A task, story, or milestone is marked **DONE** if and only if all of the following conditions are satisfied:
1. **Zero Production Code Without Tests:** Unit test coverage $\ge 85\%$ for domain logic (`aegis-core`). All database, Redis, and Kafka interactions validated using Testcontainers integration tests.
2. **Determinism Invariant Satisfied:** Zero invocations of `System.currentTimeMillis()`, `Instant.now()`, or `new Random()` within `aegis-core`. All time operations derive strictly from the `com.aegis.core.Clock` SPI.
3. **No Flaky Tests:** Test suite passes 5 consecutive runs in CI without failures.
4. **Static Analysis & Linting:** 
   - Backend: SpotBugs clean, Checkstyle clean, zero compiler warnings with `-Werror`.
   - Frontend: ESLint clean, TypeScript compiler (`tsc --noEmit`) passes with zero errors, Axe accessibility audit reports zero WCAG 2.1 AA violations.
5. **No Regressions Against Architecture Boundaries:** Module dependencies adhere strictly to the acyclic dependency graph in [Document 17 §2](file:///c:/dev/aegis/docs/17-backend-design.md). No presentation or persistence leakage into `aegis-core`.
6. **Documentation & Schema Invariant:** OpenAPI specifications ([Doc 06](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md)) and Flyway SQL migrations ([Doc 17 §5](file:///c:/dev/aegis/docs/17-backend-design.md)) match running code byte-for-byte.

---

## 3. Milestone Overview (M0 to M9)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Project Aegis Milestones                        │
├────────────────────────────────────────────────────────────────────────┤
│ M0: Scaffolding, Build Topology & Tier 0 Compose Setup (13 pts)        │
│ M1: End-to-End Vertical Slice (One In, One Out, One Seen) (21 pts)     │
│ M2: Evaluation Core, CEL Engine & Deterministic Clock (21 pts)         │
│ M3: Velocity Store, Redis Lua & Concurrency Controls (34 pts)          │
│ M4: Rule Management, Versioning & Maker-Checker Workflow (21 pts)      │
│ M5: Deterministic Replay Engine & Shift Matrix (34 pts)                │
│ M6: Dataset Importer & Free Backtest Comparison Report (21 pts)        │
│ M7: Console UI, Virtualized Tables & SSE Live Telemetry (34 pts)       │
│ M8: Identity Subsystem, Argon2id, TOTP & RBAC Guard (21 pts)           │
│ M9: Hardening, Load/Chaos Verification & Pilot Readiness (34 pts)      │
└────────────────────────────────────────────────────────────────────────┘
Total Backlog Points: 214 Story Points
```

---

## 4. Detailed Milestone and Story Breakdown

### Milestone M0: Scaffolding and Infrastructure Topology (13 Points)
- **Goal:** Establish a clean, reproducible multi-module Java 21 / Spring Boot 3.3 repository and Tier 0 Docker Compose topology respecting the $\le 3.89\text{ GB}$ memory budget.
- **Stories:**
  - `TSK-001` (3 pts): Multi-module Gradle build scaffolding with `aegis-core`, `aegis-velocity`, `aegis-rules`, `aegis-decision-api`, `aegis-console-api`, `aegis-identity`, `aegis-replay`, `aegis-audit`, `aegis-dataset-importer`, `apps/console`. Ref: [Doc 17 §2](file:///c:/dev/aegis/docs/17-backend-design.md).
  - `TSK-002` (5 pts): Tier 0 Docker Compose setup with resource limits (PostgreSQL 512MB, Redis 256MB, Kafka KRaft 768MB) and healthy startup ordering. Ref: [Doc 21 §3](file:///c:/dev/aegis/docs/21-local-dev-environment.md).
  - `TSK-003` (5 pts): Reusable Testcontainers base classes (`PostgresIntegrationTest`, `RedisIntegrationTest`, `KafkaIntegrationTest`) with schema bootstrapping. Ref: [Doc 11 §3](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md).

---

### Milestone M1: Vertical Slice (21 Points)
- **Goal:** One transaction ingested via HTTP POST, evaluated against a static stub rule, decision row committed to PostgreSQL with RLS context, and audit row rendered in real-time on a minimal React console.
- **Stories:**
  - `TSK-004` (5 pts): `POST /v1/decisions` endpoint skeleton in `aegis-decision-api` with RFC 7807 error handler, tenant UUID header parsing, and PAN pattern rejection filter. Ref: [Doc 06 §3.1](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md), [Doc 17 §4](file:///c:/dev/aegis/docs/17-backend-design.md).
  - `TSK-005` (5 pts): Flyway `V1__init_tenants_and_core.sql` and `V3__init_decisions_and_audit.sql` schema application. Ref: [Doc 17 §5](file:///c:/dev/aegis/docs/17-backend-design.md).
  - `TSK-006` (5 pts): Synchronous decision persistence pipeline via `DecisionRepository` with row-level tenant isolation assertion. Ref: [Doc 07 §2.1](file:///c:/dev/aegis/docs/07-data-model.md), [Doc 17 §3.5](file:///c:/dev/aegis/docs/17-backend-design.md).
  - `TSK-007` (3 pts): SSE endpoint `GET /v1/events/stream` emitting decision events to downstream listeners. Ref: [Doc 06 §3.5](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md).
  - `TSK-008` (3 pts): Minimal React console displaying live decisions table connected to SSE endpoint. Ref: [Doc 18 §6](file:///c:/dev/aegis/docs/18-frontend-architecture.md).

---

### Milestone M2: Evaluation Core and Zero Wall-Clock Engine (21 Points)
- **Goal:** Build the high-performance, deterministic evaluation kernel in pure Java 21 with zero runtime external I/O.
- **Stories:**
  - `TSK-009` (5 pts): Implement `com.aegis.core.Clock` SPI with fixed-instant `DeterministicClock` and wall-clock wrapper. Unit test asserting zero non-derivable time paths. Ref: [Doc 17 §3.3](file:///c:/dev/aegis/docs/17-backend-design.md).
  - `TSK-010` (8 pts): Integrate Google Common Expression Language (`cel-java`) parser and compiler. Implement AST complexity validator enforcing max 250 AST nodes and regex execution limits. Ref: [Doc 09 §3](file:///c:/dev/aegis/docs/09-rule-dsl-spec.md).
  - `TSK-011` (8 pts): Implement `DefaultEvaluationCore` executing hard rules (overrides) and soft rules (scoring accumulator). Enforce score scale 0.0 to 100.0 with Review $\ge 40.0$ and Decline $\ge 75.0$. Ref: [Doc 17 §3.1](file:///c:/dev/aegis/docs/17-backend-design.md).

---

### Milestone M3: Velocity Store, Redis Lua, and Concurrency Controls (34 Points)
- **Goal:** Implement the atomic check-and-increment velocity tracking engine defending against concurrent limit bypass and duplicate Kafka deliveries.
- **Stories:**
  - `TSK-012` (8 pts): Author atomic Redis Lua script (`velocity_sliding_window.lua`) using sorted sets (`ZADD`, `ZREMRANGEBYSCORE`, `ZCARD`) with `event_id` idempotency check. Ref: [ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md).
  - `TSK-013` (5 pts): Implement `RedisVelocityStore` adapter in `aegis-velocity` calling SHA-cached Lua script with fallback recompilation. Ref: [Doc 17 §3.2](file:///c:/dev/aegis/docs/17-backend-design.md).
  - `TSK-014` (8 pts): Concurrency integration test (`VelocityLimitConcurrencyTest`): Assert that when $N=50$ parallel threads submit identical account transactions against limit $L=5$, exactly 5 approve and 45 decline. Ref: [Doc 11 §4.1](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md), [Doc 16 §3](file:///c:/dev/aegis/docs/16-case-studies.md).
  - `TSK-015` (8 pts): Implement Kafka async transaction mirror consumer with partition key `tenant_id:account_id`. Assert duplicate delivery of identical `event_id` yields exactly one decision row. Ref: [Doc 06 §4.1](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md), [Doc 16 §4](file:///c:/dev/aegis/docs/16-case-studies.md).
  - `TSK-016` (5 pts): Offline Redis-to-PostgreSQL velocity drift reconciliation scheduled job emitting telemetry alerts upon mismatch. Ref: [Doc 05 §4](file:///c:/dev/aegis/docs/05-architecture/overview.md).

---

### Milestone M4: Rule Management and Maker-Checker Workflow (21 Points)
- **Goal:** Secure rule publishing lifecycle preventing unauthorized rule injection into production.
- **Stories:**
  - `TSK-017` (5 pts): Flyway `V2__init_rulesets.sql` schema and JPA entity mappings with immutable version hashes. Ref: [Doc 07 §2.2](file:///c:/dev/aegis/docs/07-data-model.md).
  - `TSK-018` (8 pts): Maker-checker state machine enforcement in `aegis-rules`: Author submitting rule (role `rule_author` or `risk_manager`) cannot approve rule; promotion requires approval from distinct `risk_manager`, `admin`, or `owner`. Ref: [Doc 01 §11.2](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md), [Doc 08 §4.5](file:///c:/dev/aegis/docs/08-threat-model.md).
  - `TSK-019` (5 pts): Draft simulation endpoint `POST /v1/rulesets/simulate` testing draft expressions against synthetic memory events without committing state. Ref: [Doc 06 §3.3](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md).
  - `TSK-020` (3 pts): Instant rollback endpoint `POST /v1/rulesets/{id}/rollback` restoring previous active ruleset within sub-second latency. Ref: [Doc 01 §11.2](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md).

---

### Milestone M5: Deterministic Replay Engine (34 Points)
- **Goal:** Replay engine enabling risk teams to evaluate candidate rulesets against historical datasets with guaranteed determinism.
- **Stories:**
  - `TSK-021` (8 pts): Implement `InMemoryReplayVelocityStore` utilizing Java `ConcurrentSkipListSet` maintaining strict event-time sorted ordering without wall-clock drift. Ref: [ADR-0003](file:///c:/dev/aegis/docs/adr/0003-event-time-for-deterministic-replay.md).
  - `TSK-022` (8 pts): Implement `VelocityImplementationEquivalenceTest` asserting that Lua Redis velocity and Java replay velocity return identical verdicts on randomized event sequences. Ref: [ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md), [Doc 11 §4.3](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md).
  - `TSK-023` (8 pts): Parallel replay coordinator processing batch chunks with zero writes to live production tables (`replay_mode = true`). Ref: [Doc 17 §3.6](file:///c:/dev/aegis/docs/17-backend-design.md).
  - `TSK-024` (5 pts): Replay comparison report generator computing decision shift matrix (Approve $\rightarrow$ Decline, Decline $\rightarrow$ Approve) and latency deltas. Ref: [Doc 06 §3.4](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md).
  - `TSK-025` (5 pts): Deterministic replay test asserting identical results and byte-level diff hashes on two separate executions of the same dataset $\times$ ruleset. Ref: [Doc 16 §5](file:///c:/dev/aegis/docs/16-case-studies.md).

---

### Milestone M6: Dataset Importer and Free Backtest Report (Decision D8) (21 Points)
- **Goal:** Build self-service data ingestion and executive backtest reporting to drive the zero-cost GTM wedge.
- **Stories:**
  - `TSK-026` (8 pts): Implement streaming `DatasetImporter` in `aegis-dataset-importer` ingesting CSV and JSONL files up to 100,000 rows without heap exhaustion. Enforce zero PAN storage invariant. Ref: [Doc 17 §3.8](file:///c:/dev/aegis/docs/17-backend-design.md).
  - `TSK-027` (5 pts): Importer REST endpoint `POST /v1/datasets/import` with async progress tracker (`GET /v1/datasets/import/{id}/status`). Ref: [Doc 18 §9](file:///c:/dev/aegis/docs/18-frontend-architecture.md).
  - `TSK-028` (8 pts): Executive Backtest Report export generator (`POST /v1/datasets/{id}/export-pdf` and `GET .../backtest-report`) rendering confusion matrix, caught fraud, reduced false alarms, and estimated INR savings. Ref: [Doc 03 §5.6](file:///c:/dev/aegis/docs/03-prd.md), [Doc 15 §2.2](file:///c:/dev/aegis/docs/15-go-to-market.md).

---

### Milestone M7: Console UI and Real-Time Telemetry (34 Points)
- **Goal:** Build the React/Vite web application providing risk analysts and managers high-efficiency operational tools.
- **Stories:**
  - `TSK-029` (5 pts): Generate TypeScript API client from OpenAPI spec via `openapi-typescript-codegen` with Axios interceptors for JWT and Tenant ID. Ref: [Doc 18 §5](file:///c:/dev/aegis/docs/18-frontend-architecture.md).
  - `TSK-030` (8 pts): Implement `@tanstack/react-virtual` table supporting Compact (28px) and Comfortable (44px) density modes and sub-16ms scrolling. Ref: [Doc 10 §3](file:///c:/dev/aegis/docs/10-design-system-and-ux.md), [Doc 18 §7.1](file:///c:/dev/aegis/docs/18-frontend-architecture.md).
  - `TSK-031` (8 pts): Build Live Decisions feed (`/decisions`) consuming SSE stream with 2,000-event ring buffer and `Space` pause/resume accumulator. Ref: [Doc 18 §6](file:///c:/dev/aegis/docs/18-frontend-architecture.md).
  - `TSK-032` (5 pts): Case Review queue (`/cases`) and slide-over detail drawer with keyboard triage shortcuts (`j`, `k`, `a`, `d`, `r`). Ref: [Doc 10 §5](file:///c:/dev/aegis/docs/10-design-system-and-ux.md), [Doc 18 §9](file:///c:/dev/aegis/docs/18-frontend-architecture.md).
  - `TSK-033` (5 pts): Monaco CEL Rule Editor with custom auto-complete, Web Worker AST validation squiggles, and diff viewer. Ref: [Doc 10 §4](file:///c:/dev/aegis/docs/10-design-system-and-ux.md), [Doc 18 §7.2](file:///c:/dev/aegis/docs/18-frontend-architecture.md).
  - `TSK-034` (3 pts): Playwright E2E test verifying end-to-end maker-checker approval flow (`rule_author` submits, distinct `risk_manager` approves) and keyboard case triage. Ref: [Doc 18 §10](file:///c:/dev/aegis/docs/18-frontend-architecture.md).

---

### Milestone M8: Identity Subsystem, Credentials, and RBAC (21 Points)
- **Goal:** Implement the pluggable in-house identity subsystem for Tier 0 and Tier 1 without third-party SaaS dependencies.
- **Stories:**
  - `TSK-035` (5 pts): Flyway `V9__init_auth_and_sessions.sql` schema and entities for users, credentials, TOTP secrets, invites, and sessions, supporting the seven canonical roles (`owner`, `admin`, `risk_manager`, `rule_author`, `risk_analyst`, `auditor`, `developer`). Ref: [Doc 19 §2](file:///c:/dev/aegis/docs/19-identity-and-auth-spec.md).
  - `TSK-036` (5 pts): Implement `InProcessSpringSecurityIdentityProvider` with Argon2id password hashing, rotating refresh tokens, and 15-minute JWT issuance. Ref: [Doc 19 §4](file:///c:/dev/aegis/docs/19-identity-and-auth-spec.md), [ADR-0007](file:///c:/dev/aegis/docs/adr/0007-identity-behind-a-port.md).
  - `TSK-037` (5 pts): RFC 6238 TOTP enrollment and challenge verification with encrypted secret storage (AES-256-GCM). Ref: [Doc 19 §3.3](file:///c:/dev/aegis/docs/19-identity-and-auth-spec.md).
  - `TSK-038` (3 pts): Implement `LocalMailboxNotificationService` writing signup OTPs and invite tokens to stdout/log for Tier 0/1. Ref: [Doc 19 §6](file:///c:/dev/aegis/docs/19-identity-and-auth-spec.md).
  - `TSK-039` (3 pts): Account lockout integration test (`AuthenticationBruteForceTest`): Assert 5 failed logins triggers 30-minute lockout. Ref: [Doc 08 §4.4](file:///c:/dev/aegis/docs/08-threat-model.md).

---

### Milestone M9: Pilot Readiness, Hardening, and Gate G1 Prerequisites (34 Points)
- **Goal:** Complete all reliability, security, correctness, and legal prerequisites defined in Document 01 §12 to approve Tier 1 shadow mode deployment.
- **Stories:**
  - `TSK-040` (8 pts): **Reliability Load Verification:** Execute Gatling / k6 load test asserting Tier 1 burst capacity at 1,000 req/s with server p99 latency $\le 100\text{ ms}$. Ref: [Doc 01 §12](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md), [Doc 11 §5](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md).
  - `TSK-041` (5 pts): **Chaos Injection Test:** Execute Redis primary failover and Kafka broker termination drills; assert zero duplicate velocity counts and consumer lag recovery under 5 seconds. Ref: [Doc 01 §12](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md), [Doc 12 §5](file:///c:/dev/aegis/docs/12-slos-runbooks-dr.md).
  - `TSK-042` (5 pts): **Audit Batch Sealer Verification:** Test `AuditSealer` running against 10,000 synthetic transactions; assert SHA-256 Merkle root verification passes against external cryptographic audit tool. Ref: [Doc 01 §12](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md), [ADR-0004](file:///c:/dev/aegis/docs/adr/0004-batched-audit-log-hash-chaining.md).
  - `TSK-043` (5 pts): **PAN Invariant Verification Test:** Assert that: (a) typed schema rejects card numbers in integer fields; (b) free-text field containing Luhn-valid PAN is rejected with HTTP 400; (c) known false-positive string disposition is documented. Ref: [Doc 01 §12](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md), [Doc 13 §2.1](file:///c:/dev/aegis/docs/13-compliance-posture.md).
  - `TSK-044` (5 pts): **Tier 1 Terraform Clean-Room Deployment:** Execute automated Terraform provisioning of Tier 1 single-AZ AWS Mumbai (`ap-south-1`) shadow mode footprint; assert healthcheck passes within 15 minutes. Ref: [Doc 14 §3](file:///c:/dev/aegis/docs/14-deployment-and-iac.md).
  - `TSK-045` (3 pts): **Gate G1 Legal Review Sign-Off:** Execute Legal Review Brief with qualified Indian legal counsel covering DPDP Act Data Processor role, RBI data localisation, and Bilateral DPA template. Ref: [Doc 13 §4](file:///c:/dev/aegis/docs/13-compliance-posture.md), [Doc 15 §5](file:///c:/dev/aegis/docs/15-go-to-market.md).
  - `TSK-046` (3 pts): **Gate G1 Customer Discovery Milestone:** Conduct and synthesize $\ge 10$ customer discovery interviews logged in `docs/02-discovery-plan.md` to formally baseline v1.1 PRD requirements. Ref: [Doc 02 §8](file:///c:/dev/aegis/docs/02-discovery-plan.md), [Doc 03 §8](file:///c:/dev/aegis/docs/03-prd.md).

---

## 5. The First 10 Tasks (Immediate Implementation Order)

To initiate code construction systematically without architectural deadlocks, an AI coding agent must execute the following 10 tasks in exact numerical sequence:

1. **`TSK-001`: Root Build Configuration and Gradle Multi-Module Scaffolding**
   - Action: Create root `settings.gradle.kts` and `build.gradle.kts` declaring Java 21 toolchain, Spring Boot 3.3.x plugin, and submodules: `aegis-core`, `aegis-velocity`, `aegis-rules`, `aegis-decision-api`, `aegis-console-api`, `aegis-identity`, `aegis-replay`, `aegis-audit`, `aegis-dataset-importer`.
   - Verification: Run `./gradlew projects` and assert all modules resolve cleanly.
2. **`TSK-002`: Tier 0 Docker Compose Topology Setup**
   - Action: Create `docker-compose.yml` declaring `postgres:16-alpine`, `redis:7-alpine`, and `confluentinc/cp-kafka:7.6.0` (KRaft mode) with memory budgets ($\le 1.54\text{ GB}$ total infrastructure).
   - Verification: Run `docker compose up -d` and assert containers reach healthy state via Docker healthchecks.
3. **`TSK-003`: Base Testcontainers Integration Harness**
   - Action: Implement abstract integration test classes in `aegis-core` and `aegis-decision-api` that spin up shared Testcontainers instances for Postgres, Redis, and Kafka.
   - Verification: Write smoke test `ContainerBootstrapTest` verifying connectivity to all three services.
4. **`TSK-009`: Deterministic Clock SPI and Core Domain Models**
   - Action: Implement `com.aegis.core.Clock` interface and `DeterministicClock` implementation. Define immutable `PaymentEvent`, `EvaluationResult`, and `RuleOutcome` records in `aegis-core`.
   - Verification: Unit test asserting exact microsecond stepping on `DeterministicClock.advance()`.
5. **`TSK-010`: CEL AST Parser and Complexity Validator**
   - Action: Add `cel-java` dependency to `aegis-core`. Implement `CelRuleCompiler` with AST visitor asserting node count $\le 250$ and banning unapproved regex functions.
   - Verification: Test asserting valid CEL compiles and complex nested AST ($> 250$ nodes) throws `RuleComplexityExceededException`.
6. **`TSK-011`: Evaluation Core Accumulator Engine**
   - Action: Implement `DefaultEvaluationCore` evaluating compiled CEL rules against `PaymentEvent`. Calculate soft score sums (0.0 to 100.0) and hard overrides.
   - Verification: Unit test asserting correct disposition: Score 35 $\rightarrow$ `APPROVE`, Score 55 $\rightarrow$ `REVIEW`, Score 80 $\rightarrow$ `DECLINE`.
7. **`TSK-012`: Redis Velocity Sliding Window Lua Script**
   - Action: Create `velocity_sliding_window.lua` in `aegis-velocity/src/main/resources/lua/` with `event_id` membership idempotency and sliding window eviction.
   - Verification: Test against local Redis verifying duplicate `event_id` execution returns previous count without double-incrementing.
8. **`TSK-014`: Velocity Limit Concurrency Test (`VelocityLimitConcurrencyTest`)**
   - Action: Build multi-threaded Testcontainers test firing $N=50$ concurrent requests at velocity counter with limit $L=5$.
   - Verification: Assert `countPass == 5` and `countDecline == 45` across 100 iterations.
9. **`TSK-005`: Core Flyway Database Migrations (`V1` to `V3`)**
   - Action: Write `V1__init_tenants_and_core.sql`, `V2__init_rulesets.sql`, and `V3__init_decisions_and_audit.sql` Flyway scripts with Row-Level Security policies.
   - Verification: Run `./gradlew :aegis-decision-api:flywayMigrate` against Testcontainers Postgres and assert tables exist.
10. **`TSK-004`: `POST /v1/decisions` Synchronous Ingestion Endpoint (Vertical Slice)**
    - Action: Implement Spring Web controller in `aegis-decision-api` parsing incoming JSON, passing to `DefaultEvaluationCore`, persisting decision row, and returning 200 OK.
    - Verification: End-to-end integration test posting JSON transaction and asserting HTTP 200 response with latency $< 20\text{ ms}$ and decision stored in Postgres.
