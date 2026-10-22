# Product Requirements Document — Stage 1

**Document:** 03  
**Status:** Working Draft (Build proceeds on documented assumptions; validation is a parallel track per Decision D2)  
**Owner:** Decision DR-PRD-01 (Founder / Lead Product Architect)  
**Last updated:** October 2026

---

> **Validation Gates Note (Decision D2):** Build proceeds on documented assumptions without blocking on customer discovery. Two gates govern customer progression:
> - **Gate G1 (Pre-Customer Data):** Before ANY real customer data enters the system, 10+ discovery interviews must be completed and legal review of data protection (DPDP Act 2023), India data localisation, PCI DSS scope minimisation, and Data Processing Agreements (DPA) must be finalized.
> - **Gate G2 (Pre-Paid Contract):** Before signing a paid commercial contract, a formal pilot Statement of Work (SOW) must be executed and customer security questionnaire responses must be verified. Customer validation is never claimed before the respective gate is passed.

---

## 1. Executive Summary & Product Vision

Project Aegis is a real-time, explainable transaction decision engine designed for mid-size Indian fintechs, payment aggregators, and digital lenders. It evaluates transactions against expressive rule sets, returns deterministic verdicts (`APPROVE`, `DECLINE`, `REVIEW`) in under 25 ms p50 / 100 ms p99, and provides an authoritative replay engine to simulate rule revisions against historical data before deployment.

---

## 2. Core Go-To-Market Feature: Free Backtest & Dataset Import (Decision D8)

To accelerate enterprise customer acquisition and overcome adoption hesitation, Aegis offers a **Free Historical Backtest** as its primary go-to-market wedge:
- Prospective risk teams can upload historical transaction datasets (CSV or Parquet).
- Users map their existing database/event columns to canonical Aegis event fields.
- An automated validation engine verifies schema conformance, validates timestamps, and asserts zero raw PAN ingestion.
- The replay engine executes a candidate ruleset against the historical data and generates a **Backtest Comparison Report** showing:
  1. Fraud catch rate improvements (additional true positives caught).
  2. False positive reduction (unnecessary customer declines avoided).
  3. Latency simulation (p50, p95, p99 microsecond execution profiles).
  4. Decision transition matrix (Approve $\to$ Decline, Decline $\to$ Approve).

### 2.1 Dataset Import Requirements & Acceptance Criteria
1. **File Ingestion:** Supports multipart or presigned direct S3 upload of CSV (gzipped or uncompressed) and Apache Parquet files up to 5 GB / 5,000,000 rows.
2. **Column Mapping Engine:** Interactive visual mapper and JSON schema contract (`POST /v1/datasets/import`) mapping raw headers to `event_id`, `event_time`, `amount`, `currency`, `account_id_hash`, `mcc`, `device_id`, and ground-truth fraud flags.
3. **Automated Validation Report:**
   - Emits total row count, valid row count, parse error count, and timestamp distribution.
   - Enforces automated PAN scanning: rejects any file containing Luhn-valid 13–19 digit credit/debit card numbers in free-text fields.
4. **Anonymisation Enforcement:** Ingestion requires client-side pseudonymisation (`account_id_hash`) and strips personal customer data (names, phone numbers, email addresses).
5. **Tenant & Storage Isolation:** Each import session is isolated in PostgreSQL under Row-Level Security (`dataset_imports`) and dedicated per-tenant S3 prefixes.
6. **Data Retention & Auto-Purge:** Imported datasets are automatically deleted after 30 days unless promoted to a paid pilot under Gate G2.

---

## 3. Stage 1 Scope — Core Capabilities

1. **Synchronous Decision Engine:** `POST /v1/decisions` returning verdict, risk score (0.0 to 100.0), and fired rule explainability list.
2. **Deterministic CEL Rule DSL:** Google Common Expression Language engine evaluating 50–200 rules in < 10 ms with static AST validation and ReDoS safeguards.
3. **Atomic Velocity Counters:** Redis Lua script implementing sliding-window aggregation with event-idempotent deduplication (ADR-0005).
4. **Deterministic Replay Engine:** Batch worker evaluating historical transaction streams using injected event-time clocks without live state mutation (ADR-0003).
5. **Cryptographic Audit Log:** Dual-mode audit architecture: synchronous hash-chaining for management changes and Merkle tree batch sealing for decision rows (ADR-0004).
6. **Web Console:** Dark-mode keyboard-first analyst interface with live stream, case review drawer, Monaco rule editor, and replay diff comparisons.
7. **Three Deployment Tiers (Decision D1):** Tier 0 (local Docker Compose, RAM budget $\le 3.89$ GB), Tier 1 (single-AZ AWS shadow pilot), Tier 2 (multi-AZ enterprise production).

---

## 4. Known Non-Goals for Stage 1

The following will not be built in Stage 1 regardless of discovery outcomes:
- Moving money or payment processing
- KYC orchestration
- Chargeback handling or dispute management
- Black-box ML models (rules engine only in Stage 1)
- Cross-tenant signal sharing
- Enterprise SSO / SCIM provisioning (Tier 2 only; Tier 0/1 uses in-house Spring Security module per Decision D4)
- Mobile SDK
