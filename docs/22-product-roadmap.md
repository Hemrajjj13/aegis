# Product Roadmap: Stages 0 to 4

**Document:** 22  
**Status:** Draft-complete (docs v1.0, machine-checked, not yet reviewed)
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

---

## 1. Purpose and Product Strategy

This roadmap defines the multi-year product evolution of Project Aegis from core inception to enterprise platform scale.

Aegis follows a disciplined, capital-efficient commercial sequence designed for a bootstrapped founder leveraging AI coding agents:
1. **Wedge-First Strategy (Stage 1):** Enter the market not as a bloated risk management suite, but with an acute, high-leverage wedge: a zero-downtime transaction decision engine paired with an offline **deterministic replay and backtest tool** that allows risk teams to test rule changes against their own historical data before touching production.
2. **Operations & Expansion (Stage 2):** Expand into full case triage, ground-truth label ingestion, enterprise Single Sign-On (SSO), and ML champion/challenger routing once trust and transaction flow are established.
3. **Product Adjacencies (Stage 3):** Expand across the customer lifecycle into Account Takeover (ATO) login risk, merchant onboarding, AML transaction monitoring, and disputes.
4. **Platform & Ecosystem (Stage 4):** Transform into an open platform featuring a curated fraud rule marketplace, turnkey payment gateway connectors, and third-party signal integrations.

---

## 2. Stage Progression and Gating Overview

```
┌──────────────────┐
│ STAGE 0          │ Foundation & Technical Core
│ (Milestones 0–5) │ Zero wall-clock engine, Redis Lua, Replay kernel, Tier 0 compose
└────────┬─────────┘
         │ Exit Gate: Vertical slice + Concurrency & Equivalence tests pass
         ▼
┌──────────────────┐
│ STAGE 1 (WEDGE)  │ Free Backtest, Replay & Shadow Mode Pilot
│ (Milestones 6–9) │ Dataset importer, Backtest report, Tier 1 AWS shadow deployment
└────────┬─────────┘
         │ Exit Gate: Gate G1 (Legal brief signed + 10 discovery interviews logged)
         │            Gate G2 (2 successful shadow pilots + conversion to paid)
         ▼
┌──────────────────┐
│ STAGE 2          │ Operational Scale & Case Management
│ (Enterprise)     │ Triage queue, Ground-truth labels, Keycloak SSO, ML canaries
└────────┬─────────┘
         │ Exit Gate: 5 paying customers, SOC 2 Type I, positive unit economics
         ▼
┌──────────────────┐
│ STAGE 3          │ Adjacent Risk Products
│ (Expansion)      │ Login ATO risk, Merchant onboarding, AML monitoring, Disputes
└────────┬─────────┘
         │ Exit Gate: 40% multi-product adoption, NRR >= 120%
         ▼
┌──────────────────┐
│ STAGE 4          │ Platform & Ecosystem
│ (Ecosystem)      │ Rule pack marketplace, Gateway connectors, Signal provider mesh
└──────────────────┘
```

---

## 3. Detailed Stage Specifications

---

