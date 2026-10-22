# Production-Readiness Discovery

**Document:** 01  
**Status:** Approved (docs v1.0, assumption-based)
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026

---

## 1. Purpose

This document defines what "production-ready" means for Aegis before any
real transaction traffic is processed. It is not a feature list. It is a
set of hypotheses, constraints, and open questions that must be resolved,
measured, or delegated to qualified professionals before the system handles
real money.

Every engineering, security, compliance, and operational decision in later
documents must be traceable to an answer or an acknowledged trade-off
recorded here.

**Scope of this document:** Stage 1 (the decision API, rules engine, audit
trail, and replay tool in shadow mode). Decisions that apply only to
Stage 2 and beyond are noted but not resolved here.

---

## 2. Assumptions

The following are assumed true for Stage 1. If any assumption is false,
the affected sections must be revisited before work proceeds.

| # | Assumption | If false, what breaks |
|---|------------|-----------------------|
| A1 | The system is hosted in one AWS region in India (ap-south-1 or ap-south-2) at launch | Data-residency compliance, DR topology, latency targets |
| A2 | Stage 1 runs in shadow mode only — no live approve/decline decisions are sent to a payment processor | Liability exposure, PCI scope, incident severity |
| A3 | Tenants provide tokenised transaction data; raw card PANs never reach Aegis | PCI scope classification, storage design |
| A4 | The initial customer set is Indian mid-size fintechs regulated by RBI or SEBI | Applicable regulations, audit requirements |
| A5 | Multi-tenancy isolation is row-level (shared database, tenant-keyed tables with row-level security), not schema- or instance-level | Data model, security review scope |
| A6 | The replay engine will access only data the tenant already owns — no cross-tenant data sharing in Stage 1 | Privacy controls, DPA terms |
| A7 | Java 21 virtual threads are used for I/O-bound work; blocking drivers (JDBC, Jedis) are replaced or wrapped as needed | Throughput model, thread-pool sizing |
| A8 | Kafka is managed (MSK or Confluent Cloud); the team does not operate Kafka brokers directly in Stage 1 | Ops burden, SLA accountability |

---

## 3. SLO Hypotheses

All numbers below are **hypotheses to validate by load test**.
They become committed SLOs only after passing the tests specified in the
"Verified by" column. Do not publish them to customers until that gate is passed.

### 3.1 Decision API (synchronous path)

| SLO | Target | Verified by |
|-----|--------|-------------|
| p50 server-side latency | ≤ 25 ms | k6 or Gatling load test at sustained 2,000 req/s with realistic payload mix (see §3.4) |
| p99 server-side latency | ≤ 100 ms | Same test |
| Availability (calendar month) | 99.95% (~22 min downtime/month) | SLO dashboard measuring 5xx rate; game-day drill confirming alert fires |
| Error rate (5xx) | < 0.05% of requests | Same load test |

**Latency budget breakdown (hypothesis — must be validated):**

| Step | Budget |
|------|--------|
| TLS termination + auth token validation | 5 ms |
| Redis: velocity counter read + feature lookup | 10 ms |
| Rule evaluation (compiled ruleset, in-process) | 10 ms |
| Decision write (Postgres, synchronous, committed before response — see ADR-0002) | 15 ms |
| Total server-side p99 | 40 ms headroom to 100 ms target |

Decision [DR-OPS-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) / Assumption [ASM-INT-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md): 100 ms p99 server-side latency is the baseline hypothesis, validated in discovery interviews under Gate G1.

### 3.2 Throughput

| Target | Hypothesis |
|--------|-----------|
| 2,000 decisions/s per region at launch | hypothesis, to validate by load test |
| Scale path to 10,000 decisions/s via horizontal pod scaling | hypothesis, to validate by scaling test |

Decision [DR-API-10](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) / Assumption [ASM-INT-03](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md): 2,000 decisions/s sustained baseline with burst headroom up to 5,000 decisions/s, validated in discovery interviews under Gate G1.

### 3.3 Kafka consumer lag

| SLO | Target | Verified by |
|-----|--------|-------------|
| Consumer lag (async ingest path), p99 | < 5 s in steady state | Consumer lag dashboard; alert at 10 s |
| Dead-letter queue size | 0 in steady state; alert at >0 | DLQ depth alert |

