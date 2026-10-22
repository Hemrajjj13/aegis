# Compliance Posture and Regulatory Alignment

**Document:** 13  
**Status:** Approved (docs v1.0, assumption-based)
**Owner:** Founder / Lead Architect ([DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

---

> ### NON-LEGAL DISCLAIMER
> **This document contains technical, architectural, and operational specifications designed to align with regulatory frameworks and industry security standards. This document does NOT constitute formal legal advice, a certified legal opinion, or an accredited compliance audit.** 
> 
> All regulatory classifications (including DPDP Act 2023 roles, RBI payment directives, and PCI DSS scoping) are documented engineering hypotheses. Formal legal review by qualified Indian technology counsel is mandatory prior to **Gate G1** (before real customer data enters the system), and external Qualified Security Assessor (QSA) assessment is required prior to **Gate G2** (commercial contracts).

---

## 1. Purpose and Regulatory Scope

Project Aegis is a real-time transaction decision engine serving Indian financial technology companies, digital lenders, and payment aggregators. Because the platform evaluates payment events in real time and processes high-throughput financial signals, compliance is engineered into the system architecture rather than audited retroactively.

Consistent with Decision **D2** and the [Assumptions Register](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md), all specifications in this document are **designed to align with** the following regulatory frameworks:
1. **Digital Personal Data Protection Act, 2023 (DPDP Act 2023):** Standards for processing personal data of Indian residents, consent boundaries, and data processor obligations.
2. **RBI Circular on Storage of Payment System Data (April 2018 / 2021):** Mandates that all payment system providers store end-to-end payment data exclusively in India.
3. **RBI Master Directions on Information Technology Governance and Risk Controls (November 2023):** Standards for operational cyber resilience, audit trails, and dual custody.
4. **PCI DSS v4.0 (Payment Card Industry Data Security Standard):** Architectural controls designed to minimize and eliminate cardholder data environment (CDE) audit scope.

---

## 2. Technical Control Mappings (Designed to Align With)

### 2.1 PCI DSS Scope Minimisation (Zero-PAN Invariant)
*Baseline Assumption: [ASM-LEG-03](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) (Gate G1 review)*

Project Aegis is engineered with a strict **Zero-Raw-PAN Architecture** designed to place the platform outside the scope of PCI DSS Level 1 Service Provider audits (targeting SAQ A or heavily scoped-down SAQ D):

| Control Requirement | Architectural Implementation | Verification Method |
|---|---|---|
| **No Ingestion of Primary Account Numbers (PAN)** | API schema ([doc 06 §2](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md#componentsschemasEvent)) strictly prohibits ISO 7812 13–19 digit PANs. Numeric identifiers are typed as integers or UUIDs; string fields are constrained and typed. | Automated OpenAPI schema validation in API Gateway / Decision API. |
| **Secondary PAN Filtering on Free-Text** | Any string field (`merchant_name`, `custom_fields`) is scanned for 13–19 digit sequences passing the Luhn algorithm before evaluation. Matching payloads return HTTP 400 (`code: RAW_PAN_PROHIBITED`). | CI unit & security test: `PanPreventionSecurityTest` ([doc 11 §1](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md#1-purpose-and-engineering-principles)). |
| **Token-Only Card Identification** | Aegis accepts only card network tokens (Visa Token Service, Mastercard MDES, RuPay Tokenisation) or merchant card tokens (`card_token` string up to 128 chars, [DR-API-06](file:///c:/dev/aegis/docs/00-decisions/decision-register.md)). | Data model constraint in `decisions` and `dataset_imports` ([doc 07 §3](file:///c:/dev/aegis/docs/07-data-model.md#3-table-ddl-specifications)). |
| **Zero Cardholder Data Storage** | Neither the database, Redis caches, Kafka logs, nor S3 backups contain card numbers, CVVs, expiration dates, or PIN blocks. | Automated CI database schema static inspection script. |

### 2.2 DPDP Act 2023 Alignment (Data Processor Posture)
*Baseline Assumption: [ASM-LEG-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) (Gate G1 review)*

The technical architecture is structured under the assumption that Aegis operates as a **Data Processor** for customer payment events, while operating as a **Data Fiduciary** only for console operator credentials:

| DPDP Principle | Architectural Implementation | Technical Reference |
|---|---|---|
| **Pseudonymisation & Data Minimisation** | Callers compute client-side tenant-salted SHA-256 hashes (`account_id_hash`, `vpa_hash`) before transmitting payloads. Cleartext bank account numbers and phone numbers are never persisted. | [DR-API-05](file:///c:/dev/aegis/docs/00-decisions/decision-register.md), [DR-API-08](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) |
| **Data Principal Rights & Erasure** | Tenant offboarding triggers automated cryptographic deletion (crypto-shredding). The tenant's dedicated KMS key is scheduled for destruction, rendering all S3 Parquet backups unrecoverable. | [doc 07 §9.2](file:///c:/dev/aegis/docs/07-data-model.md#92-tenant-offboarding-and-crypto-shredding), [ADR-0004](file:///c:/dev/aegis/docs/adr/0004-batched-audit-log-hash-chaining.md) |
| **Strict Purpose Limitation** | Customer datasets imported for backtesting are stored in dedicated S3 prefixes under Row-Level Security (`dataset_imports`) and automatically purged after 30 days. | [Decision D8](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md), [ADR-0008](file:///c:/dev/aegis/docs/adr/0008-dataset-import-for-backtests.md) |
| **Notice & Consent Boundaries** | Customer pilot agreements require tenants to warrant that their privacy policies and user notices cover fraud prevention processing by third-party processors. | Customer Pilot SOW ([doc 04b §7](file:///c:/dev/aegis/docs/04-sow-templates/customer-pilot-sow.md#7-security-privacy-and-data-handling)) |

### 2.3 RBI Payment Data Localisation
*Baseline Assumption: [ASM-LEG-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) (Gate G1 review)*

Designed to align with the RBI Circular on Storage of Payment System Data (April 2018):
- **Exclusive Indian Data Residency:** All production components—PostgreSQL primary and replicas, Redis caches, Kafka brokers, and S3 Glacier archives—are provisioned exclusively in the **AWS Mumbai (`ap-south-1`) region** ([doc 14 §2](file:///c:/dev/aegis/docs/14-deployment-and-iac.md#2-cloud-topology-aws-ap-south-1)).
- **Zero Cross-Border Replication:** System backups and WAL logs are never replicated across international boundaries.
- **Local Developer Isolation:** Developer laptops in Tier 0 operate strictly on synthetic datasets; no live payment records or historical customer imports may enter Tier 0 (Decision D5).

### 2.4 Auditability and Tamper-Evidence
Designed to align with RBI Master Directions on Information Technology Governance:
- **Merkle Batch Sealing:** Decision records are batched every 30 seconds by a background sealer, hashed into SHA-256 Merkle trees, and chained to previous batch roots ([ADR-0004](file:///c:/dev/aegis/docs/adr/0004-batched-audit-log-hash-chaining.md)).
- **WORM Storage Anchoring:** Daily Merkle batch chain anchors are exported to AWS S3 Object Lock in **Compliance WORM mode** ([DR-SEC-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md)). Records cannot be overwritten or deleted even by root AWS accounts during the retention period.
- **Synchronous Administrative Chaining:** All console administrative actions (logins, rule changes, permission adjustments) are synchronously hash-chained directly in the database commit ([doc 01 §9.2](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#92-tamper-evidence)).

### 2.5 Access Control and Dual Custody
- **Role-Based Access Control (RBAC):** 5 strictly enforced application roles (`admin`, `risk_manager`, `risk_analyst`, `auditor`, `developer`) and 6 database service roles ([doc 07 §2](file:///c:/dev/aegis/docs/07-data-model.md#2-database-roles-and-privilege-matrix)).
- **Maker-Checker Rule Promotion:** Enforces dual custody on all rule deployments. A rule author cannot approve or deploy their own rule ([doc 09 §6](file:///c:/dev/aegis/docs/09-rule-dsl-spec.md#6-rule-lifecycle-and-maker-checker-dual-control)).
- **Multi-Factor Authentication (MFA):** Mandatory RFC 6238 TOTP authenticator verification on all console logins ([DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md), [ADR-0007](file:///c:/dev/aegis/docs/adr/0007-identity-behind-a-port.md)).

---

## 3. Legal Review Brief (Gate G1 Prerequisite)

Prior to allowing ANY customer data into Project Aegis (satisfying **Gate G1**), the founder will retain an Indian technology and regulatory counsel to execute this formal review.

### 3.1 Objective and Scope of Engagement
- **Engagement Type:** Fixed-fee advisory and contract drafting engagement.
- **Counsel Specialization:** Indian Information Technology, Data Privacy (DPDP Act), and Financial Regulations (RBI / FinTech).
- **Core Deliverables Required from Counsel:**
  1. Formal Legal Opinion on DPDP Act classification (Data Fiduciary vs. Data Processor).
  2. Review and finalization of the Aegis Standard **Data Processing Agreement (DPA)** template.
  3. Review of the Customer Pilot Statement of Work ([doc 04b](file:///c:/dev/aegis/docs/04-sow-templates/customer-pilot-sow.md)), focusing on shadow-mode liability limitations.
  4. Written opinion on RBI Payment Data Localisation compliance for cloud architectures within AWS Mumbai.

### 3.2 Exact Questions to Ask Counsel

#### Question 1: DPDP Act 2023 Classification
> *"Under the Digital Personal Data Protection Act, 2023, does Aegis act as a **Data Processor** on behalf of the payment company (Data Fiduciary) when evaluating hashed transaction payloads (`account_id_hash`, `vpa_hash`, `card_token`, amount, MCC)? What specific mandatory representations, warranties, and audit rights must be included in our bilateral DPA to insulate Aegis from direct regulatory fines under Section 8 of the Act?"*

#### Question 2: RBI Payment Data Localisation & Historical Replay
> *"Does the RBI Circular on Storage of Payment System Data (April 2018) permit an Indian payment aggregator to share anonymized historical transaction logs with Aegis for offline backtesting and shadow-mode evaluation, provided the data is hosted entirely in AWS Mumbai (`ap-south-1`)? Does the circular require regulatory notification before running shadow evaluations?"*

#### Question 3: PCI DSS Scope Minimisation & Token Storage
> *"Given that Aegis's API schema rejects raw PANs and processes only network tokens (Visa VTS, Mastercard MDES, RuPay) and merchant tokens, does Aegis qualify for SAQ A or a scoped-down SAQ D? Does accepting tokens issued by Indian banks impose any direct PCI DSS certification obligations on a bootstrapped vendor?"*

#### Question 4: Free Backtest Data Transfer & Anonymisation Standards
> *"Does client-side salting and SHA-256 hashing of account identifiers qualify as 'anonymisation' or 'pseudonymisation' under Indian law? In our Free Backtest program, what explicit customer representations must be captured before ingesting a 90-day historical CSV/Parquet dataset?"*

#### Question 5: Limitation of Liability in Shadow Pilots
> *"In our Customer Pilot SOW, we specify that Aegis runs in 'shadow mode only' (zero live payment blocking) and cap liability to the total fees paid (which is zero during an unpaid trial). Are there any statutory provisions under the Indian Contract Act, 1872 or Consumer Protection Act that would invalidate this liability cap in the event of an alleged data breach or system downtime?"*

### 3.3 Dossier of Documents to Bring to Counsel
The founder will provide counsel with the following architectural artifacts:
1. **API Schema Contract:** [`docs/06-api-and-event-schemas.md §2`](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md#2-openapi-310-specification) (proving typed schema and zero-PAN validation).
2. **Database Data Model:** [`docs/07-data-model.md §3`](file:///c:/dev/aegis/docs/07-data-model.md#3-table-ddl-specifications) (proving tenant-salted hashes and RLS isolation).
3. **Threat Model & Security Controls:** [`docs/08-threat-model.md §3`](file:///c:/dev/aegis/docs/08-threat-model.md#3-stride-threat-analysis-per-container) (demonstrating encryption at rest and in transit).
4. **Draft Customer Pilot SOW:** [`docs/04-sow-templates/customer-pilot-sow.md`](file:///c:/dev/aegis/docs/04-sow-templates/customer-pilot-sow.md).
5. **Draft Bilateral DPA Outline:** Outlined in [`docs/15-go-to-market.md §7`](file:///c:/dev/aegis/docs/15-go-to-market.md#7-data-processing-agreement-dpa-outline).

---

## 4. Compliance Gate Verification Lifecycle

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            BUILD TRACK (TIER 0)                             │
│       Synthetic Generator only. Zero real customer data. Zero PCI scope.     │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ GATE G1: PRE-CUSTOMER DATA ENTRY                                            │
│ [ ] 10+ completed discovery interviews logged (doc 02 §7)                   │
│ [ ] Written legal counsel review of DPDP Act & RBI localisation complete    │
│ [ ] Qualified Security Assessor (QSA) scope review of zero-PAN posture      │
│ [ ] Bilateral Data Processing Agreement (DPA) finalized                     │
│ [ ] Dataset Import Tool automated PAN scanning verified in staging          │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ PILOT SHADOW MODE & FREE BACKTESTS (TIER 1)                                 │
│ Traffic mirrored asynchronously; zero customer payment blocking             │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ GATE G2: PRE-PAID COMMERCIAL CONTRACT                                       │
│ [ ] Signed Customer Pilot SOW with approved commercial terms                │
│ [ ] Customer enterprise security questionnaire verified and approved        │
│ [ ] External CERT-In accredited penetration test completed (zero P1s)       │
│ [ ] SOC 2 Type I readiness audit initiated (target 9–12 months post-launch) │
└─────────────────────────────────────────────────────────────────────────────┘
```
