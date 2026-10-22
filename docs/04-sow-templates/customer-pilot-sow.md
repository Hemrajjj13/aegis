# Customer Pilot — Statement of Work Template

**Document:** 04b  
**Status:** Stub — requires legal review before use with any customer  
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026

> **This is a template, not a signed agreement.**
> Every field in ALL CAPS must be filled in per customer.
> This document must be reviewed by legal counsel before use.
> The pilot SOW must be accompanied by a signed Data Processing Agreement (DPA).

---

## 1. Objectives and success metrics

This pilot runs in **shadow mode only**. No live approve/decline decisions
are sent to any payment processor, banking system, or customer-facing flow
during the pilot. The pilot produces a comparison report.

Success is defined as:

| Metric | Definition | Measured how |
|--------|-----------|--------------|
| Replay report delivered | A report comparing Aegis decisions against CUSTOMER's current system on CUSTOMER's historical data | Report delivered by DATE |
| False positives avoided | Transactions Aegis would approve that CUSTOMER's system declined and that were not fraud | Requires CUSTOMER's fraud labels |
| Fraud caught | Transactions Aegis would flag that CUSTOMER's system approved and that were fraud | Requires CUSTOMER's fraud labels |
| p99 decision latency | Server-side latency at p99 for all decisions in the replay run | Reported in the replay output |

**Assumption (Gate G1):** If CUSTOMER provides historical fraud labels, a full precision/recall and false-positive reduction matrix is generated. If labels are absent, the report evaluates changed decisions, rule hit rates, and score distributions without ground-truth classification (Assumption [ASM-INT-04](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md)).

## 2. Scope

### In scope

- Ingestion of CUSTOMER-provided anonymized historical transaction data (see §5)
- Replay run comparing CUSTOMER's existing rule configuration against
  at least one Aegis ruleset built collaboratively during the pilot
- One replay comparison report
- Up to NUMBER hours of Aegis team time for integration support and results walkthrough

### Explicitly out of scope