### 3.4 Load test traffic mix (hypothesis)

The load test must be representative. Until design-partner data is
available, use this synthetic mix:

| Event type | Share |
|------------|-------|
| Single card, low velocity (approve path) | 65% |
| Card at velocity limit (boundary case) | 10% |
| Blocklisted entity | 5% |
| Two simultaneous transactions for same account (concurrency test) | 5% |
| Duplicate event (idempotency test) | 5% |
| Enrichment timeout (degraded path) | 5% |
| Malformed payload | 5% |

Assumption [ASM-INT-04](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md): Synthetic traffic mix is adopted as baseline; to validate with design partners under Gate G1.

### 3.5 Replay engine

| Target | Hypothesis |
|--------|-----------|
| Throughput per worker | ≥ 20,000 events/s | hypothesis, to validate by benchmark |
| Workers scale horizontally | Linear to at least 4 workers | hypothesis |
| Replay is bit-for-bit deterministic | Same dataset + same ruleset = identical decisions on repeated runs | Verified by running the same replay twice and diffing results |

---

## 4. Failure Policy (Fail-Open vs Fail-Closed)

The decision API has a per-request time budget. If that budget is
exhausted (for example, Redis is slow, enrichment times out), the system
must still give an answer. The answer when the system cannot make a
confident decision is a **policy choice with business consequences**.

| Policy | Behaviour | Risk |
|--------|-----------|------|
| Fail-open | Return `approve`, mark decision as `degraded` | Fraud passes through during an outage |
| Fail-closed | Return `decline`, mark decision as `degraded` | Legitimate transactions are blocked during an outage |

**Design decision:** the fail policy is **configurable per tenant** and
**must be set explicitly** before a tenant goes live. There is no system default.

**Audit requirement:** every degraded decision is stored with the reason
for degradation, the components that timed out, and the configured policy
applied. These are reported in the tenant's SLA metrics.

Decision [DR-OPS-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) / Assumption [ASM-INT-05](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md): Fail policy is tenant-configurable (default fail-open with degraded flag), confirmed in customer SOW.

**Time budget (hypothesis):** 80 ms total. Each enrichment step has an
individual circuit-breaker timeout. If the cumulative budget is breached,
remaining steps are skipped and the policy is applied.

---

## 5. Data and PII Handling

### 5.1 What Aegis stores

| Data type | What is stored | What is not stored |
|-----------|---------------|--------------------|
| Payment card numbers (PAN) | Never. Not accepted on any API endpoint. | Raw PAN |
| Card tokens (network tokens, processor tokens) | Accepted and stored as the card identifier | — |
| Account identifiers | Tenant-keyed hash of the account identifier, or an opaque token provided by the tenant | Raw account number |
| Transaction amounts | Stored as received | — |
| Device fingerprint, IP, user agent | Stored as received (no PAN-equivalent sensitivity) | — |
| Fraud labels | Stored per decision, provided by tenant | — |
| Audit log | See §9. Append-only, Merkle-batch-chained for decisions, synchronously chained for management events. | — |

**PAN prevention — two-layer approach:**

1. **Strict schema validation (primary control).** The API schema defines every
   allowed field, its type, and its maximum length. Numeric identifiers are
   typed as integers or UUIDs, never as free-text strings. A field declared
   as `integer` or `uuid` cannot contain a PAN. Fields not in the allowed
   schema are rejected outright. This is the cheapest and most reliable control:
   a well-formed request cannot carry a PAN in a typed field.

2. **PAN-pattern scanning on free-text and unknown fields (secondary control).**
   Any field whose schema type is `string` and whose semantic role is not a
   known non-PAN identifier (e.g., `merchant_name`, `description`, `device_model`)
   is scanned for a Luhn-valid 13–19 digit sequence before the payload is
   processed. If a match is found, the request is rejected with HTTP 400 and
   the matched value is not logged (only a redacted indicator is logged).

   **Known false-positive problem:** Luhn validation is a checksum, not a
   strong identifier. Some non-PAN numeric strings (e.g., certain Indian
   IFSC codes combined with account fragments, or 16-digit loyalty card
   numbers without PAN semantics) can pass a Luhn check. A false positive
   rejects a legitimate transaction. The set of free-text fields accepted
   by the API should be minimised to reduce false-positive exposure.
   Decision [DR-API-09](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): Bounded extension fields (max 20 keys, string values <= 256 chars) are PAN-scanned; free-text minimized to prevent false-positive card rejections.

