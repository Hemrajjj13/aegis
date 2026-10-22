# Test and Performance Strategy

**Document:** 11  
**Status:** Approved (docs v1.0, assumption-based)
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

> **Assumption note:** Numeric performance targets, latency bounds, and throughput capacities in this document are hypotheses to validate by load test. Do not commit these numbers in customer-facing Service Level Agreements (SLAs) until all corresponding test suites pass in a staging environment.

---

## 1. Purpose and Engineering Principles

Project Aegis is a real-time, mission-critical financial decision engine. Correctness bugs (such as double-counting velocity or non-deterministic replays) directly destroy customer trust, while latency degradation (p99 > 100 ms server-side) breaches payment gateway processing windows.

This strategy establishes how the engineering team verifies system behavior across correctness, concurrency, throughput, and fault tolerance:
1. **Deterministic by Design:** Test suites enforce mathematical and algorithmic determinism. Time is always an injected parameter ([ADR-0003](file:///c:/dev/aegis/docs/adr/0003-event-time-for-deterministic-replay.md)).
2. **Real Infrastructure in Tests:** Unit tests mock external boundaries, but integration tests use real managed dependencies (PostgreSQL 16, Redis 7, Kafka 3.x) orchestrated via Testcontainers. Mocking database constraints or Redis Lua behavior is explicitly prohibited.
3. **Open-Model Load Testing:** Performance is measured using open-model arrival-rate workloads (k6 / Gatling) where arrival rate is decoupled from system response time, preventing coordinated omission.
4. **Automated CI Gates for Core Invariants:** Concurrency limits, duplicate-delivery idempotency, and Lua-vs-in-memory velocity equivalence must pass on every pull request.

---

## 2. The Test Pyramid

```
                       ┌─────────────────────────┐
                       │     Chaos & DR Drills   │  Monthly game days,
                       │  (Postgres/Redis/Kafka) │  RDS multi-AZ failover
                       ├─────────────────────────┤
                       │  Open-Model Load Tests  │  k6 / Gatling sustained
                       │  (2,000–5,000 TPS load) │  2,000 req/s, soak tests
                       ├─────────────────────────┴─────────┐
                       │   End-to-End & Replay Validation  │  Deterministic replay diffs,
                       │ (Sync API, Ingest, Outbox Relay)  │  Full vertical slice testing
                       ├───────────────────────────────────┴─────────┐
                       │      Integration Tests (Testcontainers)     │  Real Postgres RLS, real Redis
                       │ (Concurrency, Idempotency, Equivalence Test)│  Lua scripts, real Kafka topics
                       ├─────────────────────────────────────────────┴─────────┐
                       │         Unit & Architectural Rule Tests               │  JUnit 5, CEL compiler tests,
                       │  (ArchUnit clock bans, rule parsing, scoring clamping)│  Pure functions, runs in <30s
                       └───────────────────────────────────────────────────────┘
```

| Test Layer | Execution Frequency | Target Runtime | Environment / Dependencies | Pass Criteria |
|---|---|---|---|---|
| **Unit & ArchUnit** | Every commit / PR | < 30 seconds | In-process JVM; zero network/disk I/O | 100% pass; 0 calls to wall-clock in `evaluation-core` |
| **Integration** | Every PR | < 5 minutes | Testcontainers (Postgres, Redis, Kafka) | Concurrency: exactly $L$ of $N$; duplicate: 1 decision |
| **Replay & Equivalence**| Every PR | < 3 minutes | In-process JVM + Redis Testcontainer | Zero divergence between Lua and in-memory replay |
| **Load & Performance** | Nightly / Release candidate | 2 hours | Dedicated AWS Staging (mirrors production) | p50 ≤ 25 ms, p99 ≤ 100 ms at 2,000 req/s (hypotheses) |
| **Chaos Drills** | Pre-launch / Monthly | 4 hours | Staging environment with Toxiproxy / Chaos Mesh | RTO < 30 min, RPO < 5 min; no lost decisions (hypotheses) |

---

## 3. Testcontainers Integration Setup

All integration tests inherit from an abstract base class `AbstractIntegrationTest` that provisions containerized instances of PostgreSQL, Redis, and Kafka.

```
┌─────────────────────────────────────────────────────────────┐
│ Integration Test Execution (JUnit 5)                        │
│                                                             │
│  ┌──────────────────────┐     ┌──────────────────────────┐  │
│  │ PostgreSQL Container │     │ Redis Container          │  │
│  │ (postgres:16-alpine) │     │ (redis:7.2-alpine)       │  │
│  │ - Flyway Migrations  │     │ - Preloaded Lua scripts  │  │
│  │ - Forced RLS Policies│     │ - Hash-tag slot pinning  │  │
│  └──────────────────────┘     └──────────────────────────┘  │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Kafka Container (confluentinc/cp-kafka:7.5.0)          │  │
│  │ - Ingest, decisions, outbox-relay, dlq topics         │  │
│  │ - Confluent Schema Registry (Avro validation)         │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 3.1 Container Lifecycle and Dynamic Properties

```java
// Conceptual Architecture Pattern for Integration Tests
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = 
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("aegis_test")
            .withUsername("aegis_app")
            .withPassword("test_secret");

    @Container
    protected static final GenericContainer<?> REDIS = 
        new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    @Container
    protected static final KafkaContainer KAFKA = 
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
```

### 3.2 Pre-Test Initialization Routine
1. **Flyway Migration:** Automatically applies all schema migrations from `db/migration/V*.sql` (creating RLS policies, tables, and constraints from [07-data-model.md](file:///c:/dev/aegis/docs/07-data-model.md)).
2. **Redis Script Loading:** Executes `SCRIPT LOAD` for the production velocity Lua script ([ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md)) and stores the SHA-1 digest in application configuration.
3. **Kafka Topic Provisioning:** Ensures topics `ingest`, `decisions`, `outbox-relay`, and `dlq` are initialized with replication factor 1 and 3 partitions.

---

## 4. Key Correctness CI Tests

The following four tests are mandatory CI gates. A failure in any of these tests blocks pull request merging.

### 4.1 The Concurrency Test (`VelocityConcurrencyIntegrationTest`)
- **Objective:** Prove that two or more simultaneous transactions cannot bypass a sliding-window velocity limit.
- **Setup:**
  - Tenant: `tenant-concurrency-test`.
  - Velocity Rule: Maximum $L = 5$ transactions per 60-second window for an account.
  - Test Parameters: Launch $N = 20$ concurrent threads using Java virtual threads.
  - Coordination: All $N$ threads construct valid payment requests for the *same* account identifier and wait on a `java.util.concurrent.CyclicBarrier`.
- **Execution:**
  - The barrier trips, releasing all 20 threads simultaneously against `POST /v1/decisions`.
- **Assertions:**
  1. Exactly $L = 5$ requests receive HTTP 200 with `outcome = "APPROVE"`.
  2. Exactly $N - L = 15$ requests receive HTTP 200 with `outcome = "DECLINE"` (or review), with `reason_code = "VELOCITY_LIMIT_EXCEEDED"`.
  3. The Redis sorted set contains exactly 5 members.
  4. The PostgreSQL table `decisions` records exactly 20 total decision rows (5 approved, 15 declined).
  5. The reconciliation job reports zero discrepancy between Redis counters and PostgreSQL decisions.

### 4.2 Duplicate-Delivery Idempotency Test (`DuplicateDeliveryIntegrationTest`)
- **Objective:** Verify that duplicate API retries or Kafka consumer redeliveries do not inflate velocity counters or write duplicate decision rows.
- **Setup:**
  - Construct transaction event $E_1$ with unique `event_id = "evt_dup_001"` and `amount_inr = 5000.0`.
- **Execution:**
  - Submit $E_1$ synchronously to `decision-api`. (Call 1)
  - Submit the identical payload $E_1$ immediately. (Call 2)
  - Redeliver $E_1$ directly onto the Kafka `ingest` topic. (Call 3)
- **Assertions:**
  1. All three calls return the identical `decision_id` and `outcome`.
  2. PostgreSQL table `decisions` has exactly 1 row for `event_id = "evt_dup_001"`.
  3. PostgreSQL table `processed_events` has exactly 1 row for `(tenant_id, event_id)`.
  4. Redis velocity sorted set contains exactly 1 entry for `evt_dup_001`. The counter reflects count = 1, not 3 ([ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md)).
  5. Call 2 returns HTTP 200 served from the idempotency cache in < 5 ms.

### 4.3 Replay Determinism Test (`ReplayDeterminismTest`)
- **Objective:** Verify that replaying historical transactions through an identical ruleset produces bit-for-bit identical results on repeated runs ([ADR-0003](file:///c:/dev/aegis/docs/adr/0003-event-time-for-deterministic-replay.md)).
- **Setup:**
  - Synthetic dataset $D$ containing 10,000 historical payment events with varied timestamps and concurrent bursts.
  - Ruleset $R_1$ with 15 soft and hard CEL rules.
- **Execution:**
  - Worker 1 executes Replay Run A with parameters $(D, R_1)$, writing results to `replay_decisions` under `run_id = run_A`.
  - Worker 2 executes Replay Run B with identical parameters $(D, R_1)$ 30 minutes later on a separate thread, writing to `run_id = run_B`.
- **Assertions:**
  1. `count(replay_decisions WHERE run_id = run_A) == count(replay_decisions WHERE run_id = run_B) == 10,000`.
  2. A SQL full outer join on `(event_id)` between `run_A` and `run_B` reveals **0 rows** with differing `outcome`, `score`, or `rules_fired`.
  3. Live operational table `decisions` was accessed with 0 INSERT, 0 UPDATE, and 0 DELETE operations.

### 4.4 Velocity Implementation Equivalence Test (`VelocityImplementationEquivalenceTest`)
- **Objective:** Guarantee that the production Redis Lua script and the replay in-memory window (`InMemoryVelocityWindow`) produce identical verdicts and counts across all edge cases ([ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md)).
- **Generator Specification:** Generates an ordered stream of 5,000 events for an account:
  - Sequence includes exact boundary cases ($L-1$, $L$, $L+1$).
  - Sequence includes duplicate `event_id` occurrences spaced at $t+0$, $t+10\text{s}$, and $t+300\text{s}$.
  - Sequence includes expired events ($t > \text{window\_size}$).
  - Sequence includes identical timestamp events with differing sequence numbers.
- **Execution:**
  - For each event, invoke:
    1. `runRedisLua(key, eventId, eventTime, windowMs, limit)` against real Redis Testcontainer.
    2. `inMemoryWindow.check(eventId, eventTime, windowMs, limit)` in Java memory.
- **Assertions:**
  - For 100% of the 5,000 events:
    $$\text{verdict}_{\text{lua}} == \text{verdict}_{\text{inmem}} \quad \land \quad \text{count}_{\text{lua}} == \text{count}_{\text{inmem}} \quad \land \quad \text{duplicate}_{\text{lua}} == \text{duplicate}_{\text{inmem}}$$

---

## 5. Synthetic Fraud Data Generator Specification

To test rules and run backtests before customer data is integrated, Aegis includes an internal synthetic fraud generator.

### 5.1 Modeled Fraud Patterns

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Synthetic Fraud Generator: Attack Pattern Modules                           │
│                                                                             │
│  ┌─────────────────────────┐          ┌──────────────────────────────────┐  │
│  │ 1. Card Testing / Probe │          │ 2. Velocity Limit Draining       │  │
│  │ - 10-50 micro-txns      │          │ - Automated bot bursts           │  │
│  │ - Sequential card tokens│          │ - ₹10,000 txns every 5 seconds   │  │
│  │ - Rapid decline rate    │          │ - Single account / token         │  │
│  └─────────────────────────┘          └──────────────────────────────────┘  │
│                                                                             │
│  ┌─────────────────────────┐          ┌──────────────────────────────────┐  │
│  │ 3. Impossible Travel    │          │ 4. Mule Account Ring Layering    │  │
│  │ - Mumbai IP (t = 0)     │          │ - High-value inbound UPI         │  │
│  │ - London IP (t = 15m)   │          │ - Immediate fan-out to 8 accounts│  │
│  │ - Velocity delta > 800km│          │ - Dormant account reactivation   │  │
│  └─────────────────────────┘          └──────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

1. **Card Testing / Enumeration:**
   - Characteristics: High frequency (10–50 events in 3 minutes), low amount (₹1.00 to ₹10.00), single merchant identifier, sequential or slightly varying card tokens.
   - Purpose: Validates rapid card burst rules (`RUL-VEL-001`) and velocity windows.
2. **Velocity Bursts:**
   - Characteristics: Automated checkout script attempting maximum transaction limit (e.g., ₹10,000) every 5 seconds until limit exceeded.
   - Purpose: Validates exact boundary threshold clamping and Redis Lua atomicity.
3. **Impossible Travel / Geo-Velocity:**
   - Characteristics: Transaction 1 originating from Mumbai IP (`103.x.x.x`), followed 15 minutes later by Transaction 2 originating from London IP (`185.x.x.x`) on the same `account_id`.
   - Purpose: Validates geographic distance feature extractors and cross-border IP rules (`RUL-GEO-004`).
4. **Mule Account Rings & Layering:**
   - Characteristics: A dormant account receives a sudden inbound credit (₹200,000), followed within 120 seconds by rapid outbound transfers of ₹25,000 each to 8 distinct beneficiary hashes.
   - Purpose: Validates dormant account reactivation burst (`RUL-DORM-008`) and rapid beneficiary velocity.

### 5.2 Labelling Method and Stated Limitations

Each synthetic transaction is generated with deterministic ground-truth metadata:
```json
{
  "event_id": "syn_tx_94821",
  "is_synthetic": true,
  "ground_truth_label": "FRAUD",
  "fraud_scenario": "IMPOSSIBLE_TRAVEL",
  "expected_rules_fired": ["RUL-GEO-004", "RUL-TIME-003"],
  "chargeback_probability": 0.95
}
```

> **Stated Limits of Synthetic Data:**
> 1. Synthetic data models *known* fraud topologies. It cannot replicate genuine adversarial adaptation (fraudsters discovering zero-day rule flaws).
> 2. Synthetic noise distributions cannot substitute for real merchant traffic nuances (e.g., flash sales, Diwali shopping surges, network retry blips).
> 3. Synthetic datasets are valid for determinism testing, regression testing, and load sizing; they are **not** proof of real-world precision and recall. Final rule efficacy must be measured on pilot partner shadow data.

---

## 6. Performance and Load Testing Strategy

### 6.1 Why Open-Model Load Generation?
Traditional load testing tools (like standard JMeter or basic HTTP benchmarks) operate on a **closed model**: a fixed number of Virtual Users (VUs) send a request, wait for a response, and then send the next request. If the server becomes slow, the VUs wait, artificially reducing the arrival rate. This creates the **coordinated omission** problem, concealing long tail latency (p99/p99.9).

Aegis uses **open-model workloads** generated via **k6** (`scenarios.executor = 'constant-arrival-rate'` or `'ramping-arrival-rate'`) and **Gatling**:
- Arrival rate is strictly enforced: transactions are emitted at the target rate (e.g., exactly 2,000 events/second) regardless of server response latency.
- If server latency spikes, in-flight connections queue at the load balancer, accurately exposing server-side saturation and thread pool starvation.

### 6.2 Load Profiles (All figures are hypotheses to validate)

```
Target TPS
  5,000 ─┐                                      ┌──────┐ (Peak Spike)
         │                                      │      │
  2,000 ─┼───────────────────────────┐          │      │
         │ (Steady State Baseline)   │          │      │
  1,000 ─┼──────────────┐            │          │      │
         │ (Warm-up)    │            │          │      │
         └──────────────┴────────────┴──────────┴──────┴─────────► Time
           0-10m          10m-2h                  Peak Burst
```

1. **Steady-State Baseline (Sustained Load):**
   - Profile: Ramp to 2,000 decisions/second over 10 minutes; hold steady for 2 hours.
   - Payload Mix: 65% low-velocity approve, 10% boundary velocity, 5% blocklist, 5% concurrent arrivals, 5% duplicates, 5% degraded timeout, 5% malformed ([doc 01 §3.4](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L93-L110)).
   - Pass Criteria: Server-side p50 ≤ 25 ms, p99 ≤ 100 ms; HTTP 5xx error rate < 0.05% (hypotheses, to validate).
2. **Peak Burst / Spike Test:**
   - Profile: Step-jump from 2,000 TPS to 5,000 TPS in 10 seconds; hold for 5 minutes; drop back to 2,000 TPS.
   - Pass Criteria: No unhandled 500 errors; graceful degradation (fail policy applied for timeouts); system recovers p99 to < 100 ms within 30 seconds of load normalization (hypotheses, to validate).
3. **24-Hour Soak Test:**
   - Profile: Sustained 1,500 decisions/second for 24 continuous hours.
   - Pass Criteria: Zero JVM memory leaks (heap usage stabilizes after GC); HikariCP connection pool remains stable; Redis memory stabilizes without key leaks; consumer lag remains < 5 s (hypotheses, to validate).
4. **Stress / Breakpoint Test:**
   - Profile: Incrementally increase arrival rate by 500 TPS every 5 minutes until system fails.
   - Objective: Identify the first saturation point (PostgreSQL connection pool, Redis CPU, or virtual thread scheduler).

---

## 7. Chaos Drills (Fault Injection)

Chaos tests are conducted using Toxiproxy (network partitions, latency injection) and AWS Fault Injection Simulator (FIS) in the staging environment.

### 7.1 Drill 1: Redis Primary Failover
- **Fault Injected:** Send `SIGKILL` to active Redis master container or execute AWS ElastiCache forced failover.
- **Expected System Behavior:**
  1. Redis client detects disconnection; attempts reconnection to replica.
  2. In-flight velocity checks exceed the individual 10 ms feature budget.
  3. Circuit breaker trips. The evaluation core applies the tenant's configured fail policy (fail-open returns `approve` with `degraded = true`; fail-closed returns `decline` with `degraded = true`).
  4. Replica promotes to master; reconnection succeeds within 15 seconds.
  5. Reconciliation job runs and corrects any counter drift within 5 minutes.
  6. **Launch Gate:** Zero uncaught exceptions; every degraded decision is logged with `degraded_reason = "REDIS_TIMEOUT"`.

### 7.2 Drill 2: Kafka Broker Failure
- **Fault Injected:** Stop 1 broker in the 3-broker managed Kafka cluster during sustained 1,000 TPS async ingestion.
- **Expected System Behavior:**
  1. Kafka partition leader election completes in < 3 seconds.
  2. Producers with `acks=all` experience brief latency blip (< 500 ms) but zero message loss.
  3. Stream processor consumers re-balance partitions. Consumer lag temporarily spikes but recovers to < 5 seconds within 60 seconds (hypothesis, to validate).
  4. Dead-letter queue depth remains 0.

### 7.3 Drill 3: PostgreSQL Multi-AZ Failover
- **Fault Injected:** Execute `aws rds reboot-db-instance --force-failover` on production primary PostgreSQL instance.
- **Expected System Behavior:**
  1. Primary instance terminates; standby replica promotes to primary.
  2. Synchronous `decision-api` receives connection errors: returns HTTP 503 Service Unavailable with `Retry-After: 5` header.
  3. Async `stream-processor` catches connection loss, pauses offset commits, and buffers in-flight records.
  4. DNS / connection pool re-establishes connection to the new primary in < 30 seconds.
  5. Stream processor resumes processing; zero duplicate decisions written due to `processed_events` constraint.
  6. Target Recovery Time Objective (RTO) < 30 minutes; Recovery Point Objective (RPO) < 5 minutes (hypotheses, to validate).

---

## 8. Continuous Integration (CI) Gates

Every code change must pass through automated CI pipelines in GitHub Actions before deployment.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Pull Request Pipeline (Max 10 Minutes)                                      │
│                                                                             │
│  ┌────────────────┐     ┌──────────────────┐     ┌───────────────────────┐  │
│  │ 1. Static Scan │───► │ 2. Unit Tests    │───► │ 3. Integration Tests  │  │
│  │ - Checkstyle   │     │ - JUnit 5 tests  │     │ - Testcontainers      │  │
│  │ - Semgrep SAST │     │ - ArchUnit bans  │     │ - Concurrency test    │  │
│  │ - Dependency   │     │ - CEL AST safety │     │ - Idempotency test    │  │
│  └────────────────┘     └──────────────────┘     │ - Equivalence test    │  │
│                                                  └───────────┬───────────┘  │
│                                                              │              │
│                                                              ▼              │
│                                                  ┌───────────────────────┐  │
│                                                  │ 4. Replay Determinism │  │
│                                                  │ - 10k synthetic diff  │  │
│                                                  │ - Live isolation check│  │
│                                                  └───────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

1. **Gate 1: Static Analysis & Security Scan (Runtime: < 2 min):**
   - Checkstyle & Spotless code formatting.
   - ArchUnit tests: Enforces that `evaluation-core` contains zero references to `Clock.systemUTC()`, `Instant.now()`, or `LocalDateTime.now()` ([ADR-0003](file:///c:/dev/aegis/docs/adr/0003-event-time-for-deterministic-replay.md)).
   - Semgrep & Snyk: Dependency vulnerability scan (zero high/critical CVEs).
2. **Gate 2: Unit & Domain Tests (Runtime: < 1 min):**
   - 100% pass on rule parsing, CEL evaluation, score clamping, and reason code formatting.
3. **Gate 3: Testcontainers Integration Suite (Runtime: < 5 min):**
   - `VelocityConcurrencyIntegrationTest` (20 parallel requests at limit 5).
   - `DuplicateDeliveryIntegrationTest` (3x identical delivery).
   - `VelocityImplementationEquivalenceTest` (Lua vs. in-memory equivalence).
   - `CrossTenantIsolationSecurityTest` (RLS leakage validation).
4. **Gate 4: Replay Determinism Verification (Runtime: < 2 min):**
   - Runs replay engine over synthetic dataset; asserts zero bit-level diff across repeat runs.

---

## 9. Launch Gate Verification Mapping (Document 01 §12)

This table maps every correctness, reliability, and performance item in [01-production-readiness-discovery.md §12](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L490-L545) to its automated test and validation evidence.

| Launch Gate Checklist Item (Doc 01 §12) | Verifying Test Suite / Automation Tool | Execution Target & Pass Threshold | Document Reference |
|---|---|---|---|
| **SLOs defined and visible on dashboard** | Prometheus / Grafana provisioning script | Metrics populated on staging dashboard | [doc 01 §8.1](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L380-L405) |
| **Load test at 2× expected peak TPS (2,000 req/s)** | k6 open-model load test (`load-steady-state.js`) | Sustained 2,000 TPS; p50 ≤ 25 ms, p99 ≤ 100 ms (hypotheses) | [doc 01 §3.1](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L52-L73) |
| **Chaos test: Redis failover & failure policy** | Chaos test script (`test-chaos-redis-failover.sh`) | Fail-open / fail-closed matches tenant config; no crashes | [doc 01 §4](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L121-L145) |
| **Chaos test: Kafka broker failure & lag recovery** | AWS FIS / Toxiproxy broker termination drill | Consumer lag recovers to < 5 s; DLQ depth = 0 (hypotheses) | [doc 01 §3.3](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L86-L92) |
| **Postgres failover & RTO/RPO drill** | AWS RDS forced failover simulation | RTO < 30 min, RPO < 5 min confirmed (hypotheses) | [doc 01 §7.1](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L315-L325) |
| **Point-in-time restore tested** | AWS RDS PITR drill to isolated test instance | Restored data matches source snapshot exactly | [doc 01 §7.2](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L325-L332) |
| **Concurrency test: N parallel at limit L** | `VelocityConcurrencyIntegrationTest` (CI Gate) | Exactly L of N pass; zero limit breaches | [ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md) |
| **Velocity idempotency test** | `VelocityIdempotencyIntegrationTest` (CI Gate) | Same `event_id` 2x yields 1 counter increment | [ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md) |
| **Duplicate delivery test via Kafka** | `DuplicateDeliveryIntegrationTest` (CI Gate) | Same `event_id` 3x yields exactly 1 decision row | [overview.md §6.4](file:///c:/dev/aegis/docs/05-architecture/overview.md#L277-L298) |
| **Replay determinism test** | `ReplayDeterminismTest` (CI Gate) | Two runs of same dataset yield identical decisions | [ADR-0003](file:///c:/dev/aegis/docs/adr/0003-event-time-for-deterministic-replay.md) |
| **Replay isolation confirmed** | `ReplayIsolationSecurityTest` (CI Gate) | Replay role has zero INSERT rights on live tables | [07-data-model.md §2](file:///c:/dev/aegis/docs/07-data-model.md#L30-L50) |
| **Velocity equivalence test (Lua vs. in-memory)** | `VelocityImplementationEquivalenceTest` (CI Gate) | 100% match on verdict, count, duplicate flag | [ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md) |
| **Audit sealer verification test** | `AuditSealerVerificationTest` (CI Gate) | 10k synthetic decisions; Merkle proof passes CLI check | [ADR-0004](file:///c:/dev/aegis/docs/adr/0004-batched-audit-log-hash-chaining.md) |
| **PAN prevention test** | `PanPreventionSecurityTest` (CI Gate) | Schema rejects PAN in int field; Luhn in text returns 400 | [doc 01 §5.1](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L115-L130) |
| **All alerts tested against runbooks** | Game day alert simulation drill | All alerts in §8.4 fire; alert links resolve to runbook | [doc 01 §8.4](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L410-L445) |

---

## 10. Decision Register References (formerly Open Questions)

All testing and performance strategy questions have been resolved or classified per Decision **D3** in the [Decision Register](file:///c:/dev/aegis/docs/00-decisions/decision-register.md):

| # | Decision ID | Question / Topic | Status | Decision / Assumed Value |
|---|---|---|---|---|
| 1 | [DR-TEST-05](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Ground Truth Labelling Delay:** Dispute maturation window for replay matrices. | Decided | 60-day chargeback maturation lag assumed; transactions $< 60$ days evaluated as provisional labels. |
| 2 | [DR-TEST-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Load Test Environment Parity:** Hardware and environment baselines. | Decided | Standardized developer workstation (7.79 GB RAM, 4 cores) for Tier 0; CI runs on GitHub Actions; Tier 1 uses 50% topology. |
| 3 | [DR-TEST-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Chaos Engineering Automation:** Tooling and fault injection in staging. | Decided | Toxiproxy in Testcontainers for CI integration; manual AWS console game-day disruption drills in staging. |
| 4 | [DR-TEST-04](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Benchmark Dataset Sizing:** Event count and regression thresholds for CI. | Decided | 100k events for nightly CI benchmark ($\ge 15\%$ p99 latency regression fails CI); 1M events for weekly runs. |