### Stage 0: Foundation & Inception (Technical Kernel)
- **Strategic Focus:** Eliminate the two fatal technical risks (concurrency limit bypass and replay non-determinism) before writing any application chrome.
- **What Must Be True Before Starting:**
  - Architecture specifications, API schemas, and data model finalized ([Docs 05–17](file:///c:/dev/aegis/docs/00-index.md)).
  - Decision Register ([Doc 00-decisions](file:///c:/dev/aegis/docs/00-decisions/decision-register.md)) and Assumptions Register approved.
  - Tier 0 local Docker Compose environment verified within the $\le 3.89\text{ GB}$ memory budget on measured 7.79 GB hardware ([Doc 21](file:///c:/dev/aegis/docs/21-local-dev-environment.md)).
- **Core Deliverables:**
  - `aegis-core`: Deterministic `Clock` SPI, `DefaultEvaluationCore`, Google CEL AST parser and complexity limiter ($\le 250$ nodes).
  - `aegis-velocity`: Atomic Redis Lua sliding window script with `event_id` membership idempotency ([ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md)).
  - `aegis-replay`: In-memory `ConcurrentSkipListSet` velocity store maintaining strict event-time ordering ([ADR-0003](file:///c:/dev/aegis/docs/adr/0003-event-time-for-deterministic-replay.md)).
  - Vertical Slice (M1): Ingest one HTTP POST, evaluate against stub rule, commit to PostgreSQL with RLS, display in minimal console.
- **Measurable Metrics (Tier 0):**
  - Concurrency test passes: Exactly $L$ of $N$ parallel threads pass ($5$ of $50$) across 100 test iterations ([Doc 11 §4.1](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md)).
  - `VelocityImplementationEquivalenceTest` passes with 0 divergence between Redis Lua and Java replay ([Doc 11 §4.3](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md)).
  - Total container memory $\le 3,072\text{ MB}$.
- **Main Risks & Mitigations:**
  - *Risk:* Divergence between Redis Lua sliding window and Java replay in-memory set.  
    *Mitigation:* Automated equivalence test runs as a mandatory CI pull request gate.
- **Exit Criteria:**
  - Milestones M0 through M5 completed in backlog ([Doc 20](file:///c:/dev/aegis/docs/20-build-backlog.md)).
  - Vertical slice demonstrated end-to-end.

---

### Stage 1: The Wedge (Decision Engine, Deterministic Replay & Free Backtest)
- **Strategic Focus:** Acquire the first 2 design partner pilots through a frictionless, zero-risk commercial wedge: the Free Backtest Offer and asynchronous Tier 1 shadow mode.
- **What Must Be True Before Starting:**
  - Stage 0 exit criteria fully satisfied.
  - Legal Review Brief executed with qualified Indian counsel ([Doc 13 §4](file:///c:/dev/aegis/docs/13-compliance-posture.md)), confirming DPDP Act Data Processor posture and RBI Mumbai data residency.
  - Sourcing of 200-company target list initiated from verified registries ([Doc 02 §2](file:///c:/dev/aegis/docs/02-discovery-plan.md)).
- **Core Deliverables:**
  - `aegis-dataset-importer`: Streaming CSV/JSONL importer handling 100k rows without heap exhaustion; zero PAN storage invariant ([Doc 17 §3.8](file:///c:/dev/aegis/docs/17-backend-design.md)).
  - Executive Backtest Report export (PDF/JSON/CSV) computing caught fraud, reduced false alarms, and latency metrics ([Doc 03 §5.6](file:///c:/dev/aegis/docs/03-prd.md), [Doc 15 §2.2](file:///c:/dev/aegis/docs/15-go-to-market.md)).
  - Maker-checker rule lifecycle preventing self-approval ([Doc 01 §11.2](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md)).
  - Tier 1 Terraform deployment in AWS Mumbai (`ap-south-1`) running in shadow mode with Kafka mirror consumer ([Doc 14 §3](file:///c:/dev/aegis/docs/14-deployment-and-iac.md)).
  - In-house identity subsystem with Argon2id, TOTP MFA, and `LocalMailboxNotificationService` ([Doc 19](file:///c:/dev/aegis/docs/19-identity-and-auth-spec.md)).
- **Measurable Metrics (Tier 1):**
  - Conduct and log $\ge 10$ customer discovery interviews; synthesize into v1.1 PRD ([Doc 02 §8](file:///c:/dev/aegis/docs/02-discovery-plan.md)).
  - Sign $\ge 2$ design partners under Customer Pilot SOW ([Doc 04](file:///c:/dev/aegis/docs/04-sow-templates/customer-pilot-sow.md)).
  - Process mirrored transaction stream at sustained 500 req/s with server p99 latency $\le 100\text{ ms}$.
  - Zero downtime impact on customer checkout flows (100% asynchronous shadow isolation).
- **Main Risks & Mitigations:**
  - *Risk:* Customer reluctant to share production data.  
    *Mitigation:* Anonymized synthetic data backtest or signed bilateral DPA ([Doc 15 §5](file:///c:/dev/aegis/docs/15-go-to-market.md)).
  - *Risk:* Regulatory uncertainty under DPDP Act 2023.  
    *Mitigation:* Written legal opinion from counsel at Gate G1 ([Doc 13 §4](file:///c:/dev/aegis/docs/13-compliance-posture.md)).
- **Exit Criteria (Gate G1 & G2):**
  - Gate G1: 10 interviews logged; counsel brief executed; PRD updated to v1.1.
  - Gate G2: 2 design partners successfully complete 14-day shadow pilot; minimum 1 partner transitions to paid commercial contract.

---

### Stage 2: Operations & Enterprise Scale (Case Management, Labels & SSO)
- **Strategic Focus:** Move from shadow observation to primary blocking path; expand tooling into the operational case management and compliance workflow.
- **What Must Be True Before Starting:**
  - Stage 1 exit criteria met; Gate G2 passed with first paid ARR contract.
  - SOC 2 Type I readiness audit initiated with external auditor.
  - Single founder hires first Site Reliability Engineer (SRE) / Customer Success Engineer to ensure on-call redundancy ([Doc 01 §13](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md)).
- **Core Deliverables:**
  - Operational Case Review Queue with SLA tracking, analyst assignment, and bulk labeling ([Doc 10 §7.4](file:///c:/dev/aegis/docs/10-design-system-and-ux.md)).
  - Ground-truth label ingestion pipeline (chargebacks, confirmed fraud, false positives) updating feature stores.
  - Enterprise SSO extension: `KeycloakOidcIdentityProvider` adapter supporting Okta, Azure AD, and SAML 2.0 ([Doc 19 §7](file:///c:/dev/aegis/docs/19-identity-and-auth-spec.md)).
  - Machine Learning Champion/Challenger routing: shadow scoring and statistical canary traffic splitting (1%, 5%, 10%) ([Doc 01 §11.2](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md)).
  - Tier 2 multi-AZ high-availability deployment in AWS Mumbai ([Doc 14 §4](file:///c:/dev/aegis/docs/14-deployment-and-iac.md)).
- **Measurable Metrics:**
  - Convert 5 paying enterprise customers; reach positive unit economics.
  - Sustained throughput of 2,500 req/s with p99 latency $\le 50\text{ ms}$.
  - Case review triage time reduced by $\ge 30\%$ via keyboard navigation workflows.
  - Zero critical findings in external CREST/CERT-In penetration test.
- **Main Risks & Mitigations:**
  - *Risk:* Enterprise sales cycles stalling on security questionnaires.  
    *Mitigation:* Pre-packaged Security Whitepaper, DPA, and SOC 2 Type I report ([Doc 15 §5](file:///c:/dev/aegis/docs/15-go-to-market.md)).
- **Exit Criteria:**
  - 5 active live-blocking enterprise customers.
  - SOC 2 Type I certification achieved.

---

### Stage 3: Adjacent Risk Products (ATO, Onboarding & AML)
- **Strategic Focus:** Expand Annual Contract Value (ACV) and Net Revenue Retention (NRR) by solving adjacent risk problems across the customer lifecycle.
- **What Must Be True Before Starting:**
  - Stage 2 exit criteria met; core payment decision engine running stably with $\ge 99.95\%$ availability.
  - Core team expanded to include specialized risk and compliance domain engineers.
- **Core Deliverables:**
  - **Account Takeover (ATO) & Login Risk:** Real-time credential stuffing detection, device fingerprint velocity, behavioral anomaly scoring at login.
  - **Merchant & User Onboarding Risk:** Document verification webhooks, bank account penny-drop verification, corporate registry checks.
  - **AML Transaction Monitoring:** Suspicious Activity Reporting (SAR/STR) generation aligned with Financial Intelligence Unit - India (FIU-IND) guidelines.
  - **Dispute & Chargeback Automation:** Automated evidence compilation and chargeback representment tracking.
- **Measurable Metrics:**
  - Net Revenue Retention (NRR) $\ge 120\%$.
  - $\ge 40\%$ of existing customers adopting at least one adjacent product module.
  - Platform processing $> 50\text{ million}$ decisions per month across payments, logins, and onboarding.
- **Main Risks & Mitigations:**
  - *Risk:* Product fragmentation and dilution of core payment decision engine excellence.  
    *Mitigation:* Each adjacent module built as an independent service utilizing the shared `EvaluationCore` and velocity infrastructure.
- **Exit Criteria:**
  - At least two adjacent modules generating independent ARR.
  - Verified regulatory compliance with FIU-IND reporting standards.

---

### Stage 4: Platform & Ecosystem (Marketplace & Pre-Built Mesh)
- **Strategic Focus:** Establish Aegis as the industry-standard transaction risk operating system in India and high-growth markets.
- **What Must Be True Before Starting:**
  - Proven multi-product market fit; $> 20$ live enterprise customers.
  - Published and version-stabilized Public Rule Engine SDKs (Java, Python, TypeScript, Go).
- **Core Deliverables:**
  - **Rule Pack Marketplace:** Curated, certified fraud rules authored by industry experts (e.g., "UPI P2M QR Tampering Pack", "Gaming Fraud Pack", "Instant Lending Default Pack") with rev-share for authors.
  - **Pre-Built Gateway Connectors:** Zero-code integrations for Juspay, Razorpay, Cashfree, PayU, Stripe, and Adyen.
  - **External Signal Provider Mesh:** Native integrations with IP intelligence (MaxMind, IPQualityScore), device fingerprinting vendors, and telecom SIM-swap APIs.
- **Measurable Metrics:**
  - 50+ third-party connectors and certified rule packs in marketplace.
  - Ecosystem revenue contributing $\ge 25\%$ of total corporate revenue.
- **Main Risks & Mitigations:**
  - *Risk:* Malicious or inefficient rule packs degrading tenant performance.  
    *Mitigation:* Mandatory automated sandbox compilation, AST complexity limits ($\le 250$ nodes), and performance profiling prior to marketplace certification.
- **Exit Criteria:**
  - Project Aegis recognized as the default open platform standard for real-time risk decisioning.