- Live decisions (no integration to CUSTOMER's payment processing path)
- Case management, analyst workflow, or console access beyond the replay report screen
- Ongoing rule tuning or managed service
- Any guarantee of fraud-detection improvement over the current system
- Any chargeback liability or indemnification
- Data enrichment from third-party sources
- Machine learning models

## 3. Deliverables and acceptance criteria

| Deliverable | Acceptance criterion | Due |
|-------------|---------------------|-----|
| Data ingestion confirmed | Aegis confirms receipt and schema validation of CUSTOMER data with row count | DATE |
| Draft ruleset | At least RULE_COUNT rules drafted and reviewed with CUSTOMER's risk team | DATE |
| Replay run complete | run_id logged; no errors; results written to replay tables | DATE |
| Report delivered | Report includes all metrics in §1; reviewed in a call with CUSTOMER | DATE |

## 4. Milestones and timeline

| Week | Activity |
|------|----------|
| 1 | Data transfer (see §5); schema validation; kickoff call |
| 2 | Rule drafting with CUSTOMER's risk team |
| 3 | Replay run; preliminary results |
| 4 | Report delivered; results walkthrough call |

Total pilot duration: 4 weeks from data receipt.

**Assumption:** Standard pilot duration is 4 weeks from dataset validation (Assumption [ASM-GTM-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md)).

## 5. Environments and data

### What CUSTOMER provides

| Item | Format | Sensitivity |
|------|--------|------------|
| Historical transaction records | Newline-delimited JSON per Aegis event schema | See DPA |
| Fraud labels (if available) | Boolean field per transaction or a separate label file keyed on transaction_id | Confidential |
| Date range | Minimum 90 days of history recommended | — |

CUSTOMER is responsible for anonymizing data before transfer:
- No raw PANs, customer names, email addresses, or mobile numbers in the transfer.
- Card identifiers must be tokenised or hashed before transfer.
- Aegis will reject any payload containing a Luhn-valid 13–19 digit number.

**Specification (Decision D8):** Data transfer occurs exclusively via the Aegis Dataset Import Tool ([doc 06 §8](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md#8-dataset-import-api-and-schema-mapping-specification-decision-d8)) using pre-signed S3 upload URLs with client-side salting. Cleartext account numbers or raw PANs are strictly rejected.

### What Aegis provides

- A dedicated staging environment for this pilot (isolated from other tenants)
- A sandbox API key for schema validation
- The replay report in PDF and newline-delimited JSON
- Deletion confirmation within 30 days of pilot completion (see §7)

## 6. Assumptions and dependencies

- CUSTOMER has signed the DPA before any data is transferred
- CUSTOMER's data contains at least 50,000 transactions for the replay to be statistically meaningful (baseline hypothesis to validate with pilot customers)
- CUSTOMER's fraud labels cover at least 30% of the dataset
- CUSTOMER has a named point of contact available for a 1-hour call in each of weeks 1–4
- Aegis team has completed the production-readiness gate (doc 01 §12) before this pilot begins

## 7. Security, privacy, and data handling

All data handling follows the signed DPA. Summary:
- Data is stored in India (AWS ap-south-1 or ap-south-2) only
- Data is encrypted at rest and in transit (TLS 1.3)
- Access is restricted to named Aegis personnel listed in the DPA
- Data is deleted within 30 days of pilot completion; deletion confirmed in writing
- No CUSTOMER data is used for any purpose other than this pilot
- No CUSTOMER data is shared with any third party

## 8. Roles and responsibilities

| Role | CUSTOMER | Aegis |
|------|---------|-------|
| Data export and anonymization | CUSTOMER | — |
| Rule drafting | Provides fraud patterns and domain knowledge | Implements in rule DSL |
| Integration support | Named contact for questions | Solutions engineer |
| Report review | Risk Manager or Head of Fraud | Founder or PM |
| Legal sign-off | CUSTOMER legal | Aegis legal |

## 9. Change control

Changes to scope require a written amendment signed by both parties.
Timeline changes require written agreement from both parties.
No scope addition will be processed without a signed amendment and
an updated DPA if the data handling changes.

## 10. Fees, payment terms, and exit

**Pilot fee:** Free backtest and shadow evaluation offered in exchange for anonymised case study rights and feedback (Decision D8, Assumption [ASM-GTM-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md)); commercial conversion is governed by Gate G2.

**Case study:** if results are positive, CUSTOMER grants Aegis the right to
publish an anonymised case study (no CUSTOMER name without explicit written
consent).

**Exit:** either party may terminate the pilot with 7 days' written notice.
On termination, Aegis deletes CUSTOMER data within 30 days and provides
a deletion confirmation. CUSTOMER retains the replay report.

**Data return:** CUSTOMER may request a copy of the derived ruleset and
the replay report at any time during or after the pilot. Raw transaction
data is not returned (CUSTOMER holds the original).

**Limitation of liability:** Aegis's liability under this SOW is limited
to the pilot fee paid. Aegis does not warrant that results from the pilot
will be replicated in production. The pilot is shadow mode; no live fraud
decisions are affected.

> **Legal note:** These liability and indemnity terms are subject to formal counsel review prior to Gate G2 commercial execution (Assumption [ASM-LEG-04](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) / Gate G2).

---

## 11. Master Pilot Agreement Outline (Legal Framework)

This outline defines the standard clauses of the bilateral **Master Pilot Agreement** accompanying this Statement of Work and the Data Processing Agreement (DPA), submitted to legal counsel at Gate G1 ([Doc 13 §4](file:///c:/dev/aegis/docs/13-compliance-posture.md)):

### 11.1 Preamble and Relationship of Parties
- **Parties:** Project Aegis legal entity (Service Provider) and Customer (Design Partner).
- **Independent Contractors:** Neither party is an agent, partner, or joint venturer of the other.

### 11.2 Shadow Mode Covenant (Zero Live Payment Interference)
- **Non-Interference Warranty:** The Service Provider covenants that the Aegis software operates strictly in **shadow mode** (offline batch replay or asynchronous Kafka event mirroring).
- Under no circumstances shall Aegis transmit synchronous blocking signals (`DECLINE` or `CANCEL`) to the Customer's live acquiring banks, card networks, or payment gateways during the Pilot.

### 11.3 Intellectual Property Rights
- **Aegis IP:** Aegis retains all right, title, and interest in the core evaluation platform, algorithms, CEL parser, deterministic replay engine, and console software.
- **Customer IP:** Customer retains sole ownership of its raw transaction logs, customer profiles, and ground-truth fraud labels.
- **Derived Rulesets:** Rules authored specifically for the Customer's transaction patterns are licensed non-exclusively and perpetually to the Customer for internal fraud prevention.

### 11.4 Confidentiality and Anonymized Case Study Rights
- **Confidential Information:** All customer data, benchmark metrics, and security documentation are deemed Confidential Information.
- **Publicity & Attribution:** Aegis may publish technical performance metrics and fraud prevention deltas strictly in an **anonymized case study format** ([Doc 16 §6](file:///c:/dev/aegis/docs/16-case-studies.md)). Customer names, trademarks, and identifiable business statistics are withheld unless express written consent is granted by Customer's authorized signatory.

### 11.5 Data Protection & Indian Regulatory Alignment
- **DPDP Act 2023:** Aegis acts strictly as a **Data Processor** processing personal data solely upon documented instructions of the Customer (Data Fiduciary).
- **Data Residency:** All customer transaction records and audit logs are hosted strictly in AWS Mumbai (`ap-south-1`), complying with RBI storage-of-payment-system-data mandates.
- **Zero Raw PAN Invariant:** Customer agrees not to transmit raw Primary Account Numbers; Aegis is indemnified against accidental PAN transmissions subject to immediate filter rejection.

### 11.6 Term, Termination, and Certified Deletion
- **Duration:** 4 weeks from confirmed ingestion of the initial dataset.
- **Convenience Termination:** Either party may terminate the agreement upon 7 calendar days' written notice.
- **Data Purge:** Upon pilot termination or expiry, Aegis shall purge all Customer data partitions from PostgreSQL and S3 within 30 days and provide an executive Officer Certificate of Data Destruction.

### 11.7 Liability Cap and Transition to Commercial Agreement
- **Mutual Liability Cap:** Total cumulative liability arising out of or related to this pilot agreement is capped at ₹50,000 (or the nominal pilot fee paid).
- **Commercial Transition:** Upon successful validation of pilot success metrics (§1), Customer maintains a right of first offer to transition to an Annual Enterprise Subscription under pre-agreed design partner preferential pricing ([Doc 15 §3](file:///c:/dev/aegis/docs/15-go-to-market.md)).

