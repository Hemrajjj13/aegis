# Customer and Market Discovery Plan

**Document:** 02  
**Status:** Draft-complete (docs v1.0, machine-checked, not yet reviewed)
**Owner:** Founder / Lead Architect ([DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

---

## 1. Purpose and Validation Policy (Decision D2, D3)

This document establishes the customer and market discovery protocol for Project Aegis. Because Project Aegis is a commercial product with growth potential built by a solo founder with AI coding agents, **product engineering proceeds on documented baseline assumptions while customer discovery executes as a parallel track** (Decision D2).

### 1.1 The Non-Negotiable Validation Rule
> **MANDATORY POLICY:** Nothing in Project Aegis is considered "validated" until discovery interviews are conducted, logged in Section 6 of this document, and formally reviewed against the [Assumptions Register](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md). 
> 
> Engineering leads, rule authors, and marketing materials must never claim customer validation or market consensus based on intuition or informal chats. Passing **Gate G1** requires a minimum of 10 fully logged discovery interviews across target personas.

---

## 2. Discovery Objectives and Hypotheses

The discovery program validates the following core assumptions prior to **Gate G1** (pre-customer data entry) and **Gate G2** (pre-paid contract):

| Objective | Target Hypothesis | Assumption Ref | Gate |
|---|---|---|---|
| **Ideal Customer Profile (ICP)** | Mid-size Indian payment aggregators, fintech lenders, and neobanks processing 1M–10M transactions/month with 1–5 risk team members. | [ASM-INT-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | **Gate G1** |
| **Server Latency Budget** | A server-side p99 latency target of $\le 100\text{ ms}$ aims to fit within payment gateway checkout timeout envelopes. | [ASM-INT-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | **Gate G1** |
| **Throughput Profile** | Baseline launch capacity of 2,000 TPS with peak burst headroom of 5,000 TPS is sufficient for pilot cohorts. | [ASM-INT-03](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | **Gate G1** |
| **Traffic Distribution** | Synthetic mix (65% approve, 10% velocity limit, 5% blocklist, 5% duplicate, 5% timeout, 5% malformed) mirrors production stress. | [ASM-INT-04](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | **Gate G1** |
| **Fail-Open Policy** | Payment aggregators demand configurable fail-open with degraded flagging during downstream outages; lenders prefer fail-closed. | [ASM-INT-05](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | **Gate G1** |
| **Account Identifier Hashing** | Callers can compute client-side tenant-salted SHA-256 hashes (`account_id_hash`) without sending raw account IDs. | [ASM-INT-08](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | **Gate G1** |
| **VPA Masking Policy** | Hashing UPI VPAs (`vpa_hash`) is designed to support velocity counter requirements without hindering fraud analyst investigation. | [ASM-INT-09](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | **Gate G1** |
| **Sequence Number Feasibility** | Originating gateways cannot guarantee monotonic counters; synthetic ordering `(event_time, received_at)` is required. | [ASM-INT-10](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | **Gate G1** |
| **Backend Tech Stack** | Target fintech engineering teams predominantly build in Java or Node.js (TypeScript); Python and Go SDKs are secondary. | [ASM-INT-11](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | **Gate G1** |
| **Free Backtest Offer** | A free 48-hour historical backtest using CSV/Parquet dataset import generates high-intent qualified pilot leads. | [ASM-GTM-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | **Gate G1/G2** |

---

## 3. The 200-Company Target List: Sourcing Methodology

The target list of 200 named Indian fintech accounts is constructed exclusively from public, verified registry sources without paid advertising:

### 3.1 Primary Sourcing Registries
1. **RBI Authorised Payment System Operators (PA/PG List):**
   - *Source:* Reserve Bank of India official directory under the Payment and Settlement Systems Act, 2007 (`rbi.org.in`).
   - *Entities:* Licensed Payment Aggregators (e.g., Razorpay, Cashfree, Pine Labs, PayU, BillDesk, Easebuzz, NTT DATA, Infibeam Avenues, Innoviti, EnKash, Open Financial).
   - *Target Size:* 30 Tier-1 and emerging payment aggregators.
2. **RBI Registered Digital Lending NBFCs:**
   - *Source:* RBI Department of Non-Banking Supervision registered NBFC list and Digital Lending Association of India (DLAI) member directory.
   - *Entities:* Lending fintechs and BNPL operators (e.g., KreditBee, MoneyTap, EarlySalary/Fibe, Slice, Uni, Jupiter Edge, LazyPay, Axio/Capital Float, Navi).
   - *Target Size:* 60 digital lending and credit companies.
3. **NPCI UPI Ecosystem Directory & PPI Wallet Licensees:**
   - *Source:* National Payments Corporation of India (NPCI) live UPI application participant lists and RBI Prepaid Payment Instrument (PPI) list.
   - *Entities:* Neobanks, prepaid card issuers, and wallet operators (e.g., Fi Money, Jupiter, Niyo, Freo, Cheq, FamPay, Decentro).
   - *Target Size:* 30 neobanks and consumer fintechs.
4. **Fast-Growing Marketplaces with Proprietary Payment Checkout:**
   - *Source:* Inc42 FinTech 100 directory and Tracxn Emerging Startups FinTech India.
   - *Entities:* High-volume e-commerce, mobility, quick-commerce, and gaming platforms running internal merchant aggregators (e.g., Dream11, Mobile Premier League, Zepto, Swiggy Instamart, Meesho, Porter).
   - *Target Size:* 40 marketplaces and digital platforms.
5. **Specialized Banking Technology Integrators & Risk Consultants:**
   - *Source:* NASSCOM DSCI Cybersecurity & FinTech SIG directory.
   - *Entities:* Specialized fintech software houses and consulting partners providing core risk modules.
   - *Target Size:* 40 firms.

### 3.2 Contact Discovery Method per Account
For each qualified account from the registries above:
- Identify 2 named individuals:
  - **Commercial / Risk Owner:** Head of Fraud, Chief Risk Officer (CRO), or Fraud Operations Manager.
  - **Technical Integrator:** VP Engineering, Principal Payment Architect, or Head of Platform.
- Record: Full Name, Title, LinkedIn URL, Corporate Email format (`first.last@company.com`), Primary Payment Rails (UPI / Card / NetBanking), and Estimated Transaction Volume bracket.

---

## 4. Structured Interview Script (Sections A through G)

Protocol: 30-minute structured video interview. Recorded only with explicit consent.

### Section A — Role and Context (5 min)
*Goal: Confirm ICP sizing ([ASM-INT-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md)) and operating environment.*
1. What is your exact title and what are your primary responsibilities in the fraud and risk organization?
2. How many people are on your risk/fraud team, and how is it split between rule writing, manual case review, and engineering?
3. What is your average monthly transaction volume, and what does peak TPS look like during festival sales or flash deals? ([ASM-INT-03](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md))
4. What payment rails make up your volume today: UPI (% share), Cards (% share), NetBanking, Wallets, BNPL/EMI? ([DR-API-07](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))

### Section B — Current State Architecture (5 min)
*Goal: Map rule evaluation architecture, latency constraints, and hashing feasibility.*
5. Walk me through the lifecycle of a transaction hitting your checkout: where does fraud screening happen (synchronously inline vs. asynchronous post-authorization)?
6. What is the hard timeout window your checkout allows for fraud evaluation before the payment gateway fails? Is 100 ms server-side acceptable? ([ASM-INT-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md))
7. Where do your rules execute today: hardcoded in microservices, database stored procedures, or a dedicated vendor?
8. How do you track velocity across user accounts or cards? Do you use Redis, in-memory counters, or database group-by queries?
9. When passing account identifiers to a risk engine, can your engineering team send a tenant-salted SHA-256 hash (`account_id_hash`) rather than raw account numbers? ([ASM-INT-08](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md))
10. Can your payment gateway or microservices reliably provide a monotonic sequence number per account, or does network retry scramble packet arrival order? ([ASM-INT-10](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md))

### Section C — Pain Depth and Rule Promotion (7 min)
*Goal: Validate the core problem: fear of breaking production rules and lack of historical simulation.*
11. How long does it currently take your team to go from identifying a new fraud trend to deploying a rule into production?
12. Walk me through the last time a rule change went wrong: did it cause an unexpected spike in legitimate checkout declines (false positives) or let fraud slip through? What was the business impact?
13. How do you validate that a new rule is safe *before* you deploy it today: do you run it in shadow mode, test it on a spreadsheet, or run SQL queries against data warehouses?
14. How long does it take your data team to run a retrospective query against 90 days of transaction logs to test a rule hypothesis?
15. What does one wrongly declined high-value transaction cost your company in merchant trust or customer churn?

### Section D — Operational Recovery and Fail Policies (3 min)
*Goal: Validate fail-open vs fail-closed policy assumptions.*
16. If your fraud evaluation engine experiences an infrastructure timeout or network glitch, what is your mandatory policy: **fail-open** (allow transaction through with degraded flag) or **fail-closed** (block payment)? ([ASM-INT-05](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md))
17. What is your team's requirement for reviewing historical decisions: how many days of live history must be instantly searchable in the console (30, 90, or 180 days)? ([ASM-INT-07](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md))

### Section E — Regulatory and Data Handling (3 min)
*Goal: Confirm DPDP, localisation, and PCI boundaries before Gate G1.*
18. Under India's DPDP Act 2023, does your legal compliance team classify transaction risk evaluation as a Data Processor relationship, provided no raw customer names, phone numbers, or PANs are shared? ([ASM-LEG-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md))
19. Does your organization require customer payment data and decision audit logs to reside exclusively in India (e.g. AWS Mumbai `ap-south-1`)? ([ASM-LEG-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md))
20. Does your compliance team allow sending card network tokens (Visa VTS, Mastercard MDES, RuPay tokenisation) to external vendors while keeping raw PAN out of scope? ([ASM-LEG-03](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md))

### Section F — Free Backtest Offer & GTM Validation (5 min)
*Goal: Test Decision D8 and willingness to import historical data.*
21. If we provided you with a self-service Dataset Import Tool that lets you upload 90 days of historical CSV/Parquet data (with account numbers hashed on your laptop and all raw PANs stripped), and delivered an automated Replay Comparison Report in 48 hours showing:
    - Every transaction your current system declined that was actually legitimate (false positives avoided)
    - Every fraudulent transaction your current system approved that our engine caught
    - Precise p99 evaluation latency under full load
    ...with zero code integration and zero cost, would you run that test? ([ASM-GTM-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md))
22. What format is your historical transaction data stored in today (PostgreSQL, Snowflake, BigQuery, AWS S3 Parquet)?
23. What legal hurdle or internal approval would you need to clear before uploading an anonymized 50,000+ transaction dataset?

### Section G — Commercials and Decision Ownership (2 min)
*Goal: Validate pricing model and buying center.*
24. How do you pay for fraud tools today: per-transaction evaluation fee, monthly platform fee, or per-seat analyst fee?
25. Who in your company signs off on fraud infrastructure purchases: CRO, VP Engineering, Chief Product Officer, or CEO?

---

## 5. Outreach Templates

All outreach is personalized, direct, and focused strictly on the Free Backtest value proposition.

### 5.1 Cold Email Template
```text
Subject: Testing fraud rule changes before shipping them at [Company Name]

Hi [First Name],

I noticed [Company Name] is scaling transaction volumes rapidly across UPI and card rails. 

Most risk leads I speak with share the same frustration: when a new fraud attack hits, writing the rule is easy, but verifying that it won't decline legitimate checkouts is terrifying. Testing rule changes against 90 days of history usually means waiting days for data engineering or guessing in staging.

We built Project Aegis specifically to solve this. It's a deterministic fraud decision engine with a historical replay tool: you can upload 90 days of historical transaction data and see exactly what fraud you missed and what false positives you would avoid—in under 48 hours, with zero code integration.

We are offering a free, confidential backtest to 5 mid-size Indian payment platforms this month. All data is client-side hashed and zero raw card data ever enters the system.

Open to a brief 15-minute chat this Thursday at 2:00 PM IST to see if this is relevant for [Company Name]?

Best regards,

[Founder Name]
Founder & Architect, Project Aegis
https://projectaegis.in
```

### 5.2 LinkedIn Direct Message Template (< 300 Characters)
```text
Hi [First Name] — saw your work leading risk at [Company Name]. We built a deterministic engine that lets fintechs test fraud rule changes against 90 days of history before deploying, cutting false declines. Offering a free 48h backtest for 5 teams this month. Worth a 10-min chat this week?
```

### 5.3 Follow-up Email Template (Day 4)
```text
Subject: Re: Testing fraud rule changes before shipping them at [Company Name]

Hi [First Name],

Following up on my note earlier this week. 

To give you an idea of what the free backtest produces: we ingest an anonymized CSV/Parquet export of your historical transactions (account numbers hashed on your side), run them through our evaluation engine in parallel with your existing ruleset, and hand you an executive comparison report detailing:
1. False positives avoided (revenue saved from wrongful declines)
2. Incremental fraud caught
3. Microsecond p99 latency verification

No live traffic integration, no production risk, and zero cost.

Do you have 10 minutes this Friday to review how the data import works?

Best regards,

[Founder Name]
```

---

## 6. The Free-Backtest One-Pager

This one-pager is distributed to prospective design partners following initial interest:

```text
================================================================================
PROJECT AEGIS — FREE HISTORICAL FRAUD BACKTEST PROGRAM
Stop Guessing on Fraud Rules. Verify Against 90 Days of History in 48 Hours.
================================================================================

THE PROBLEM:
Every time your risk team tweaks a fraud rule or velocity threshold, you take a 
calculated risk. If the rule is too strict, legitimate checkouts drop and revenue 
bleeds. If it is too loose, chargebacks spike. Today, testing rule changes against 
historical data requires slow SQL queries or staging guesswork.

THE OFFER:
Project Aegis provides a free, self-service historical backtest. Within 48 hours 
of receiving your historical dataset, we deliver an executive and technical report 
benchmarking your current fraud performance against modern deterministic rules.

WHAT YOU RECEIVE:
1. False Positive Reduction Audit: Exact count and volume of legitimate transactions 
   wrongfully declined by your current rules that Aegis would have approved.
2. Incremental Fraud Detection: Fraudulent payments your current system missed that 
   Aegis flagged.
3. Latency Verification: Server-side p50, p95, and p99 evaluation latency under load.
4. Rule Optimization Recommendations: Concrete CEL rule definitions tailored to 
   your merchant mix.

HOW DATA HANDLING WORKS (ZERO-TRUST & ZERO-PII):
- Complete Client-Side Protection: Our open import tool runs client-side hashing 
  on your accounts (tenant-salted SHA-256). You never send bank account numbers.
- Zero Raw PAN Invariant: Aegis strictly rejects any payload containing raw card 
  numbers (ISO 7812). Only card network tokens are accepted.
- Strict Data Isolation: Your dataset is isolated in a single-tenant storage bucket, 
  evaluated in a dedicated replay container, and completely crypto-shredded within 
  30 days with a signed Certificate of Deletion.
- Sovereign Residency: All processing occurs strictly in AWS Mumbai (ap-south-1).

ZERO INTEGRATION REQUIRED:
Upload historical CSV or Parquet files via our secure pre-signed import tool. 
Zero SDKs to deploy. Zero impact on live payment gateway checkouts.

Interested in claiming one of 5 free backtest slots?
Contact: founder@projectaegis.in | https://projectaegis.in
================================================================================
```

---

## 7. Interview Log Template

Every completed discovery conversation must be entered into this log within 24 hours of completion:

| Log ID | Date | Company Name | Persona / Title | Monthly Txn Volume | Primary Payment Rails | Current Fraud Tech Stack | Core Pain Identified | Latency Requirement | Fail Policy (Open/Closed) | Reaction to Free Backtest | Gate G1 Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **INT-001** | `YYYY-MM-DD` | *[Company]* | *[e.g., Head of Fraud]* | *[e.g., 4M txns/mo]* | *UPI (75%), Card (20%), NetBanking (5%)* | *In-house Postgres rules + manual Excel* | *Fear of false declines on festival sales* | *< 80 ms server p99* | *Fail-open (mandatory)* | *High; requested DPA draft* | Logged ([ASM-INT-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md)) |
| **INT-002** | `YYYY-MM-DD` | *[Company]* | *[e.g., VP Engineering]* | *[e.g., 8M txns/mo]* | *Cards (60%), UPI (40%)* | *Legacy third-party vendor* | *High per-txn vendor cost; no replay* | *< 100 ms server p99* | *Fail-open* | *Interested pending legal review*| Logged ([ASM-INT-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md)) |

---

## 8. Discovery Synthesis Template (Batch Evaluation)

This synthesis protocol is executed after **Interview 5**, **Interview 10 (Gate G1 Review)**, and **Interview 20**:

### 8.1 Quantitative Metric Rollup
- Total Interviews Completed: `[N / 10]`
- Target ICP Fit (% meeting 1M–10M volume and 1–5 risk team members): `[%]`
- Primary Payment Rail Breakdown: `UPI: __%, Card: __%, NetBanking: __%, Other: __%`
- Average Stated p99 Latency Budget: `[__ ms]`
- Fail Policy Consensus: `Fail-Open: __%, Fail-Closed: __%`
- Backtest Conversion Intent (% agreeing to upload historical dataset): `[%]`

### 8.2 Qualitative Pattern Extraction
1. **Dominant Pain Pattern:** Summary of exact words used by risk managers describing rule deployment anxiety.
2. **Objection Patterns:** Top regulatory, technical, or commercial concerns raised regarding the dataset import tool.
3. **Assumptions Validated vs. Refuted:**
   - Review each item in [assumptions-register.md](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) (`ASM-INT-01` through `ASM-INT-12`).
   - Move status from `Open` to `Validated` or `Refuted` with explicit citations to Log IDs.
4. **Product Scope Adjustments:** Required modifications to [03-prd.md](file:///c:/dev/aegis/docs/03-prd.md) v1.1.
