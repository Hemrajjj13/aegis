# Documentation Index — Project Aegis

**Document:** 00  
**Status:** Approved (docs v1.0, assumption-based)
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](./00-decisions/decision-register.md))  
**Last updated:** October 2026  

---

## 1. Purpose

This index is the single entry point for all Project Aegis engineering, architectural, operational, and commercial specifications. It maps every document to the operational question it answers, its current status, and the gating prerequisites required before treating it as authoritative.

---

## 2. Core Decisions, Assumptions, Governance, and Agent Rules

| Document ID | File Path | Question It Answers | Status | Governing Gate / Rule |
|---|---|---|---|---|
| **AGENTS** | [../AGENTS.md](../AGENTS.md) | What are the mandatory rules, prime directives, and review zones for AI coding agents? | Approved | Laws 1–7, TDD, Human-Only Zones |
| **DR** | [00-decisions/decision-register.md](./00-decisions/decision-register.md) | How are architectural and operational open questions definitively resolved? | Active | Decision D3 |
| **ASM** | [00-decisions/assumptions-register.md](./00-decisions/assumptions-register.md) | What product, commercial, and regulatory hypotheses exist and how are they validated? | Active | Gate G1 / G2 (Decision D2) |
| **AUD** | [audit/consistency-report.md](./audit/consistency-report.md) | How are terminology, enums, scales, memory budgets, and claims verified across docs? | Active | Living Consistency Audit |

---

## 3. Wave 1: Discovery, Requirements, and Commercial Foundations

| Document ID | File Path | Question It Answers | Status | Governing Gate / Prerequisite |
|---|---|---|---|---|
| **01** | [01-production-readiness-discovery.md](./01-production-readiness-discovery.md) | What does "production-ready" mean for this real-time payment decision engine? | Approved | Launch Gate Checklist §12 |
| **02** | [02-discovery-plan.md](./02-discovery-plan.md) | How is the 200-company target list built, outreach executed, and interviews validated? | Approved | 10+ Logged Interviews (Gate G1) |
| **02-pre** | [02-customer-market-discovery-plan.md](./02-customer-market-discovery-plan.md) | Precursor customer discovery outline *(Consolidated into Doc 02)* | Archived | Precursor Outline |
| **03** | [03-prd.md](./03-prd.md) | What does Stage 1 ship, to whom, measured how, and what acceptance criteria apply? | Approved (v1.0) | v1.1 due post-10 interviews (Gate G1) |
| **04a** | [04-sow-templates/internal-build-sow.md](./04-sow-templates/internal-build-sow.md) | What was the internal build contract? *(Superseded by Doc 20)* | **Superseded** | Mapped to Document 20 Backlog |
| **04b** | [04-sow-templates/customer-pilot-sow.md](./04-sow-templates/customer-pilot-sow.md) | What do a customer pilot SOW and Master Pilot Agreement legally commit to? | Approved Template | Legal Review Brief (Gate G1 / G2) |

---

## 4. Wave 2: Architecture, Technical Design, and Quality Strategy

| Document ID | File Path | Question It Answers | Status | Governing Gate / Prerequisite |
|---|---|---|---|---|
| **05** | [05-architecture/overview.md](./05-architecture/overview.md) | What are the major components, boundaries, and runtime interactions? | Approved | Modular Monolith (ADR-0001) |
| **ADR-0001** | [adr/0001-modular-monolith-first.md](./adr/0001-modular-monolith-first.md) | Why build a modular monolith before microservices? | Approved | Decision D1 |
| **ADR-0002** | [adr/0002-durable-decision-write-before-response.md](./adr/0002-durable-decision-write-before-response.md) | Why write the decision to Postgres before returning HTTP 200 to caller? | Approved | Synchronous Persistence Invariant |
| **ADR-0003** | [adr/0003-event-time-for-deterministic-replay.md](./adr/0003-event-time-for-deterministic-replay.md) | Why enforce event time, not wall-clock time, in the replay engine? | Approved | Deterministic Replay Invariant |
| **ADR-0004** | [adr/0004-batched-audit-log-hash-chaining.md](./adr/0004-batched-audit-log-hash-chaining.md) | Why batch audit-log Merkle hash-chaining instead of serialising per tenant? | Approved | High Throughput Audit Sealing |
| **ADR-0005** | [adr/0005-velocity-script-event-id-idempotency.md](./adr/0005-velocity-script-event-id-idempotency.md) | How do Redis velocity Lua and Java replay achieve counter idempotency? | Approved | Concurrency & Equivalence CI Gate |
| **ADR-0006** | [adr/0006-deployment-tiers.md](./adr/0006-deployment-tiers.md) | How do deployment tiers (Tier 0, 1, 2) operate from a unified codebase? | Approved | Decision D1, D6, D7 |
| **ADR-0007** | [adr/0007-identity-behind-a-port.md](./adr/0007-identity-behind-a-port.md) | How is authentication/authorisation abstracted across deployment tiers? | Approved | Decision D4 |
| **ADR-0008** | [adr/0008-dataset-import-for-backtests.md](./adr/0008-dataset-import-for-backtests.md) | How does the dataset import pipeline enable free backtests safely? | Approved | Decision D8 |
| **06** | [06-api-and-event-schemas.md](./06-api-and-event-schemas.md) | What are the exact OpenAPI 3.1 REST schemas and Kafka event contracts? | Approved | OpenAPI Contract / Protected Doc |
| **07** | [07-data-model.md](./07-data-model.md) | Which PostgreSQL tables exist, how is PII isolated, and what is retained? | Approved | Privacy Review / Protected Doc |
| **08** | [08-threat-model.md](./08-threat-model.md) | What are the STRIDE attack vectors, abuse cases, and security mitigations? | Approved | Security Sign-off |
| **09** | [09-rule-dsl-spec.md](./09-rule-dsl-spec.md) | How are CEL rules written, versioned, sandboxed, and explained? | Approved | AST Complexity Limit / Protected Doc |
| **10** | [10-design-system-and-ux.md](./10-design-system-and-ux.md) | What are the visual tokens, typography, density modes, and text wireframes? | Approved | WCAG 2.1 AA/AAA Verified |
| **11** | [11-test-and-performance-strategy.md](./11-test-and-performance-strategy.md) | How are concurrency limits, idempotency, and speed verified in CI? | Approved | Automated CI Testcontainers Gates |

