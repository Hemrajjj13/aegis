# Project Aegis — Product & Regulatory Assumptions Register

**Document:** `docs/00-decisions/assumptions-register.md`  
**Status:** Authoritative Validation Tracking Register  
**Maintained by:** Founder / Lead Architect  
**Last Updated:** October 2026  

---

## 1. Product Validation Governance & Gates (Decision D2, D3)

Project Aegis is a product with growth potential built by one founder with AI coding agents. To maintain engineering momentum without getting blocked by external delays, **product build proceeds on documented assumptions while validation runs as a parallel track** (Decision D2).

To protect legal, regulatory, and commercial safety, two non-negotiable **Validation Gates** govern progress:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            PHASE 0: BUILD TRACK                             │
│       Synthetic Data Generator (tools/synthetic-generator) + Local CI       │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ GATE G1: PRE-CUSTOMER DATA ENTRY                                            │
│ Must pass BEFORE any real customer data enters Aegis:                       │
│ 1. 10+ completed customer discovery interviews (doc 02).                    │
│ 2. Legal counsel review of:                                                 │
│    - DPDP Act 2023 (Data Fiduciary vs. Data Processor role determination).  │
│    - India payment data localisation (RBI circulars).                       │
│    - PCI DSS scope minimisation (Zero raw PAN invariant verification).      │
│    - Data Processing Agreement (DPA) and Privacy Policy finalized.          │
│ 3. Prospective customer dataset enters ONLY through Dataset Import Tool     │
│    (Decision D8) with client-side salting/hashing and automated PAN scan.   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ PHASE 1: SHADOW PILOT & FREE BACKTESTS                                      │
│ Asynchronous traffic mirroring + historical replay; zero payment blocking   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ GATE G2: PRE-PAID COMMERCIAL CONTRACT                                       │
│ Must pass BEFORE signing a paid commercial contract:                        │
│ 1. Signed pilot Statement of Work (SOW) with defined commercial terms.      │
│ 2. Customer enterprise security questionnaire verified and answered.        │
│ 3. External CERT-In accredited penetration test completed with zero P1s.    │
└─────────────────────────────────────────────────────────────────────────────┘
```

> **Mandatory Rule:** Never state or claim customer validation before the respective gate has been formally passed and documented in this register.

---

## 2. Assumptions Register

| Assumption ID | Category | Assumption / Baseline Specification | How to Validate | Responsible Owner | Gate Dependency | Current Status |
|---|---|---|---|---|---|---|
| **ASM-LEG-01** | Legal / DPDP | Aegis acts as a **Data Processor** for transaction events and a **Data Fiduciary** only for console operator credentials under DPDP Act 2023. | Formal legal review of technical architecture and DPA template with Indian technology counsel. | Founder / Legal Counsel | **Gate G1** | Open (Pending Legal Review) |
| **ASM-LEG-02** | Legal / RBI | Storing primary data, Redis caches, Kafka logs, and S3 archives exclusively in AWS Mumbai (`ap-south-1`) aligns with RBI payment-data localisation directives without requiring cross-border replication. | Legal counsel review of RBI circular on Storage of Payment System Data (April 2018 / 2021). | Founder / Compliance Counsel | **Gate G1** | Open (Pending Legal Review) |
| **ASM-LEG-03** | Legal / PCI | Enforcing a zero-raw-PAN architecture (strict schema validation + Luhn-valid regex filters on free-text) and processing only network/merchant card tokens places Aegis outside PCI DSS Level 1 Merchant/Service Provider scope (SAQ A or SAQ D-Service Provider scoped down). | Formal assessment by a certified Qualified Security Assessor (QSA). | Founder / QSA Consultant | **Gate G1** | Open (Pending QSA Review) |
| **ASM-LEG-04** | Legal / DPA | A standard bilateral Data Processing Agreement (DPA) with a 30-day data retention and crypto-shredding certificate is acceptable to mid-size Indian fintechs. | DPA review by pilot customer compliance officers during discovery interviews. | Founder / Legal Counsel | **Gate G1** | Open (Drafting DPA) |
| **ASM-INT-01** | Discovery | Target customers are mid-size Indian payment aggregators, fintech lenders, and neo-banks processing 1M–10M transactions/month with 1–5 risk team members. | Conduct 10+ discovery interviews using protocol defined in `docs/02-customer-market-discovery-plan.md`. | Founder | **Gate G1** | Open (0 of 10 completed) |
| **ASM-INT-02** | Discovery | A p99 server-side evaluation latency target of $\le 100\text{ ms}$ is acceptable for payment gateways without triggering checkout timeouts. | Verify payment gateway upstream timeout thresholds during discovery interviews. | Founder | **Gate G1** | Assumed Baseline |
| **ASM-INT-03** | Discovery | Pilot customers expect a launch baseline capacity of 2,000 decisions/second with peak burst headroom up to 5,000 decisions/second. | Review peak TPS profiles of prospective pilot customers during interviews. | Founder | **Gate G1** | Assumed Baseline |
| **ASM-INT-04** | Discovery | The synthetic load test traffic mix (65% standard approve, 10% limit boundary, 5% blocklist, 5% duplicate, 5% timeout, 5% malformed) accurately mirrors production stress patterns. | Review ground-truth traffic distributions with at least one design partner. | Tech Lead / Founder | **Gate G1** | Assumed Baseline |
| **ASM-INT-05** | Discovery | Pilot partners accept tenant-configurable **fail-open** (verdict `approve`, flag `degraded`) during severe downstream enrichment outages, rather than failing closed on customer transactions. | Risk policy walkthrough with pilot Risk Leads. | Founder | **Gate G1** | Assumed Baseline |
| **ASM-INT-06** | Discovery | Shared-table multi-tenancy enforced via PostgreSQL Row-Level Security (`FORCE ROW LEVEL SECURITY`) satisfies compliance audits without requiring separate physical databases per tenant. | Customer security review and architecture sign-off during pilot onboarding. | Security Lead / Founder | **Gate G1** | Assumed Baseline |
| **ASM-INT-07** | Discovery | 90 days of live searchable decision history in PostgreSQL NVMe storage satisfies fraud review queue requirements, with older data accessed via S3 Glacier archives. | Validate analyst investigation lookback windows in risk team interviews. | Product Lead | **Gate G1** | Assumed Baseline |
| **ASM-INT-08** | Discovery | Callers can compute client-side tenant-salted SHA-256 hashes (`account_id_hash`) to avoid transmitting cleartext bank account numbers. | Verify integration capabilities of pilot engineering teams. | Tech Lead | **Gate G1** | Assumed Baseline |
| **ASM-INT-09** | Discovery | Hashing UPI VPAs (`vpa_hash`) satisfies velocity counter requirements without hindering fraud analyst investigation. | Confirm VPA masking policy with pilot compliance officers. | Product Lead | **Gate G1** | Assumed Baseline |
| **ASM-INT-10** | Discovery | Originating payment gateways cannot reliably guarantee cross-node monotonic `sequence_number` counters; synthetic ordering must use `(event_time, received_at)`. | Technical integration review with gateway developers. | Tech Lead | **Gate G1** | Assumed Baseline |
| **ASM-INT-11** | Discovery | Pilot fintech engineering teams predominantly use Java or Node.js (TypeScript) for backend microservices; Go/Python SDKs are secondary. | Survey tech stacks during discovery interviews. | Founder | **Gate G1** | Assumed Baseline |
| **ASM-INT-12** | Discovery | Enterprise customers accept daily Merkle root batch anchoring in AWS S3 Object Lock (Compliance WORM mode) as legal proof of tamper-evidence without public blockchain notarisation. | Legal/compliance review of audit log verification whitepaper. | Compliance Counsel | **Gate G1** | Assumed Baseline |
| **ASM-GTM-01** | Commercial | A **Free Backtest Offer** based on historical CSV/Parquet dataset import generates high-intent qualified pilot leads by demonstrating immediate fraud catch gains. | Measure prospective customer conversion rate from initial pitch to backtest dataset submission. | Founder | **Gate G1 / G2** | Core GTM Assumption |
| **ASM-SEC-01** | Security | A 15-minute P1 incident response SLA during business hours (10:00–19:00 IST) with best-effort off-hours is acceptable to design partners during an unpaid shadow pilot. | Explicitly incorporated into Pilot Statement of Work (SOW). | Founder | **Gate G2** | Gated in SOW |
| **ASM-SEC-02** | Security | An external CERT-In / CREST-accredited penetration test report demonstrating zero critical or high vulnerabilities is sufficient to satisfy customer security questionnaires. | Contract external security assessment firm prior to commercial contracting. | Founder | **Gate G2** | Scheduled Pre-G2 |
| **ASM-PERF-01**| Performance | Java 21 `evaluation-core` compiles and evaluates a ruleset of 50–200 CEL rules in $< 10\text{ ms}$ on standard server hardware without garbage collection spikes. | Continuous automated micro-benchmarks committed to `/tests/load`. | Tech Lead | Build Track (Continuous) | Open (To Validate) |
| **ASM-PERF-02**| Performance | Redis sorted-set velocity Lua script executes atomic sliding-window check-and-insert in $< 2\text{ ms}$ at 2,000 TPS under concurrent multi-account load. | Testcontainers integration concurrency load test. | Backend Lead | Build Track (Continuous) | Open (To Validate) |
| **ASM-PERF-03**| Performance | Replay engine throughput achieves $\ge 20,000\text{ events/second}$ per worker thread when reading local or S3 Parquet datasets. | Replay engine benchmark test against 1,000,000 synthetic records. | Data Lead | Build Track (Continuous) | Open (To Validate) |
| **ASM-PERF-04**| Performance | Redis derived state can be completely rebuilt from PostgreSQL `decisions` source of truth for 50 million records in $< 15\text{ minutes}$. | Rebuild batch script staging drill with simulated 50M row dataset. | DevOps Lead | Build Track (Pre-Launch) | Open (To Validate) |