### 5.2 Data classification

Assumption [ASM-LEG-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md): Baseline data classification policy adopted; formal DPDP review under Gate G1.
The following is an initial working classification:

| Class | Examples | Controls |
|-------|----------|---------|
| Confidential — Personal | Name, email, mobile, device ID (if linkable to a person) | Encrypted at rest (AES-256), encrypted in transit (TLS 1.3), access-logged, retention limit applies |
| Confidential — Financial | Transaction amounts, merchant, timestamps, outcome | Encrypted at rest, access-logged, retention limit applies |
| Internal | Rule definitions, rulesets, thresholds | Access-controlled by role |
| Public | API documentation, status page | No restriction |

### 5.3 Retention and deletion

Decision [DR-DM-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) / Assumption [ASM-INT-07](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md): 90 days hot PostgreSQL retention; cold archive in S3 Glacier.
Standard policy: transaction decisions retained for 7 years (RBI record-keeping
guidance for financial records — to verify with counsel under Gate G1), PII fields
deleted or anonymised after 3 years unless a legal hold applies.

Deletion is **tenant-scoped**: when a tenant is offboarded, all their
data (transactions, rules, audit log, replay datasets) is deleted within
30 days and confirmed with a signed certificate of deletion. This must be
in the DPA.

### 5.4 Encryption

- At rest: AES-256 via cloud-provider managed encryption; encryption keys
  are tenant-isolated (separate KMS key per tenant for Confidential — Personal data).
  Decision [DR-SEC-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): Shared AWS KMS master key with tenant UUID encryption contexts for Tier 0/1; dedicated customer CMKs for Tier 2 enterprise.
- In transit: TLS 1.3 minimum on all external connections; mTLS for
  service-to-service calls in production.
- Secrets (API keys, DB passwords, Kafka credentials): stored in AWS Secrets
  Manager; never in environment variables, never in code, never in logs.

---

## 6. Security and Compliance Questions

> The statements in this section are **preliminary engineering assessments**.
> Each item marked "needs counsel verification" must be reviewed by a
> qualified lawyer and/or compliance consultant before being treated as settled.

### 6.1 India data localisation

The Reserve Bank of India (RBI) has issued guidance requiring that payment
system data be stored only in India. **needs counsel verification:**
- Does the current RBI circular apply to Aegis in shadow-mode-only operation?
- Does it apply to the replay dataset (historical transaction data)?
- Does "only in India" prohibit replication to a non-Indian DR region,
  or only primary storage?

**Engineering assumption pending counsel:** all data at rest is in the
India AWS region (ap-south-1 or ap-south-2). No data is replicated outside
India.

**AZ failure vs region failure — distinct scenarios:**
- *Single-AZ failure* (most common): the Postgres read replica in a second AZ
  promotes to primary automatically. No data leaves the region. RTO < 5 min
  for AZ failure (hypothesis).
- *Full region failure* (rare): if ap-south-1 fails entirely, DR can only
  recover from the WAL archives in S3 (which are also in ap-south-1 unless
  cross-region replication is enabled). Enabling cross-region S3 replication
  to ap-south-2 restores RPO/RTO for a region failure, but requires
  counsel confirmation that cross-region replication within India is
  permissible under the localisation circular.

Assumption [ASM-LEG-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md): Storage exclusively in AWS Mumbai (ap-south-1) for Tier 0 and Tier 1 pilot aligns with RBI localisation circulars without requiring cross-border or complex inter-region replication. Legal counsel review scheduled under Gate G1.

Decision [DR-IAC-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): Single region (ap-south-1 Mumbai) for Tier 0 and Tier 1. Secondary region (ap-south-2 Hyderabad) evaluated only for Tier 2 enterprise customers.

### 6.2 DPDP Act 2023

The Digital Personal Data Protection Act 2023 governs personal data
of Indian residents. **needs counsel verification:**

