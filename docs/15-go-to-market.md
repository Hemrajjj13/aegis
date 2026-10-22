# Go-To-Market and Commercial Strategy

**Document:** 15  
**Status:** Draft-complete (docs v1.0, machine-checked, not yet reviewed)
**Owner:** Founder / Lead Architect ([DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

---

## 1. Purpose and Go-To-Market Philosophy

Project Aegis is a high-growth, commercial fraud decision engine built by a solo founder with AI coding agents, bootstrapped without venture capital. Because our capital is finite and our target buyers are risk-averse financial institutions, **our go-to-market strategy completely rejects paid advertisements, generic inbound PR, and enterprise sales bloat**.

Our sales motion is built entirely around an **empirical, proof-first wedge: the Free Historical Backtest (Decision D8)**. Fraud and risk leaders do not buy software based on marketing claims; they buy based on mathematical proof that a platform reduces false declines and catches fraud without blowing up checkout latency.

---

## 2. Positioning and Strategic Narrative

### 2.1 The Core Positioning Statement
> **For** mid-size Indian payment aggregators, fintech lenders, and neobanks processing 1M–10M transactions per month,  
> **Project Aegis is** the real-time, explainable fraud decision engine  
> **That** enables risk teams to simulate and verify rule changes against 90 days of historical data before deploying them to production.  
> **Unlike** legacy enterprise fraud suites that require 6-month integrations and operate as opaque black boxes,  
> **Aegis provides** a self-service dataset import backtest that demonstrates potential fraud reductions in 48 hours, executes in $< 25\text{ ms}$ p50, and is designed for zero raw card liability and no cleartext PII.

### 2.2 Three Defensible Value Pillars
1. **Mathematical Determinism (The Backtest Wedge):** Replay candidate rule changes against historical data with injected event-time clocks ([ADR-0003](file:///c:/dev/aegis/docs/adr/0003-event-time-for-deterministic-replay.md)) and in-memory velocity simulation ([ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md)), delivering 0.00% divergence and eliminating rule deployment anxiety.
2. **Predictable Sub-100ms Latency:** High-throughput Java 21 evaluation core compiles Google CEL rules to ASTs executing in microseconds, respecting tight payment gateway timeout budgets ([ASM-INT-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md)).
3. **Sovereign India Zero-Trust Architecture:** Strictly zero raw PAN ingress, client-side salted hashing (`account_id_hash`, `vpa_hash`), and 100% Indian data residency in AWS Mumbai (`ap-south-1`) designed to align with RBI and DPDP Act 2023 directives ([doc 13 §2](file:///c:/dev/aegis/docs/13-compliance-posture.md#2-technical-control-mappings-designed-to-align-with)).

---

## 3. Ideal Customer Profile (ICP) and Qualification

### 3.1 ICP Definition ([ASM-INT-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md))
- **Industry & Verticals:**
  - RBI-licensed Payment Aggregators & Payment Gateways (PA/PG)
  - Digital NBFC Lenders, BNPL providers, and Consumer Credit Fintechs
  - Neobanks, Prepaid Payment Instrument (PPI) wallets, and Digital Co-Branded Card Issuers
- **Operating Geography:** India domestic retail payment rails (UPI, RuPay, IMPS, NetBanking, Cards).
- **Scale:** 1,000,000 to 10,000,000 transactions per month (approx. 50 to 500 TPS sustained).
- **Team Size:** Risk, Fraud, and Compliance teams consisting of 1 to 5 members.

### 3.2 Account Qualification Checklist
| Qualification Dimension | Green Flag (Ideal Fit) | Red Flag (Disqualify / Defer) |
|---|---|---|
| **Pain Point** | Fear of false declines on UPI/cards; slow manual SQL queries to test rule changes. | Complacent with current fraud loss; no dedicated risk owner. |
| **Data Accessibility** | Historical transactions stored in PostgreSQL, Snowflake, BigQuery, or S3 Parquet. | Data locked in legacy monolithic mainframe with zero export capability. |
| **Technical Stack** | Modern cloud backend (AWS/GCP, Java, Node.js, Go, PostgreSQL, Kafka). | On-premises bare metal with strict closed-network air-gap requirements. |
| **Buying Authority** | Direct access to Head of Fraud, Chief Risk Officer (CRO), or VP Engineering. | Multi-layered bureaucratic procurement committee requiring 12-month RFPs. |

---

## 4. The 4-Stage Sales Motion (Without Ads)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ STAGE 1: TARGET OUTREACH (200 Curated Accounts from Public Registries)      │
│ Direct founder LinkedIn/Email outreach offering the Free Historical Backtest│
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ STAGE 2: THE FREE BACKTEST WEDGE (Decision D8, ADR-0008)                    │
│ Customer uploads 90-day CSV/Parquet dataset via client-side salted tool.    │
│ Automated Replay Report delivered in 48 hours showing false declines saved. │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ STAGE 3: DESIGN PARTNER SHADOW PILOT (3–5 Accounts)                         │
│ 4-week shadow mode via async Kafka mirroring; zero live payment blocking.   │
│ Weekly feedback sprints; signed Pilot SOW (doc 04b); Gate G1 passed.        │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ STAGE 4: COMMERCIAL CONVERSION (Gate G2 Passed)                             │
│ Transition to live inline Decision API; paid usage contract signed.         │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.1 Stage 1: Curated Account Outreach
- Target accounts sourced from RBI official registries ([doc 02 §3](file:///c:/dev/aegis/docs/02-discovery-plan.md#3-the-200-company-target-list-sourcing-methodology)).
- Initial outreach uses personalized, value-led templates focusing on checkout revenue protection ([doc 02 §5](file:///c:/dev/aegis/docs/02-discovery-plan.md#5-outreach-templates)).
- Target conversion: 200 accounts contacted $\to$ 40 responses $\to$ 15 discovery calls.

### 4.2 Stage 2: The Free Backtest Wedge
- Provide the prospective risk team with the Free Backtest One-Pager ([doc 02 §6](file:///c:/dev/aegis/docs/02-discovery-plan.md#6-the-free-backtest-one-pager)).
- Deliver the client-side salting script / Dataset Import Tool ([doc 06 §8](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md#8-dataset-import-api-and-schema-mapping-specification-decision-d8)).
- Customer uploads a 90-day transaction sample (min. 50,000 transactions).
- Within 48 hours, Aegis generates the **Backtest Comparison Report** ([doc 03 §5 (US-006)](file:///c:/dev/aegis/docs/03-prd.md#us-006-backtest-comparison-report-generation-decision-d8)), demonstrating exact false positives avoided and fraud caught.

### 4.3 Stage 3: The Design Partner Shadow Pilot
- Onboard 3 to 5 qualified design partners into a structured 4-week shadow pilot.
- Customer signs the bilateral DPA and Customer Pilot SOW ([doc 04b](file:///c:/dev/aegis/docs/04-sow-templates/customer-pilot-sow.md)).
- Customer mirrors live traffic asynchronously into an isolated Tier 1 Kafka topic; Aegis evaluates in shadow mode without blocking transactions.
- Weekly 45-minute sprint reviews with the founder to inspect rule hit rates and tune thresholds.

### 4.4 Stage 4: Paid Commercial Conversion
- Passing **Gate G2** requires a verified security questionnaire, signed SOW, and clean penetration test report.
- Switch caller integration from async mirroring to synchronous inline evaluation (`POST /v1/decisions`).

---

## 5. Acquisition Channels Table

| Acquisition Channel | Strategy & Tactical Execution | Target Persona | Expected Outcome Benchmark | Cost |
|---|---|---|---|---|
| **Direct Founder Outreach** | Personalized LinkedIn connection notes (<300 chars) and 2-step email sequence referencing the Free Backtest One-Pager. | Head of Fraud, Chief Risk Officer (CRO) | 20% reply rate; 7.5% discovery call booking rate (15 calls from 200 outreach). | $0 (Sweat equity) |
| **Fintech Communities & Groups** | Active technical participation in India FinTech Forum, Fintech Meetup, The Ken Community, and DSCI FinTech SIG. | VP Engineering, Principal Payment Architect | 2–3 high-intent inbound inquiries per month. | $0 |
| **Engineering Content Proofs** | Publishing deep technical case studies on concurrency limit testing, duplicate Kafka survival, and replay determinism ([doc 16](file:///c:/dev/aegis/docs/16-case-studies.md)). | Tech Leads, Payment Platform Engineers | Organic inbound referral from senior engineering peers; developer credibility. | $0 |
| **Ecosystem Regulatory Webinars** | Co-hosting educational sessions on DPDP Act 2023 compliance and RBI data localisation architecture for risk teams. | Compliance Officers, Heads of Legal/Risk | Position Aegis as the sovereign regulatory benchmark in Indian fintech. | $0 |

---

## 6. Pricing Strategy and Hypotheses

> **Claims Discipline Note:** All pricing figures below are **hypotheses to validate** during discovery interviews and pilot negotiations ([ASM-GTM-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md)).

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          STAGE 1: EVALUATION (FREE)                         │
│  - Free Backtest Offer: $0 (One-time historical analysis & report)          │
│  - Shadow Pilot Program: $0 (4 weeks in exchange for case study rights)     │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      STAGE 2: COMMERCIAL PRODUCTION (PAID)                  │
│  Option A: Pure Metered Usage                                               │
│    - ₹0.025 to ₹0.040 per evaluated decision (billed monthly)               │
│    - Minimum monthly spend commit: ₹50,000 / month                          │
│                                                                             │
│  Option B: Platform Base + Volume Overage                                   │
│    - Base Platform Fee: ₹75,000 / month (includes 2,000,000 decisions/mo)   │
│    - Overage: ₹0.020 per decision beyond base allotment                     │
│                                                                             │
│  Enterprise Add-Ons (Tier 2):                                               │
│    - Dedicated AWS KMS Customer Managed Key: +$100 / month                  │
│    - 99.99% Availability Multi-Region DR SLA: Custom contract              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Supporting Commercial Documents Outlines

### 7.1 Data Processing Agreement (DPA) Outline
A standard bilateral DPA aligned with DPDP Act 2023, designed to be executed prior to Gate G1:
1. **Definitions:** Aligned with DPDP Act 2023 ("Data Principal", "Data Fiduciary", "Data Processor").
2. **Scope of Processing:** Explicitly limited to fraud prevention, risk scoring, and rule evaluation.
3. **Data Processor Obligations:** Technical and organizational measures; processing only on documented instructions; zero PII retention.
4. **Sub-Processors:** Disclosure of AWS India (`ap-south-1`) as sole hosting sub-processor.
5. **Data Localisation Commitment:** Contractual covenant that data remains stored in India.
6. **Breach Notification:** Mandatory notification to customer within 24 hours of any confirmed security incident.
7. **Deletion and Crypto-Shredding:** Automated cryptographic erasure within 30 days of contract termination and delivery of a Certificate of Deletion.

### 7.2 Security Whitepaper Outline
An 8-page technical dossier provided to enterprise security teams during Gate G2:
1. **Executive Summary & Zero-Trust Architecture Overview.**
2. **Data Classification & Zero-PAN Architecture:** Structural exclusion of raw card numbers; secondary Luhn-valid regex scanner.
3. **Cryptographic Controls:** Argon2id password hashing, TLS 1.3 in transit, AES-256 KMS at rest.
4. **Audit Immutability:** SHA-256 Merkle batch sealing, AWS S3 Object Lock Compliance WORM anchors.
5. **Multi-Tenant Isolation:** PostgreSQL Row-Level Security (`FORCE ROW LEVEL SECURITY`) and tenant session scoping.
6. **Vulnerability Management:** Dependency scanning, container isolation, and annual CREST/CERT-In penetration tests.

---

## 8. Sales Funnel Metrics and Conversion Targets

Consistent with Decision D9, all pipeline expectations are tracked as hypotheses:

| Funnel Stage | Volume Target | Conversion Hypothesis | Milestone Gate |
|---|---|---|---|
| **1. Qualified Accounts Identified** | 200 accounts | 100% sourced from RBI/Fintech directories | Build Phase |
| **2. Outreach Responses** | 40 responses | 20.0% response rate on direct founder outreach | Discovery |
| **3. Discovery Calls Completed** | 15 calls | 37.5% conversion from response to 30-min call | **Gate G1** (10+ required) |
| **4. Historical Datasets Imported** | 5 datasets | 33.3% conversion from discovery call to backtest | Gate G1 Passed |
| **5. Design Partner Shadow Pilots** | 3 pilots | 60.0% conversion from backtest report to pilot | Pilot Phase |
| **6. Paid Commercial Contracts** | 1 paid customer | 33.3% conversion from 4-week pilot to contract | **Gate G2** Passed |

---

## 9. Objection Handling Playbook

### Objection 1: "We already have an in-house rule engine built on our database."
> **Response:** *"That is standard across Indian fintechs. The challenge isn't executing simple SQL queries—it is knowing what happens when you want to change a rule. When a new fraud vector emerges, can your team test a new threshold against 90 days of history in 5 minutes without writing custom data warehouse ETLs? Aegis doesn't force you to rip out your database; our Replay Tool sits alongside your stack so you can simulate and verify rule changes before pushing them live."*

### Objection 2: "We cannot share customer data or bank account numbers with an external vendor."
> **Response:** *"We agree completely. Under our architecture, you never send customer names, phone numbers, or bank account numbers. Our open Dataset Import Tool computes a tenant-salted SHA-256 hash on your infrastructure before data is uploaded. Furthermore, our engine automatically detects and rejects raw card PANs. We evaluate only mathematical hashes in an isolated AWS Mumbai container that is crypto-shredded after 30 days."*

### Objection 3: "Our payment gateway checkout has a 150 ms hard timeout. An external API will cause drop-offs."
> **Response:** *"Aegis was engineered specifically for sub-second Indian checkout loops. Our Java 21 core evaluates complex rule sets in under 10 ms, with an end-to-end server p99 latency under 100 ms. Furthermore, we support tenant-configurable fail-open circuit breakers: if internal latency ever breaches 80 ms, the engine immediately yields an approve verdict with a degraded flag. Your checkout never blocks."*

### Objection 4: "You are an early-stage company. What happens if your service goes down?"
> **Response:** *"First, our initial engagement is shadow-mode only—we run asynchronously off mirrored Kafka topics or backtest imports, presenting zero risk to your live checkout. Second, our engine is stateless: PostgreSQL is the single source of truth, and Redis derived velocity caches can rebuild in minutes. Third, our pilot agreement provides you full export of all custom rules and audit logs, with zero proprietary lock-in."*
