# Documentation Index — Project Aegis

Last updated: October 2026  
Maintainer: Founder / Lead Architect (Decision [DR-DOC-01](./00-decisions/decision-register.md))

---

## Purpose

This index is the single entry point for all project documentation.
It maps every document to the question it answers, its current status,
and the gate it must pass before the team treats it as authoritative.

---

## Decisions, Assumptions, and Audits

| # | File | Question it answers | Status | Gate |
|---|------|---------------------|--------|------|
| DR | [00-decisions/decision-register.md](./00-decisions/decision-register.md) | How are architectural and operational open questions resolved? | Active | Decision D3 |
| ASM | [00-decisions/assumptions-register.md](./00-decisions/assumptions-register.md) | What product/regulatory assumptions exist and how are they validated? | Active | Gate G1 / G2 (D2) |
| AUD | [audit/consistency-report.md](./audit/consistency-report.md) | How are terminology, enums, scales, and claims verified across docs? | Active | Living Audit |

---

## Wave 1 — Discovery (weeks 1–3)

| # | File | Question it answers | Status | Gate |
|---|------|---------------------|--------|------|
| 01 | [01-production-readiness-discovery.md](./01-production-readiness-discovery.md) | What does "production-ready" mean for this system? | Approved | Founder sign-off |
| 02 | [02-customer-market-discovery-plan.md](./02-customer-market-discovery-plan.md) | Who are the buyers, what do they pain about, and what will they pay? | Outline | 10+ interviews completed (Gate G1) |
| 03 | [03-prd.md](./03-prd.md) | What does stage-1 ship, to whom, measured how? | Draft | Design partner review (Gate G1) |
| 04a | [04-sow-templates/internal-build-sow.md](./04-sow-templates/internal-build-sow.md) | What is the internal build contract? | Draft | Legal review (Gate G1) |
| 04b | [04-sow-templates/customer-pilot-sow.md](./04-sow-templates/customer-pilot-sow.md) | What does a customer pilot commit to? | Draft | Legal review (Gate G2) |

## Wave 2 — Design (weeks 3–6)

| # | File | Question it answers | Status | Gate |
|---|------|---------------------|--------|------|
| 05 | [05-architecture/overview.md](./05-architecture/overview.md) | What are the major components and how do they interact? | Draft | Architecture review |
| ADR-0001 | [adr/0001-modular-monolith-first.md](./adr/0001-modular-monolith-first.md) | Why not microservices from day one? | Approved | — |
| ADR-0002 | [adr/0002-durable-decision-write-before-response.md](./adr/0002-durable-decision-write-before-response.md) | Why write the decision before responding to the caller? | Approved | — |
| ADR-0003 | [adr/0003-event-time-for-deterministic-replay.md](./adr/0003-event-time-for-deterministic-replay.md) | Why use event time, not wall-clock time, in the replay engine? | Approved | — |
| ADR-0004 | [adr/0004-batched-audit-log-hash-chaining.md](./adr/0004-batched-audit-log-hash-chaining.md) | Why batch audit-log hash-chaining instead of serialising per tenant? | Approved | — |
| ADR-0005 | [adr/0005-velocity-script-event-id-idempotency.md](./adr/0005-velocity-script-event-id-idempotency.md) | How do velocity scripts and replay achieve counter idempotency? | Approved | — |
| ADR-0006 | [adr/0006-deployment-tiers.md](./adr/0006-deployment-tiers.md) | How do deployment tiers (Tier 0, 1, 2) operate from a single codebase? | Approved | Decision D1, D6, D7 |
| ADR-0007 | [adr/0007-identity-behind-a-port.md](./adr/0007-identity-behind-a-port.md) | How is authentication/authorisation abstracted across deployment tiers? | Approved | Decision D4 |
| ADR-0008 | [adr/0008-dataset-import-for-backtests.md](./adr/0008-dataset-import-for-backtests.md) | How does the dataset import pipeline enable free backtests safely? | Approved | Decision D8 |
| 06 | [06-api-and-event-schemas.md](./06-api-and-event-schemas.md) | What are the API and Kafka message contracts? | Draft | Schema registry compat check |
| 07 | [07-data-model.md](./07-data-model.md) | Which tables exist, how is PII handled, what is retained how long? | Draft | Privacy review |
| 08 | [08-threat-model.md](./08-threat-model.md) | What are the attack surfaces and mitigations? | Draft | Security sign-off |
| 09 | [09-rule-dsl-spec.md](./09-rule-dsl-spec.md) | How are rules written, versioned, stored, and explained? | Draft | Risk team review |
| 10 | [10-design-system-and-ux.md](./10-design-system-and-ux.md) | What is the visual and interaction language? | Draft | Design review |
| 11 | [11-test-and-performance-strategy.md](./11-test-and-performance-strategy.md) | How is correctness and speed verified? | Draft | QA lead sign-off |

## Wave 3 — Operate (weeks 6–9)

| # | File | Question it answers | Status | Gate |
|---|------|---------------------|--------|------|
| 12 | [12-slos-runbooks-dr.md](./12-slos-runbooks-dr.md) | How does this stay up and recover? | Draft | Game-day drill passed |
| 13 | Compliance mapping | PCI scope, DPDP, SOC 2 / ISO 27001 | Not started | Consultant review (Gate G1/G2) |
| 14 | [14-deployment-and-iac.md](./14-deployment-and-iac.md) | How is an environment reproduced from scratch? | Draft | Clean-room deploy verified |

## Wave 4 — Market (weeks 8–12)

| # | File | Question it answers | Status | Gate |
|---|------|---------------------|--------|------|
| 15 | GTM plan, pricing, pilot agreement, DPA, security whitepaper | How does the product reach and close its first customers? | Not started | Founder review (Gate G1) |
| 16 | Case study templates | How are results communicated externally? | Not started | First pilot data (Gate G2) |

---

## Reference

| File | Purpose |
|------|---------|
| [glossary.md](./glossary.md) | Canonical definitions of terms used across all documents |
| [adr/0000-template.md](./adr/0000-template.md) | Template for new architectural decision records |

---

## Governance and Conventions

- **Decision Register:** Every former OPEN QUESTION is recorded as Decided or Deferred in [decision-register.md](./00-decisions/decision-register.md) per Decision D3.
- **Assumptions Register & Validation Gates:** Customer-facing assumptions are governed by **Gate G1** (pre-customer data entry) and **Gate G2** (pre-paid contract) in [assumptions-register.md](./00-decisions/assumptions-register.md).
- **Claims Discipline:** Any performance number in docs, README, or marketing must be measured with hardware and methodology recorded (Decision D9). Never state a number as a result unless measured.
- **Compliance Phrasing:** Never write "compliant"; always write "designed to align with" (e.g. designed to align with RBI directives, DPDP Act 2023).
- **Hypotheses:** Unmeasured numeric targets are explicitly marked `hypothesis, to validate by load test`.
- Dates are ISO 8601 (YYYY-MM-DD). Amounts are in INR unless stated.