| Question | Engineering implication if "yes" |
|----------|----------------------------------|
| Are transaction records "personal data" under DPDP? | Consent, notice, retention limits apply |
| Is Aegis a "data fiduciary" or a "data processor" for its tenants? | Determines which DPDP obligations attach to Aegis vs the tenant |
| Do deletion/correction rights apply to fraud-decision records? | Deletion workflow must be built |
| What is the required notice to data principals? | DPA terms and tenant's privacy policy must reference Aegis |

### 6.3 PCI DSS scope

**Design choice:** Aegis does not accept raw PANs (see §5.1). This choice
is made explicitly to minimise PCI scope. **needs counsel verification:**
- Does accepting card tokens (network tokens, processor tokens) bring Aegis
  in-scope for any PCI DSS controls?
- Which SAQ level applies if any?
- How does the scope assessment change when a tenant starts sending
  card-present data (EMV chip data)?

### 6.4 SOC 2 / ISO 27001 roadmap

Assumption [ASM-SEC-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md): External CREST/CERT-In penetration test report with zero critical/high findings required prior to Gate G2 (pre-paid contract); SOC 2 Type I roadmap at 9–12 months post-pilot.

Preliminary estimate: SOC 2 Type I in ~9 months; SOC 2 Type II in ~18 months.
ISO 27001 if a large bank or enterprise customer requires it (typically 12–18 months).

Controls to implement before any audit:
- Inventory of all systems and data stores
- Access review process (quarterly at minimum)
- Incident response policy and drill
- Change management process
- Vendor risk assessment for AWS, MSK/Confluent, any sub-processors
- Penetration test (external, by a qualified firm) — results reviewed and
  critical/high findings remediated before the first live tenant

### 6.5 Other regulations

Assumption [ASM-LEG-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md): Additional financial-sector regulatory requirements (SEBI, IRDA) to validate during customer discovery interviews under Gate G1.

---

## 7. Reliability and Disaster Recovery

### 7.1 Targets (hypotheses)

| Metric | Target | Notes |
|--------|--------|-------|
| RPO (Recovery Point Objective) | 5 minutes | hypothesis — based on Postgres WAL streaming + hourly base backups |
| RTO (Recovery Time Objective) | 30 minutes | hypothesis — based on automated failover + health check pass |

Decision [DR-OPS-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) / [DR-IAC-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): Baseline RPO 5 minutes, RTO 30 minutes for Tier 1 pilot; confirmed during pilot SOW negotiation.

### 7.2 Postgres

- Primary in one AZ; read replica in a second AZ in the same region.
- Continuous WAL archiving to S3 with 35-day retention.
- Monthly restore drill: restore to a isolated environment, run integrity
  checks, confirm decision count matches source, document result.
- Point-in-time recovery tested to the 5-minute RPO target before going live.

### 7.3 Redis

Redis is explicitly **derived state**: velocity counters and feature caches
can be rebuilt from Postgres decision history by the reconciliation job.
Redis does use AOF persistence and a replica for fast recovery, but the
failure mode is "rebuild from source of truth", not "failover and continue".

**Known limitation:** Redis replication is asynchronous. A primary failure
before replication can result in losing the last few write operations. This
means a velocity counter could temporarily be off by a small number of
increments. This error is bounded, audited by the reconciliation job, and
documented in the failure-policy runbook. The alternative (synchronous
replication / wait-for-replica ACK) adds latency; the trade-off is recorded
in ADR-0002.

### 7.4 Kafka

Kafka (managed service) is configured with:
- Replication factor 3 for all topics
- `acks=all` on producers
- Consumer offset commits only after the decision is written to Postgres
- A dead-letter topic for messages that fail after N retries; DLQ depth
  alerting to PagerDuty within 1 minute

### 7.5 Multi-region and geographic DR

The current design provides:
- **Multi-AZ within one region** (ap-south-1): Postgres primary + replica
  in separate AZs; Redis replica in a second AZ; Kafka with 3-broker
  cross-AZ placement. This protects against single-AZ failure.
