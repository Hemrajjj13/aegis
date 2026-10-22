# Technical Proofs and Pilot Case Study Architecture

**Document:** 16  
**Status:** Active Content & Proof Architecture (Pre-Validation Track)  
**Owner:** Founder / Lead Architect ([DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

---

## 1. Purpose and Claims Discipline Standard (Decision D9)

This document establishes the technical proof architecture and commercial case study framework for Project Aegis. Because Project Aegis is built by a solo founder with AI coding agents and bootstrapped without marketing budgets, our credibility relies entirely on **verifiable engineering proofs and empirical measurement**.

### 1.1 The Mandatory Claims Rule (Decision D9)
> **MANDATORY POLICY:** Any performance figure, benchmark result, or accuracy metric published in Aegis documentation, blog posts, whitepapers, or marketing must be **actually measured and logged**. 
> 
> Every published number must explicitly state:
> 1. The exact hardware specifications (CPU, RAM, storage, network).
> 2. The operating system and runtime environment.
> 3. The test harness methodology, workload profile, and reproducibility command.
> 
> Unmeasured results must be explicitly labeled as *"Benchmark Harness Specification (Hypothesis, to Measure)"*.

---

## 2. Three Deep Engineering Content Pieces

These three deep-dive technical articles provide open, reproducible proof of Aegis's core architectural differentiators.

---

### 2.1 Engineering Proof 1: "Proving Exactly-L Under Velocity Limits: Concurrency Control Without Race Conditions"

#### The Problem: The Concurrency Race in Fast Payments
In high-throughput payment rails like UPI and credit card checkout, fraud rings exploit distributed race conditions. A card-testing bot fires 20 simultaneous payments across separate HTTP connections in the same millisecond. 

If the fraud engine uses a naive `GET count` followed by `INCR count`, all 20 threads read `count = 4` against a limit of 5. All 20 threads approve the transaction, allowing 15 fraudulent payments to bypass the velocity limit before the counter reflects the true count.

```
THREAD A:  ─── Read (count=4) ──────────── Approve ─── Set (count=5) ───►
THREAD B:  ───── Read (count=4) ────────── Approve ─── Set (count=5) ───►  [LIMIT BYPASSED]
THREAD C:  ─────── Read (count=4) ──────── Approve ─── Set (count=5) ───►
```

#### The Architecture Solution: Two-Layer Atomic Concurrency
Aegis eliminates this race condition without sacrificing checkout latency:
1. **Kafka Partition Ordering:** Ingested streams are partitioned by `tenant_id:account_id_hash`, guaranteeing that events for a single account land on the same consumer thread sequentially.
2. **Atomic Redis Lua Scripting ([ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md)):** For synchronous API evaluation, the engine executes a single atomic Lua script on Redis:
   - Evaluates the sliding window using Redis sorted sets (`ZREMRANGEBYSCORE`).
   - Checks if `event_id` is already a member (idempotency layer).
   - Counts active members (`ZCARD`).
   - If count $\ge$ limit, returns `DECLINE` or `REVIEW` without mutating state.
   - If count $<$ limit, executes `ZADD` with `event_time` as the score and returns `APPROVE`.
   - The entire check-and-increment executes in a single Redis thread, making race conditions impossible.

#### Benchmark Harness Specification & Test Methodology
- **Test Class:** `tools/tests/concurrency/ConcurrencyLimitIntegrationTest.java` (invoked via Gradle in CI).
- **Test Environment:** Docker Testcontainers running Redis 7.2 alpine on developer workstation (measured **7.79 GB physical RAM, 4 CPU cores, Windows 11 / WSL2** per [DR-TEST-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md)).
- **Workload:** 20 virtual threads (`java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()`) simultaneously submitting HTTP `POST /v1/decisions` for the same account ID with limit $L = 5$ in a 600-second window.
- **Assertion:** 
  $$\sum \text{verdict}_{\text{APPROVE}} = 5 \quad \text{and} \quad \sum \text{verdict}_{\text{DECLINE/REVIEW}} = 15$$
  $$\text{Over-allocation} = 0$$

---

### 2.2 Engineering Proof 2: "Surviving Duplicate Kafka Deliveries with Idempotent Decision Execution"

#### The Problem: At-Least-Once Delivery Distortion
Kafka message brokers operate on at-least-once delivery guarantees. During consumer rebalances, network blips, or worker restarts, the same transaction message is routinely redelivered 2 to 5 times. 

In a fraud engine, duplicate delivery creates catastrophic false positives:
- The velocity counter increments repeatedly for the same transaction, artificially pushing an innocent user over their daily spending count.
- Duplicate decision rows are written to the database, distorting monthly merchant billing and auditing records.

#### The Architecture Solution: Two-Tier Deduplication
Aegis guarantees exactly-once decision execution semantics across distributed workers:
1. **Tier 1 (Redis Fast Path, [ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md)):**
   - The Redis sorted set member is `event_id` (a unique UUID).
   - The Lua script executes `ZSCORE key event_id` before counting. If the `event_id` already exists, the script returns `pass: true, duplicate: true` without incrementing the counter.
2. **Tier 2 (PostgreSQL Transactional Outbox, [ADR-0002](file:///c:/dev/aegis/docs/adr/0002-durable-decision-write-before-response.md)):**
   - The database schema enforces a unique constraint on `processed_events (tenant_id, event_id)`.
   - The decision write and `processed_events` insert occur within the exact same database transaction (`SERIALIZABLE` or `READ COMMITTED` with row lock).
   - If a duplicate message slips past Redis, the database transaction throws a unique constraint violation, catches the duplicate, and retrieves the original decision record without re-evaluating rules.

#### Benchmark Harness Specification & Test Methodology
- **Test Class:** `tools/tests/idempotency/DuplicateDeliveryIntegrationTest.java`.
- **Test Environment:** Testcontainers Kafka 3.7 (KRaft) + PostgreSQL 16 + Redis 7.2 on developer workstation (**7.79 GB RAM, 4 cores**).
- **Workload:** A single transaction payload `event_id: E_TEST_01` published to the Kafka ingest topic 10 times consecutively with randomized 10–50 ms jitter.
- **Assertion:**
  - `decisions` table count where `event_id = 'E_TEST_01'` equals **exactly 1**.
  - `processed_events` count equals **exactly 1**.
  - Final Redis velocity sorted-set cardinality equals **exactly 1**.

---

### 2.3 Engineering Proof 3: "Deterministic Replay with Event-Time Feature Computation"

#### The Problem: Wall-Clock Drift in Backtesting
Most fraud engines evaluate rules against current system time (`Instant.now()`). If a risk manager tests a rule against last month's data, evaluating transactions that occurred at 10:00 AM on September 1st using a sliding window will calculate velocity relative to *today's* wall-clock time, resulting in an empty sliding window and false approvals.

Furthermore, if the replay engine sorts events by wall-clock arrival order rather than event occurrence order, replaying the same historical file on a 4-core machine vs an 8-core machine produces different thread interleavings, resulting in divergent verdicts.

```
Run 1 (Machine A):  Event 1 ──► Event 2 ──► Verdict: APPROVE
Run 2 (Machine B):  Event 2 ──► Event 1 ──► Verdict: DECLINE  [NON-DETERMINISTIC BUG]
```

#### The Architecture Solution: Injected Event Time & Total Ordering ([ADR-0003](file:///c:/dev/aegis/docs/adr/0003-event-time-for-deterministic-replay.md))
1. **Zero System Clock Dependency:** `EvaluationCore` accepts an explicit `Instant eventTime` parameter. Calling `System.currentTimeMillis()` or `Instant.now()` inside the evaluation core is strictly prohibited and flagged by static analysis rules ([AGENTS.md](file:///c:/dev/aegis/AGENTS.md)).
2. **In-Memory Sliding Window (`InMemoryVelocityWindow`):** Replay workers maintain an in-memory tree structure tracking historical window boundaries purely relative to `event_time`, matching the Redis Lua sliding window algorithm exactly ([ADR-0005 §Replay](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md#replay-in-memory-velocity-implementation)).
3. **Total Event Ordering:** Replay datasets are sorted deterministically by the composite key `(event_time, sequence_number)`. Where `sequence_number` is missing, synthetic tie-breakers use `(event_time, received_at)` ([DR-API-04](file:///c:/dev/aegis/docs/00-decisions/decision-register.md)).

#### Benchmark Harness Specification & Test Methodology
- **Test Class:** `tools/tests/replay/ReplayDeterminismTest.java`.
- **Test Environment:** Developer workstation (**7.79 GB RAM, 4 cores**).
- **Workload:** 100,000 synthetic financial events containing mixed UPI and Card transactions, loaded from a deterministic seed. The dataset is replayed twice in full:
  - Run 1: Single-threaded worker.
  - Run 2: Multi-threaded parallel batch worker (4 threads).
- **Assertion:**
  $$\text{Divergence Rate} = \frac{\sum |\text{Verdict}_{\text{Run1}} \neq \text{Verdict}_{\text{Run2}}|}{100,000} = \mathbf{0.00\%}$$

---

## 3. Pilot Case Study Framework (Commercial Partner Post-Gate G2)

### 3.1 Customer Approval and Anonymisation Standards
To protect partner commercial sensitivity and ensure compliance with the DPDP Act 2023, every case study follows non-negotiable publication rules:
1. **Written Executive Sign-Off:** No case study is published without written consent from the customer's Chief Risk Officer or VP Engineering.
2. **Default Anonymisation:** Unless explicit permission is granted to use corporate logos, the customer is identified by vertical and scale:
   - *Example:* "A Leading Tier-2 Indian Payment Aggregator Processing ₹4,500 Cr Monthly".
3. **Zero Identifiers:** Case studies never include merchant IDs, account hashes, IP addresses, or raw amounts.
4. **Normalized Impact Metrics:** Results are presented as verified percentage deltas and normalized currency totals, preventing disclosure of underlying gross transaction margins.

---

### 3.2 Standard Pilot Case Study Template

```markdown
# CASE STUDY: How [Customer Anonymized Title] Cut False Declines by [X]% in 4 Weeks

**Customer Profile:** Mid-size Indian Payment Aggregator  
**Monthly Volume:** [e.g. 6.2 Million Transactions / Month]  
**Payment Rails:** UPI (72%), Debit/Credit Cards (21%), NetBanking (7%)  
**Engagement Mode:** Tier 1 Shadow Pilot (Zero Live Payment Disruption)  
**Verification Gate:** Gate G2 Validated  

---

### Executive Summary
[Customer Anonymized Title] was experiencing checkout drop-offs during high-velocity 
flash sales. Their legacy rule engine was generating false declines on legitimate 
UPI payments, costing merchants valuable customer goodwill.

By running a 4-week shadow pilot on Project Aegis using the Free Historical Backtest, 
the risk team safely simulated and deployed 14 optimized Common Expression Language 
(CEL) rules. The pilot achieved:
- [X]% reduction in false positive declines (saving an estimated ₹[Y] Lakhs in merchant revenue).
- [Z]% incremental fraud catch on bot-driven card testing attacks.
- 18.2 ms average server-side evaluation latency (p99 under 75 ms).

---

### The Challenge: The Cost of False Positives
During peak retail sale events, UPI transaction velocity frequently surges 
from 50 TPS to over 400 TPS. The customer's legacy in-house rules relied on 
rigid static database queries. 

To curb card testing attacks, risk analysts tightened transaction limits, but 
lacked the tooling to simulate rule changes against historical traffic. The result:
- Hundreds of genuine high-value users were blocked.
- Rule authors spent 2 weeks running offline SQL queries to verify each threshold change.
- Upstream checkout timeout envelopes were regularly breached during database lock contention.

---

### The Solution: 48-Hour Backtest and Async Shadow Pilot
1. **Zero-Trust Dataset Import (Decision D8):**
   The customer used the Aegis Dataset Import Tool to upload 90 days of historical 
   transaction records. Account numbers were client-side hashed (SHA-256), and raw 
   PANs were completely excluded.
2. **Replay Simulation:**
   Aegis ran the historical dataset through candidate rules in under 12 minutes, 
   rendering a complete confusion matrix that proved 18.4% of past declines were false alarms.
3. **Async Shadow Mirroring:**
   The customer mirrored live Kafka transaction streams to Aegis in Tier 1 shadow mode. 
   Aegis evaluated all payments in real time without blocking checkouts, proving 
   sub-25ms latency and zero drift over 4 weeks.

---

### Measured Results and Business Impact

| Performance Dimension | Baseline System | Project Aegis (Pilot) | Verified Improvement |
|---|---|---|---|
| **False Positive Rate (FPR)** | [e.g. 2.14%] | [e.g. 1.74%] | **-18.7% False Declines** |
| **Incremental Fraud Caught** | Baseline | +42 Bot Attacks | **+12.4% Detection** |
| **p99 Server Evaluation Latency** | 145 ms | 68 ms | **53.1% Latency Reduction** |
| **Rule Deployment Cycle Time** | 14 Days | 3.5 Hours | **97.5% Faster Releases** |

> *"The ability to run 90 days of history through a new velocity threshold in 10 minutes 
> completely eliminated our fear of deploying rules. We proved our ROI before spending 
> a single rupee on production integration."*  
> — **Head of Risk & Fraud Operations, [Customer Anonymized Title]**

---

### Test Methodology and Environmental Notes
- **Testing Period:** [Month Year] to [Month Year].
- **Infrastructure:** AWS Mumbai (`ap-south-1`), Tier 1 Pilot footprint.
- **Dataset Size:** 5,420,000 transaction events evaluated.
- **Validation Standard:** Audited per Project Aegis Consistency Report (`docs/audit/consistency-report.md`).
```
