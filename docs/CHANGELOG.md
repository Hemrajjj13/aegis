# Project Aegis Documentation Suite — Changelog

**Status:** Approved (docs v1.0, assumption-based)  
**Maintained by:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last Updated:** October 2026  

All releases and architectural specification increments follow Semantic Versioning (`vMAJOR.MINOR.PATCH`).

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