- **Region-level DR**: WAL archives to S3. If S3 cross-region replication
  to ap-south-2 is permitted (see §6.1), RPO for a full region failure
  approaches 5 minutes. Without cross-region S3 replication, a region failure
  requires a full restore from the last hourly S3 base backup in the same
  region, which may exceed 30 minutes.

Decision [DR-IAC-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): Region-level failover SLA is deferred to Tier 2 enterprise contracts; Tier 1 pilot operates multi-AZ within ap-south-1.

---

## 8. Observability

Every deployed component must emit the following before any tenant traffic
is accepted.

### 8.1 Metrics (Prometheus + Grafana or equivalent)

| Metric | Labels |
|--------|--------|
| `decision_api_latency_seconds` histogram | tenant_id, outcome, ruleset_version |
| `decision_api_errors_total` counter | tenant_id, error_type |
| `kafka_consumer_lag_messages` gauge | topic, consumer_group |
| `redis_velocity_increment_duration_seconds` histogram | tenant_id |
| `rule_evaluation_duration_seconds` histogram | tenant_id, ruleset_version |
| `replay_events_processed_total` counter | run_id, tenant_id |
| `degraded_decisions_total` counter | tenant_id, reason |
| `reconciliation_drift_events_total` counter | tenant_id |

### 8.2 Logs

- Structured JSON logs, one line per event.
- Every log line for a decision includes `decision_id`, `tenant_id`,
  `event_id`, `trace_id`. No PII in logs.
- Log level `INFO` in production; `DEBUG` disabled by default, togglable
  per tenant via feature flag for a limited duration.
- Logs shipped to a log aggregator (CloudWatch Logs or equivalent) with
  90-day hot retention, 7-year cold archive (same retention as decisions).

### 8.3 Traces

- OpenTelemetry instrumentation on all services.
- Trace context propagated through Kafka headers and HTTP headers.
- Traces sampled at 1% in production, 100% for degraded decisions and
  errors. Stored in a compatible backend (Jaeger, Tempo, or AWS X-Ray).

### 8.4 Alerts

Each alert must link to a runbook. No alert exists without a runbook.

| Alert | Threshold | Runbook |
|-------|-----------|---------|
| Decision API p99 latency > 200 ms for 2 min | Warn | runbooks/decision-api-latency.md |
| Decision API error rate > 1% for 1 min | Page | runbooks/decision-api-errors.md |
| Kafka consumer lag > 10 s | Page | runbooks/consumer-lag.md |
| DLQ depth > 0 | Page | runbooks/dlq.md |
| Redis reconciliation drift > 0 | Warn within 5 min | runbooks/redis-drift.md |
| Postgres replication lag > 30 s | Page | runbooks/postgres-replication.md |

Decision [DR-OPS-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): Founder on-call during Stage 0/1 with 15-min business hours escalation window; secondary engineer hired for Tier 2.

### 8.5 Status page

A public status page (Statuspage.io or equivalent) showing:
- Decision API availability
- Kafka ingest availability
- Console availability
- Active incidents and post-mortems

Must be live before any customer is onboarded.

---

## 9. Audit Trail

The audit trail is a first-class security and compliance requirement,
not a logging afterthought.

### 9.1 What is audited

| Event | Stored fields |
|-------|---------------|
| Decision | input snapshot, features computed, rules fired with weights, ruleset version hash, final score, outcome, latency, degraded flag, timestamp, decision_id |
| Rule change | who authored, who approved, diff, ruleset version hash, timestamp |
| Rule publish | who approved, rollout stage, timestamp |
| Login / logout | user_id, IP, device, timestamp, success/failure |
| Permission change | actor, target_user, old role, new role, timestamp |
| API key create / revoke | actor, key ID (not value), timestamp |
| Data export | actor, scope, timestamp, record count |
| Replay run | initiator, ruleset A hash, ruleset B hash, dataset identifier, row count, run_id, start/end time |

### 9.2 Tamper-evidence

Two chaining modes are used, depending on event frequency. Full design is in ADR-0004.

**Management events** (rule changes, logins, permission changes, API key
operations, data exports) are **synchronously chained**: each event includes
SHA-256(`previous_hash` ∥ canonical-JSON(`event`)), committed in the same
database transaction as the event. Tamper-evidence window: zero.

