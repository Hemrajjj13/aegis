# Product Requirements Document (PRD) — Project Aegis Stage 1

**Document:** 03  
**Status:** Draft-complete (docs v1.0, machine-checked, not yet reviewed)
**Owner:** Founder / Lead Product Architect ([DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

---

## 1. Purpose and Governance

This document establishes the comprehensive functional and operational requirements for Project Aegis Stage 1. Project Aegis is a real-time, explainable transaction decision engine tailored for Indian fintechs, payment aggregators, and digital lenders.

### 1.1 Parallel Validation Protocol (Decision D2)
Stage 1 engineering proceeds strictly on documented engineering baselines while customer discovery runs in parallel:
- **Gate G1 (Pre-Customer Data Entry):** 10+ completed discovery interviews, legal review of DPDP Act 2023, data localisation, PCI scope, and DPA execution. **Version 1.1 of this PRD is triggered and due immediately upon passing Gate G1.**
- **Gate G2 (Pre-Paid Contract):** Signed pilot SOW, verified enterprise security questionnaires, and external penetration test pass.

---

## 2. Problem Statement & Value Proposition

### 2.1 The Problem in Indian Fintechs
1. **Rule Change Fear & Revenue Bleed:** Risk teams identify emerging fraud attacks within hours, but deploying a new rule takes days or weeks because teams fear false declines. Wrongfully declining a legitimate UPI or credit card transaction causes direct merchant revenue loss and permanent user churn.
2. **Absence of Historical Replay:** Risk teams have no tooling to test a new rule or threshold against 90 days of historical transactions before deploying. Testing is reduced to slow manual SQL queries on data warehouses or guessing in production shadow modes.
3. **Checkout Latency Bottlenecks:** Modern Indian payment rails (UPI, IMPS, RuPay) require sub-second checkout loops. Existing third-party fraud vendors add 150–400 ms of network overhead, causing upstream payment gateways to time out.
4. **Data Sovereignty & Zero-Trust PII Liability:** The DPDP Act 2023 and RBI payment data localisation directives impose heavy penalties for transmitting cleartext account data or storing payment records outside India.

### 2.2 The Aegis Solution
Project Aegis provides:
- A high-throughput decision engine returning deterministic verdicts (`APPROVE`, `DECLINE`, `REVIEW`) in $< 25\text{ ms}$ p50 and $< 100\text{ ms}$ p99.
- A deterministic historical **Replay Engine** that backtests candidate rules against millions of past transactions in minutes.
- A self-service **Dataset Import Tool** allowing prospective customers to upload historical CSV/Parquet files with client-side salting for a free backtest comparison report (Decision D8).
- A zero-raw-PAN, zero-PII architecture storing all records natively in AWS Mumbai (`ap-south-1`).

---

## 3. Personas and Target Users

| Persona | Canonical Role & Context | Primary Jobs to Be Done | Key Pain Points |
|---|---|---|---|
| **Priya**<br>*(Fraud Analyst)* | `risk_analyst`: Operates case review queue; reviews suspicious transactions flagged for manual inspection. | Quick triage of flagged payments; wants unambiguous reason codes and fired rule weights; requires sub-second queue filtering. | Slow UI, obscure risk scores without explanations, having to check 3 different screens to understand a verdict. |
| **Arjun**<br>*(Rule Author)* | `rule_author`: Dual-control maker who drafts and simulates fraud detection logic; manages velocity thresholds and blocklists. | Formulate CEL rules, test against historical datasets, simulate threshold changes, submit draft rulesets for peer review. | Fear of false declines; inability to backtest without data engineering help; slow release cycles. |
| **Vikram**<br>*(Head of Fraud / CRO)* | `risk_manager`: Dual-control checker accountable for fraud loss rate, false-positive decline rate, and payment gateway approval ratios. | Review aggregate false-positive vs fraud-loss trade-offs; approve and promote production rules; audit compliance logs. | Hard to prove ROI of risk tooling; high per-transaction costs of legacy vendors; fear of regulatory non-compliance. |
| **Rahul**<br>*(Platform Engineer)* | `developer`: Integrates Aegis into the payment checkout pipeline and maintains high-availability microservices. | Integrate clean SDKs; ensure $< 100\text{ ms}$ p99 latency; verify zero-downtime rule updates; configure fail-open policies. | Upstream gateway timeouts; complex distributed state; lack of idempotency guarantees during retries. |
| **Ananya**<br>*(Compliance & Audit Officer)* | `auditor`: Reviews adherence to RBI directives, DPDP Act 2023, and internal risk policies. | Export tamper-evident audit trails; verify zero PAN storage; ensure timely crypto-shredding of historical data. | Untracked rule promotions; altered database logs; vendor data stored outside India. |
| **Neha**<br>*(Tenant Administrator)* | `admin`: Manages tenant user provisioning, API credential lifecycle, environment toggles, and operational configuration. | Manage team invitations, rotate API keys, configure sandbox vs production environments. | Manual onboarding friction, credential leakage risks, lack of auditability for administrative operations. |
| **Kabir**<br>*(Fintech Founder / Tenant Owner)* | `owner`: Accountable for organizational billing, enterprise subscription tier, root access, and tenant deletion. | Oversee billing usage, manage tenant lifecycle, authorize break-glass actions. | Opaque vendor pricing, runaway transaction costs, vendor lock-in. |

---

## 4. Stage 1 Scope Definition

### 4.1 In-Scope (Stage 1)
- **Synchronous Decision API:** `POST /v1/decisions` supporting single payment evaluation.
- **Asynchronous Ingest Path:** Kafka consumer path for shadow-mode traffic mirroring.
- **Evaluation Core:** Java 21 engine compiling and evaluating Google CEL expressions.
- **Atomic Velocity Tracking:** Sliding-window counters backed by Redis Lua (ADR-0005).
- **Deterministic Replay Engine:** Batch engine backtesting historical events using injected event-time clocks (ADR-0003).
- **Dataset Import & Backtest Pipeline:** Web upload of CSV/Parquet files with column mapping, automated validation, and comparison reporting (Decision D8, ADR-0008).
- **Rule Lifecycle & Maker-Checker:** Draft, simulate, review, approve, and canary rollout states.
- **Cryptographic Audit Trail:** Synchronous hash-chaining for admin events; Merkle batch sealing for decisions (ADR-0004).
- **Web Console:** React 18 SPA (Live Feed, Case Review, Rule Editor, Replay Comparison, Dataset Importer).
- **In-House Identity & RBAC:** Spring Security module with JWT, Argon2id, TOTP, and dev notification provider (Decision D4, ADR-0007).
- **Three Deployment Tiers:** Tier 0 (local Compose), Tier 1 (AWS pilot shadow), Tier 2 (AWS production) (Decision D1, ADR-0006).

### 4.2 Out-of-Scope (Stage 1 Non-Goals)
- No money movement, payment processing, or banking settlement.
- No automated black-box machine learning model training (rules-only engine in Stage 1).
- No KYC verification, credit underwriting, or identity verification.
- No chargeback dispute filing or merchant settlement workflows.
- No cross-tenant data sharing or federated fraud pooling.
- No enterprise SAML/OIDC SSO (deferred to Tier 2 enterprise customers per Decision D4).
- No mobile native SDKs (Java and TypeScript backend SDKs only per DR-API-11).
- No multi-currency real-time FX conversion on ingest (callers submit `amount_inr` per DR-DSL-05).

---

## 5. Detailed User Stories & Testable Acceptance Criteria

### US-001: Synchronous Decision Evaluation
**As a** Payment Platform Engineer,  
**I want to** submit a payment event payload to `POST /v1/decisions` and receive an immediate verdict (`APPROVE`, `DECLINE`, `REVIEW`) with risk score and explainability metadata,  
**So that** my checkout gateway can decide whether to authorize the payment in real time.

**Acceptance Criteria:**
- **Given** an authenticated caller with a valid API key,
- **When** a valid payment event JSON payload (with `event_id`, `event_time`, `amount_minor_units`, `account_id_hash`, `payment_method`) is submitted,
- **Then** the engine returns HTTP 200 within $< 100\text{ ms}$ at p99 containing `decision_id`, `outcome`, `score` (0.0 to 100.0), and `rules_fired` list.
- **Given** an event payload with wall-clock skew $|event\_time - received\_at| > 300\text{ seconds}$,
- **Then** the API rejects the request with HTTP 400 Bad Request.

---

### US-002: Concurrency-Safe Velocity Counting
**As a** Risk Manager,  
**I want to** enforce sliding-window velocity limits (e.g. max 5 transactions per card within 10 minutes),  
**So that** card-testing fraud bots firing simultaneous requests cannot bypass limits due to race conditions.

**Acceptance Criteria:**
- **Given** a velocity rule configured for max 5 events per 600-second window,
- **When** 20 concurrent HTTP requests for the same `account_id_hash` hit the Decision API simultaneously within a 20 ms window,
- **Then** exactly 5 transactions return `APPROVE` and exactly 15 transactions trigger the velocity rule returning `REVIEW` or `DECLINE`.
- **Then** zero over-allocation occurs across distributed evaluation threads.

---

### US-003: Idempotent Decision Retrieval
**As a** Payment Platform Engineer,  
**I want to** retry a payment evaluation with the same `event_id` after a network timeout,  
**So that** retried requests return the exact stored verdict without double-incrementing velocity counters.

**Acceptance Criteria:**
- **Given** a payment event `E1` that has already been evaluated and persisted in PostgreSQL,
- **When** an identical payload with `event_id: E1` is resubmitted within the 14-day idempotency window,
- **Then** the API returns HTTP 200 with the exact original verdict, score, and `ruleset_version_hash`.
- **Then** the Redis velocity counter for that account is NOT incremented a second time.

---

### US-004: Deterministic Historical Replay
**As a** Risk Manager,  
**I want to** run a historical transaction dataset through a candidate ruleset,  
**So that** I can observe what decisions would have been rendered without affecting live production state.

**Acceptance Criteria:**
- **Given** a historical dataset of 100,000 transaction events in Parquet/CSV format,
- **When** Replay Run `R1` is executed against Ruleset Version `V2`,
- **Then** the replay worker uses injected `event_time` for all feature computations (ADR-0003).
- **Then** zero rows are written to live production tables (`decisions`, `processed_events`, `outbox`).
- **Then** running Replay Run `R2` with identical inputs produces a 0.00% divergence in decision outcomes.

---

### US-005: Self-Service Dataset Import (Decision D8, ADR-0008)
**As a** Prospective Customer Risk Lead,  
**I want to** upload an anonymized historical transaction file (CSV or Parquet) and map its columns to Aegis fields,  
**So that** Aegis can run a backtest without requiring engineering integration into my production systems.

**Acceptance Criteria:**
- **Given** a prospective customer accessing the Dataset Importer,
- **When** a CSV or Parquet file (up to 5 GB / 5,000,000 rows) is uploaded via pre-signed S3 upload,
- **Then** the system parses the headers and presents an interactive column mapping interface (`event_id`, `event_time`, `amount`, `account_id_hash`, `mcc`, `fraud_label`).
- **Given** a dataset containing unmasked, Luhn-valid 13–19 digit PAN sequences in any field,
- **Then** the automated validation scanner immediately aborts the import, deletes the staged file, and generates a validation failure report citing the PAN violation.
- **Then** all staged data is stored with tenant RLS isolation and automatically purged after 30 days.

---

### US-006: Backtest Comparison Report Generation (Decision D8)
**As a** Head of Fraud,  
**I want to** review an executive and technical Backtest Report comparing my historical system against Aegis,  
**So that** I can quantitatively evaluate whether Aegis avoids false positives and catches incremental fraud.

**Acceptance Criteria:**
- **Given** an imported historical dataset with ground-truth fraud labels,
- **When** the replay execution finishes,
- **Then** the console renders a comparison dashboard and downloadable PDF/JSON report showing:
  1. Confusion matrix (True Positives, False Positives, True Negatives, False Negatives).
  2. False positive reduction count and currency volume.
  3. Incremental fraud caught that the baseline system approved.
  4. Decision distribution: % Approved, % Declined, % Reviewed.
  5. Microsecond execution latency percentiles (p50, p95, p99).

---

### US-007: Rule Authoring and Maker-Checker Dual Control
**As a** Rule Author or Risk Manager,  
**I want to** author CEL rules in a syntax-highlighted editor and submit them for peer approval,  
**So that** no single individual can unilaterally modify production risk thresholds without oversight.

**Acceptance Criteria:**
- **Given** a user with role `rule_author` or `risk_manager`,
- **When** they create or modify a rule in the editor,
- **Then** the editor validates CEL syntax, AST complexity ($\le 250$ nodes), and ensures regexes do not risk catastrophic backtracking.
- **Given** Rule Draft `D1` authored by User `A`,
- **When** User `A` attempts to approve or publish `D1`,
- **Then** the API rejects the action with HTTP 403 Forbidden ("Author cannot approve own rule change").
- **Then** only a distinct user with role `admin`, `owner`, or a second `risk_manager` can approve and activate the ruleset.

---

### US-008: Tamper-Evident Audit Batch Sealing
**As a** Compliance Officer,  
**I want** all decision records to be batched, Merkle-hashed, and chained into an append-only ledger,  
**So that** any subsequent tampering or database modification can be mathematically proven.

**Acceptance Criteria:**
- **Given** live decision records committed to PostgreSQL,
- **When** the background audit sealer runs every 30 seconds,
- **Then** it aggregates unsealed decisions into a batch, computes a SHA-256 Merkle root, chains it to the previous batch hash, and writes a row to `decision_audit_batches`.
- **Then** daily batch roots are exported to AWS S3 Object Lock in Compliance WORM mode.
- **Given** an auditor running the verification CLI against a decision ID,
- **Then** the tool recomputes the Merkle leaf, verifies the sibling proof against the sealed root, and returns `PASS` or `TAMPERED`.

---

### US-009: In-House Authentication and 2FA
**As a** Console Operator,  
**I want to** log in using email/password with mandatory TOTP authenticator verification,  
**So that** brute-force or credential-stuffing attacks cannot compromise the fraud management console.

**Acceptance Criteria:**
- **Given** a registered user,
- **When** they submit valid email and password credentials,
- **Then** the system returns a temporary 5-minute pre-auth token requiring TOTP verification.
- **Given** a valid 6-digit TOTP code from a standard authenticator app (Google Authenticator, Bitwarden),
- **Then** the system issues an HTTP-only JWT access token (15-min TTL) and refresh token (7-day TTL).
- **Then** 5 consecutive failed login attempts lock the account for 15 minutes.

---

### US-010: Zero Raw PAN Ingress Filter
**As a** Security Architect,  
**I want** all API ingress endpoints to reject any request containing an unmasked card number,  
**So that** Aegis remains strictly outside PCI DSS Level 1 audit scope.

**Acceptance Criteria:**
- **Given** an API request where an integer or UUID field contains card data,
- **Then** the request fails OpenAPI schema validation with HTTP 400.
- **Given** an API request where a string field (e.g. `merchant_name`, `custom_fields`) contains a 13–19 digit numeric sequence that passes the Luhn checksum,
- **Then** the Decision API immediately rejects the request with HTTP 400 Bad Request (`code: RAW_PAN_PROHIBITED`).
- **Then** the rejected payload is NOT written to database logs; only a sanitized audit event is recorded.

---

### US-011: Automated Fail-Open Circuit Breaker
**As a** Payment Platform Engineer,  
**I want** Aegis to fail-open with a degraded flag if internal downstream enrichment times out,  
**So that** an internal glitch never halts high-volume customer checkout traffic.

**Acceptance Criteria:**
- **Given** an enrichment service experiencing a network partition or Redis rebuild,
- **When** cumulative decision evaluation time reaches the 80 ms circuit-breaker timeout,
- **Then** the engine skips remaining evaluations and renders verdict `APPROVE` with `degraded: true` and `reason_code: ENRICHMENT_TIMEOUT_FAIL_OPEN`.
- **Then** the degraded decision is recorded with its degradation reason and visible on the SLA metrics dashboard.

---

### US-012: Decision Explainability Breakdown
**As a** Fraud Analyst,  
**I want** every decision to explain exactly why it was rendered,  
**So that** I can defend the decision to customers or banking partners.

**Acceptance Criteria:**
- **Given** a completed decision evaluation,
- **Then** the response payload and console case drawer display:
  1. Base score and final clamped score (0.0 to 100.0).
  2. Complete list of rules evaluated and rules triggered.
  3. Individual weight contributions for each triggered rule (e.g. `+28.0`, `+20.0`).
  4. Input feature snapshot (velocity counts, MCC, amount, device ID) at event time.

---

### US-013: Real-Time Console Live Feed
**As a** Fraud Operations Analyst,  
**I want to** monitor transactions as they are evaluated in real time via Server-Sent Events (SSE),  
**So that** I can spot sudden automated bot attacks or anomalous traffic spikes immediately.

**Acceptance Criteria:**
- **Given** an authenticated user on `/live-feed`,
- **When** transactions are evaluated by the Decision API,
- **Then** events stream to the browser via SSE within $< 200\text{ ms}$ of database commit.
- **Then** the feed supports live pause/resume, client-side filtering by outcome (`APPROVE`, `DECLINE`, `REVIEW`), and auto-reconnects with exponential backoff on network drop.

---

### US-014: Case Review Queue and Manual Disposition
**As a** Fraud Analyst,  
**I want to** review payments flagged as `REVIEW` in a dedicated case queue,  
**So that** I can manually approve or decline payments before clearing settlement holds.

**Acceptance Criteria:**
- **Given** a payment evaluated with verdict `REVIEW` ($40.0 \le \text{score} < 75.0$),
- **When** an analyst inspects the case drawer,
- **Then** they can assign the case, mark manual disposition (`OVERRIDE_APPROVE`, `CONFIRM_FRAUD`), add notes, and submit.
- **Then** the manual disposition emits an audit management event recorded in `audit_management_events`.

---

## 6. Success Metrics Measurable at Tier 0 and Tier 1

Consistent with Decision D9 (Claims Discipline), all product metrics are defined by measurable test harnesses and staging criteria:

| Metric | Target | Measurement Method | Environment Tier |
|---|---|---|---|
| **Concurrency Limit Correctness** | 100% exact limit pass rate (exactly L of N pass; 0 over-allocation) | Automated CI concurrency test (`ConcurrencyLimitIntegrationTest`) | **Tier 0** (Developer Laptop / GitHub Actions) |
| **Replay Determinism** | 0.00% verdict divergence on identical datasets | Automated CI determinism suite (`ReplayDeterminismTest`) | **Tier 0** (CI) |
| **Tier 0 Local Memory Footprint** | $\le 3.89\text{ GB}$ total RAM across all core containers | `docker stats` during full pipeline execution | **Tier 0** (Local Laptop) |
| **Synchronous Server Latency** | p50 $\le 25\text{ ms}$, p99 $\le 100\text{ ms}$ | k6 open-model arrival-rate load test (2,000 req/s sustained) | **Tier 1** (AWS Pilot Staging) |
| **System Ingress Throughput** | $\ge 2,000\text{ TPS}$ sustained, $\ge 5,000\text{ TPS}$ peak burst | k6 arrival-rate scenario committed in `/tests/load` | **Tier 1** (AWS Pilot Staging) |
| **Replay Processing Speed** | $\ge 20,000\text{ events/second}$ per worker thread | Replay benchmark harness against 1,000,000 synthetic records | **Tier 1** (AWS Pilot Staging) |
| **Automated Availability SLO** | 99.9% uptime (error budget $\le 51.84\text{M}$ failed reqs / 30 days) | Prometheus synthetic health probes + CloudWatch metrics | **Tier 1** (AWS Pilot Staging) |

---

## 7. Assumptions Mapping & Release Cadence

### 7.1 Cross-Reference to Assumptions Register
Every requirement in this PRD maps to an explicit entry in the [Assumptions Register](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md):
- Target ICP volume & risk team size $\to$ `ASM-INT-01`
- Latency target ($< 100\text{ ms}$ p99) $\to$ `ASM-INT-02`
- Launch throughput (2,000 TPS) $\to$ `ASM-INT-03`
- Fail-open circuit breaker policy $\to$ `ASM-INT-05`
- Shared RLS multi-tenancy $\to$ `ASM-INT-06`
- Hot 90-day retention $\to$ `ASM-INT-07`
- Client-side hashing (`account_id_hash`, `vpa_hash`) $\to$ `ASM-INT-08`, `ASM-INT-09`
- Synthetic tie-breaker ordering $\to$ `ASM-INT-10`
- Free backtest offer conversion wedge $\to$ `ASM-GTM-01`

### 7.2 PRD Revision Trigger
> **PRD Release Policy:** This document represents **v1.0 (Assumption-Based)**. 
> 
> **Version 1.1** must be authored and released upon the completion of the first **10 logged customer discovery interviews** (satisfying **Gate G1** per `docs/02-discovery-plan.md §8`). Any customer interview data that contradicts user stories, acceptance criteria, or non-goals will be reconciled in v1.1.
