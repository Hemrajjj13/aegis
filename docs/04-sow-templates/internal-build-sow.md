# Internal Build — Statement of Work

**Document:** 04a  
**Status:** Stub — requires PRD (doc 03) and production-readiness sign-off (doc 01) before use  
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026

---

> This template is for internal tracking of the Stage 0–1 build.
> It is not a customer-facing document.

---

## 1. Objectives and success metrics

Objectives aligned with PRD (doc 03 §1) Stage 1 deliverables, governed by Validation Gate G1 (pre-customer data entry) and Gate G2 (pre-paid contract) in the [Assumptions Register](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md).

Success is defined as: all items in the launch gate checklist in
doc 01 §12 are checked and signed.

## 2. Scope

### In scope — Stage 1

- Decision API (synchronous endpoint)
- Kafka-based async ingest path
- Evaluation core (rules engine, shared by sync and async paths and replay)
- Rule DSL, versioning, and maker-checker workflow
- Replay engine (batch, deterministic)
- Audit trail (hash-chained, append-only)
- Console (screens listed in the architecture overview)
- Identity service (authentication and authorisation for all roles)
- Observability: metrics, logs, traces, alerts, status page
- Infrastructure as code for staging and production

### Explicitly out of scope — Stage 1

See doc 03 non-goals.

## 3. Deliverables and acceptance criteria

Deliverables are tracked per PRD (doc 03 §3) and acceptance criteria enforced by automated CI gates defined in the [Test and Performance Strategy](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md).

## 4. Milestones and timeline

| Milestone | Target week | Acceptance criterion |
|-----------|-------------|----------------------|
| Vertical slice (one transaction in → one decision out → audit row visible in console) | Week 10 | End-to-end test passes in staging |
| Evaluation core + Redis velocity + idempotency | Week 14 | Concurrency and duplicate-delivery CI tests pass |
| Rule DSL + versioning + maker-checker | Week 18 | Rule lifecycle walk-through completed by non-engineer |
| Replay engine | Week 22 | Determinism test passes; replay run does not touch live tables |
| Console complete | Week 26 | Each role can complete their primary workflow without guidance |
| Load and chaos tests | Week 30 | Launch gate checklist §12 passes |
| Shadow pilot with first design partner | Week 34 | First replay report delivered to design partner |

> **Timeline note:** the milestones above assume a funded multi-person team
> (approximately the squad structure in the founding document). Building
> solo with AI coding agents, the realistic timeline is **9–12 months**,
> not 34 weeks. Use the stage exit criteria from the founding document as
> your guide, not the calendar. Adjust this table before sharing it with
> any external party.

Milestone calendar reflects bootstrapped solo-founder velocity with AI coding agents (9–12 months baseline to pilot readiness). Exit criteria from founding principles take precedence over arbitrary calendar dates.

## 5. Environments and data

| Environment | Provided by | Format |
|-------------|-------------|--------|
| Staging compute and managed services | Internal (AWS) | IaC |
| Synthetic transaction dataset | Internal (generator) | JSON per event schema |
| Production environment | Internal (AWS) | IaC |

## 6. Assumptions and dependencies

- AWS account is active and billing is set up
- Domain name is registered and DNS is delegated
- Counsel review of DPDP and data-localisation is complete before staging receives any real data
- All questions in the [Decision Register](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) are resolved or classified before staging is opened to pilot customers

## 7. Security, privacy, and data handling

All data handling follows doc 01 §5.
No real customer data enters any environment until the launch gate in doc 01 §12 is signed.

## 8. Roles and responsibilities

| Role | Person | Responsibility |
|------|--------|----------------|
| Engineering lead | Founder / Lead Architect ([DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md)) | Architecture decisions, pull request review, human-only zones |
| Product owner | Founder / Lead Architect ([DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md)) | Acceptance criteria, priority |
| Security review | External Specialist ([DR-SEC-04](file:///c:/dev/aegis/docs/00-decisions/decision-register.md), Pre-G2) | Threat model, penetration test sign-off |
| Legal review | Technology Counsel (ASM-LEG-01..04, Pre-G1) | DPA, SOW, regulatory questions |

## 9. Change control

Changes to scope or milestones require updating this document and
doc 03 (PRD). Changes that affect the launch gate checklist in doc 01 §12
require re-sign-off on the affected section.

## 10. Fees and exit

Not applicable (internal build). If the project is paused or cancelled,
all infrastructure is torn down within 30 days and data is deleted per
the retention policy in doc 01 §5.3.