**Decision records** are chained by a **background batch sealer**: a
scheduled job runs every 30 seconds (configurable), batches unsealed decision
rows, computes a Merkle root over the batch, and chains that root to the
previous batch's root. The sealed batch metadata and daily chain anchors are
published to S3 with Object Lock. Tamper-evidence window: ≤ 30 seconds.

This design keeps the decision write path (ADR-0002) free of per-record
hash computation, avoiding a serial hot-row bottleneck at high throughput.
Any tampering with a decision row breaks Merkle root verification and the
batch chain, and is detectable by the verification CLI tool.

Decision [DR-SEC-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) / Assumption [ASM-INT-12](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md): 30-second Merkle batch sealing with daily S3 Object Lock anchors is the accepted baseline; per-record synchronous sealing is deferred to protect write-path throughput.

### 9.3 Export

Auditors can export the full audit log for a tenant for any date range via
the console (Auditor role required). Export format: newline-delimited JSON.
Export events themselves appear in the audit log.

---

## 10. Operations and On-Call

### 10.1 Environments

| Environment | Purpose | Data |
|-------------|---------|------|
| sandbox | Customer integration testing | Synthetic only; isolated per tenant API key |
| staging | Internal pre-production | Synthetic; mirrors production config |
| production | Live traffic | Real data; change control required |

Staging and production configurations must be managed by infrastructure-as-code
and reviewed in pull requests. No manual production changes without an
incident or change ticket.

### 10.2 On-call