---

## 5. Wave 3: Operations, Compliance, and Backend/Frontend Engineering

| Document ID | File Path | Question It Answers | Status | Governing Gate / Prerequisite |
|---|---|---|---|---|
| **12** | [12-slos-runbooks-dr.md](./12-slos-runbooks-dr.md) | How does the platform stay up, handle failover, and recover from disaster? | Approved | Game-Day Chaos Verification |
| **13** | [13-compliance-posture.md](./13-compliance-posture.md) | How does the architecture align with PCI DSS, DPDP Act 2023, and RBI rules? | Approved | Legal Review Brief (Gate G1) |
| **14** | [14-deployment-and-iac.md](./14-deployment-and-iac.md) | How is the infrastructure provisioned across Tiers 0, 1, and 2 via Terraform? | Approved | Clean-Room Deploy in AWS Mumbai |
| **17** | [17-backend-design.md](./17-backend-design.md) | What is the Gradle multi-module layout, Java 21 SPI ports, and Flyway order? | Approved | Backend Implementation Standard |
| **18** | [18-frontend-architecture.md](./18-frontend-architecture.md) | What is the React app structure, role routing, SSE feed, and screen mapping? | Approved | Frontend Implementation Standard |
| **19** | [19-identity-and-auth-spec.md](./19-identity-and-auth-spec.md) | What are the exact auth state machines, OTP/token rules, and DDL tables? | Approved | In-House Identity & RBAC |

---

## 6. Wave 4: Commercial Go-To-Market, Case Studies, and Product Execution

| Document ID | File Path | Question It Answers | Status | Governing Gate / Prerequisite |
|---|---|---|---|---|
| **15** | [15-go-to-market.md](./15-go-to-market.md) | How does Aegis acquire and close design partners without paid ad spend? | Approved | Free Backtest Wedge / Gate G1 |
| **16** | [16-case-studies.md](./16-case-studies.md) | What are the measured engineering content proofs and pilot study templates? | Approved | Measured Hardware Benchmarks (D9) |
| **20** | [20-build-backlog.md](./20-build-backlog.md) | What are the milestones (M0–M9), user stories, story points, and DoD? | Approved | Definition of Done / Backlog |
| **21** | [21-local-dev-environment.md](./21-local-dev-environment.md) | How do Compose profiles run within $\le 3.89$ GB RAM on 7.79 GB hardware? | Approved | Tier 0 Docker Compose Topology |
| **22** | [22-product-roadmap.md](./22-product-roadmap.md) | What are Stages 0 to 4, entry prerequisites, metrics, and exit criteria? | Approved | Multi-Year Product Evolution |

---

## 7. Reference and Standards

| File Path | Description |
|---|---|
| [glossary.md](./glossary.md) | Canonical definitions of terms used across all specifications and code |
| [adr/0000-template.md](./adr/0000-template.md) | Standard template for proposing Architecture Decision Records |
| [CHANGELOG.md](./CHANGELOG.md) | Semantic versioning log of all documentation releases and spec increments |
| [audit/readiness-report.md](./audit/readiness-report.md) | Formal Build Readiness (7/7 passed) and Pilot Readiness evaluation report |
| [Project Aegis Documentation Suite v0.1.md](./Project%20Aegis%20Documentation%20Suite%20v0.1.md) | Historical founding document suite and inception brief (October 2026) |

---

## 8. Governance and Claims Conventions

- **Decision Register:** Every former OPEN QUESTION is recorded as Decided or Deferred in [decision-register.md](./00-decisions/decision-register.md) per Decision D3.
- **Assumptions Register & Validation Gates:** Customer-facing assumptions are governed by **Gate G1** (pre-customer data entry) and **Gate G2** (pre-paid contract) in [assumptions-register.md](./00-decisions/assumptions-register.md).
- **Claims Discipline (Decision D9):** Any performance number in docs, README, or marketing must be directly measured with hardware and methodology recorded. Never state a number as a result unless measured.
- **Compliance Phrasing:** Never write "compliant"; always write **"designed to align with"** (e.g., designed to align with RBI directives, DPDP Act 2023, PCI DSS scope minimisation).
- **Hypotheses:** Unmeasured numeric targets are explicitly marked `[hypothesis, to validate]`.
- **Localization:** Dates are ISO 8601 (YYYY-MM-DD). Monetary amounts are in INR (₹) unless explicitly stated.