Decision [DR-OPS-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): Founder on-call during Stage 0/1; documented on-call schedule and escalation protocol in [doc 12 §8](file:///c:/dev/aegis/docs/12-slos-runbooks-dr.md#8-tier-specific-operations-and-dr-runbooks-decision-d1).

Minimum on-call requirements:
- A pager reachable within 5 minutes
- A runbook for every alert (see §8.4)
- A defined incident severity matrix (P1: data loss or total outage;
  P2: degraded decisions; P3: console issue not affecting decisions)
- A public post-mortem within 5 business days for every P1 incident

### 10.3 Change management

- Database migrations: reviewed by a second person, tested in staging first,
  backed up immediately before applying in production.
- Rule-engine changes: covered by the maker-checker workflow (see §11).
- Dependency updates: automated PRs (Dependabot or Renovate), merged only
  after CI passes; security advisories triaged within 48 hours.

---

## 11. Roles and Access Control

### 11.1 Human roles

| Role | Can | Cannot |
|------|-----|--------|
| Owner | Everything, including billing, user management, tenant deletion | Nothing restricted |
| Admin | Manage users, API keys, settings, environments | Approve rule changes; cannot approve their own invite |
| Risk Manager | Approve and publish rules, set thresholds and fail policy, run replays | Approve a change they authored (maker-checker enforced) |
| Rule Author | Draft rules, run simulations and replays, view decisions | Publish rules |
| Analyst | Review cases, label transactions, view decisions and audit log | Change rules or settings |
| Auditor | Read-only access including full audit log and exports | Change anything |
| Developer | Manage API keys and webhooks, access sandbox | View production PII fields by default |

### 11.2 Maker-checker for rule publishing

No rule change may be published by the same person who authored it.
This is enforced in the application layer (not just by policy) for every
transition from `draft` → `approved`.

The rule lifecycle is:
```
draft → simulated → submitted for approval → approved → shadow → canary → live
```

Each transition requires:
- `draft → simulated`: Rule Author triggers simulation against synthetic
  data; results stored with ruleset hash.
- `submitted for approval → approved`: Risk Manager approves. If the
  Risk Manager is also the author, the system rejects the approval and
  requires a different Risk Manager or the Owner.
- `approved → shadow`: Risk Manager or Owner starts shadow mode.
- `shadow → canary`: Risk Manager sets a canary percentage (1%, 5%, 10%).
- `canary → live`: Risk Manager or Owner promotes after reviewing metrics.
- Any stage → `reverted`: Risk Manager or Owner can revert to the previous
  live ruleset with one action; revert is also audited.

### 11.3 System / API authentication

| Client type | Method |
|-------------|--------|
| Decision API callers | OAuth2 client credentials or API key with HMAC-SHA256 request signing + 5-minute timestamp window |
| Webhook receivers | HMAC-SHA256 signature on payload |
| Inter-service | mTLS in production |
| Replay engine (batch) | Service account with scoped IAM role |

---

## 12. Launch Gate Checklist

The following must all be true before any real transaction is processed.
The person who checks each item signs their name and date against it.

### Reliability
- [ ] SLOs defined and visible on a dashboard
- [ ] Load test at 2× expected peak TPS completed; p99 ≤ 100 ms confirmed
- [ ] Chaos test: Redis failover tested; fail-open and fail-closed both behaved correctly
- [ ] Chaos test: Kafka broker failure tested; consumer lag recovered under 5 s
- [ ] Postgres failover tested; RTO < 30 min confirmed
- [ ] Point-in-time restore tested to 5-minute RPO
- [ ] Backup restore drill documented

### Correctness
- [ ] Concurrency test: N parallel requests at limit L → exactly L pass (CI gate)
- [ ] Velocity idempotency test: same event_id submitted twice to Lua script → counter incremented only once (CI gate)
- [ ] Duplicate delivery test: same event_id delivered 3× via Kafka → exactly 1 decision row; velocity counter unchanged after first delivery
- [ ] Replay determinism test: same dataset × same ruleset → identical results on two separate runs
- [ ] Replay isolation confirmed: replay run does not write to live decision tables
- [ ] Velocity implementation equivalence test: `VelocityImplementationEquivalenceTest` passes — Lua script and in-memory replay implementation return identical verdicts and counts on a generated event sequence including duplicates and boundary cases (CI gate, see ADR-0005)
- [ ] Audit sealer test: batch sealer runs against 10k synthetic decisions; Merkle proof verification passes for a randomly selected decision row

### Security
- [ ] Threat model reviewed (internal review at minimum)
- [ ] External penetration test ordered; critical/high findings resolved
- [ ] Secrets: no secrets in code, env vars, or logs; Secrets Manager in use
- [ ] Dependency scan clean (no critical CVEs unmitigated)
- [ ] Container image scan clean
- [ ] PAN prevention test: (a) typed-field schema rejects a PAN in an integer field; (b) free-text field containing a Luhn-valid sequence is rejected with HTTP 400; (c) a known false-positive string in a free-text field is assessed and its disposition is documented
- [ ] Access review: only intended people have production access

### Data and compliance
- [ ] Retention policy implemented and tested (deletion job runs)
- [ ] DPA template reviewed by counsel
- [ ] Privacy notice reviewed by counsel
- [ ] DPDP Act obligations mapped by counsel
- [ ] RBI data-localisation design alignment confirmed by counsel
- [ ] PCI scope determination received from counsel

### Observability
- [ ] All metrics listed in §8.1 are populated in the dashboard
- [ ] All alerts in §8.4 fire in staging when thresholds are breached
- [ ] Every alert links to a runbook; runbooks tested in game day
- [ ] Status page live and updated correctly during a simulated incident

### Operations
- [ ] On-call rota documented and people paged
- [ ] Incident severity matrix agreed
- [ ] Game day conducted: P1 simulated end-to-end
- [ ] Infrastructure reproducible: clean-room deploy from IaC succeeds

### Product
- [ ] Onboarding works end-to-end for each role in the staging environment
- [ ] At least one external engineer has integrated with the sandbox without help
- [ ] API documentation published and accurate
- [ ] Pricing and SOW templates signed off
- [ ] Pilot customer has signed SOW and DPA

---

## 13. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Redis async replication loses a counter increment during failover | Low | Medium — velocity counter temporarily off; reconciliation job corrects within 5 min | Documented error bound; reconciliation job; alert on drift; event_id membership in sorted set limits duplicate-inflate risk (see ADR-0005) |
| PAN reaches Aegis in a free-text field; Luhn scan produces a false positive on a non-PAN string | Medium | Medium — legitimate transaction rejected; customer complaint | Minimise free-text fields in schema; document false-positive risk per field; assess with pilot customer data (see §5.1) |
| Pilot customer's data volume exceeds 2,000 TPS | Medium | High — SLO breach at launch | Confirm peak TPS in SOW before signing; re-run load test if it exceeds 2,000 TPS |
| Counsel review delays before first customer | High | Medium — cannot make legal commitments | Engage counsel in month 1, not month 3 |
| DPDP Act obligations unclear for B2B fraud data | High | High — regulatory breach | Get written legal opinion before onboarding any Indian customer |
| Founder is the single point of failure for on-call | High | High — personal burnout; SLA breach | Hire first SRE or define escalation before first live tenant |
| Replay tool produces different results on re-run | Low | High — invalidates the core differentiator | Determinism tests in CI; event-time ordering enforced (see ADR-0003) |
| A maker-checker bypass in the rule lifecycle | Low | High — untested rule reaches production | Enforce in application code, not just UI; write a test for it |
| Tenant data isolation failure (row-level security miss) | Very Low | Critical — regulatory and reputational | Row-level security policy tested per release; access tests per tenant pair |

---

## 14. Decision Register References (formerly Open Questions)

Per Decision **D3**, every previously open question has been resolved as **Decided** (assumed baseline value with rationale) or **Deferred** (trigger and owner) in the [Decision Register](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) and [Assumptions Register](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md):

| # | Topic | Decision / Assumption Ref | Status | Resolution / Baseline Value |
|---|---|---|---|---|
| OQ-01 | Target p99 Latency | [DR-OPS-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md), [ASM-INT-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | Decided | 100 ms server-side p99 baseline hypothesis; validated under Gate G1. |
| OQ-02 | Fail Policy | [DR-OPS-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md), [ASM-INT-05](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | Decided | Tenant-configurable fail-open with degraded flag (default for payment gateways). |
| OQ-03 | RPO / RTO Targets | [DR-OPS-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md), [DR-IAC-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | Decided | RPO 5 min, RTO 30 min baseline for Tier 1 pilot. |
| OQ-04 | Second Indian AWS Region | [DR-IAC-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md), [ASM-LEG-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | Decided | Single region (`ap-south-1` Mumbai) for Tier 0/1; `ap-south-2` evaluated for Tier 2 enterprise. |
| OQ-05 | DPDP Act Role & Scope | [ASM-LEG-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | Assumed | Data Processor for transactions; Data Fiduciary for console credentials; Gate G1 review. |
| OQ-06 | RBI Data Localisation | [ASM-LEG-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | Assumed | All data retained exclusively in AWS Mumbai (`ap-south-1`); Gate G1 review. |
| OQ-07 | PCI DSS Scope Minimisation | [ASM-LEG-03](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | Assumed | Zero raw PAN invariant + card network tokens only; SAQ scoped down; Gate G1 review. |
| OQ-08 | Audit Certifications (SOC 2) | [ASM-SEC-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | Assumed | CREST/CERT-In pen test pre-G2; SOC 2 Type I in 9–12 months post-launch. |
| OQ-09 | KMS Key Isolation Pricing | [DR-SEC-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | Decided | Single shared master KMS key with tenant UUID context for Tier 0/1; dedicated CMKs for Tier 2. |
| OQ-10 | Retention Period | [DR-DM-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md), [ASM-INT-07](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | Decided | 90 days hot PostgreSQL retention; older partitions exported to S3 Glacier. |
| OQ-11 | Document Owner & On-Call | [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md), [DR-OPS-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | Decided | Founder / Lead Architect owns docs and on-call during Stage 0/1. |
| OQ-12 | Peak Launch TPS | [DR-API-10](file:///c:/dev/aegis/docs/00-decisions/decision-register.md), [ASM-INT-03](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | Decided | Tier 1: 500 req/s sustained (1,000 req/s burst); Tier 2: 2,500 req/s sustained (5,000 req/s burst). |
| OQ-13 | Additional Regulators (SEBI/IRDA)| [ASM-LEG-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | Assumed | Evaluated per customer during Gate G1 discovery interviews. |
| OQ-14 | Trademark Verification | [DR-API-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | Deferred | Trademark search and entity registration scheduled prior to commercial contracting (Gate G2). |
| OQ-15 | Representative Traffic Mix | [ASM-INT-04](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) | Assumed | Synthetic mix adopted as baseline; validated with design partners under Gate G1. |

