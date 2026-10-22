# Service Level Objectives (SLOs), Runbooks, and Disaster Recovery

**Document:** 12  
**Status:** Draft-complete (docs v1.0, machine-checked, not yet reviewed)
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

> **Assumption note:** Numeric thresholds, error budget windows, and recovery targets (RTO/RPO) in this document are hypotheses to validate during game days and staging load tests. Do not publish these values in binding customer Service Level Agreements (SLAs) until measured under production conditions.

---

## 1. Purpose and Reliability Principles

Project Aegis sits directly in the payment flow. When a payment gateway calls `POST /v1/decisions`, an outage or severe latency spike causes consumers' card and UPI transactions to fail or time out at checkout.

This document establishes operational discipline across three pillars:
1. **SLO-Driven Alerting:** Alerts fire on **error budget burn rates**, not transient CPU spikes or isolated single-request blips.
2. **Actionable Runbooks:** Every PagerDuty alert links directly to a runbook detailing symptoms, exact diagnostic CLI commands, mitigation steps, and escalation paths.
3. **Disaster Recovery and Derived State:** PostgreSQL is the single durable system of record. Redis is treated as derived state that can be completely rebuilt from PostgreSQL within 15 minutes (hypothesis, to validate).

---

## 2. Service Level Objectives (SLOs) and Error Budgets

All targets are measured over a **30-day rolling window**.

### 2.1 SLI and SLO Definitions

| Service Level Indicator (SLI) | Target (Hypothesis) | Measurement Specification | Monthly Error Budget |
|---|---|---|---|
| **Decision API Availability** | **99.95%** | $$\frac{\text{Successful requests (HTTP non-5xx)}}{\text{Total valid requests}} \times 100$$ | 0.05% (~21.9 minutes of allowed downtime per month) |
| **Decision API Latency** | **99.0%** | $$\frac{\text{Requests with server-side latency} \le 100\text{ ms}}{\text{Total successful requests}} \times 100$$ | 1.0% of requests exceeding 100 ms. Formula: $$2,000\text{ req/s} \times 86,400\text{ s/day} \times 30\text{ days} = 5,184,000,000\text{ total requests; } 1\% = 51,840,000\text{ requests (51.84M)}$$ |
| **Kafka Ingest Pipeline Lag** | **99.9%** | $$\frac{\text{1-minute intervals with consumer lag} < 5\text{ s}}{\text{Total 1-minute intervals}} \times 100$$ | 43 minutes of lag > 5 s per month |
| **Audit Log Integrity** | **100.0%** | $$\frac{\text{Sealed batches with unbroken Merkle hash chain}}{\text{Total sealed batches}} \times 100$$ | 0 broken chains permitted (zero budget) |
| **Replay Determinism** | **100.0%** | $$\frac{\text{Rerun batches with identical decision hashes}}{\text{Total rerun batches}} \times 100$$ | 0 unexplained divergences permitted |

### 2.2 Multi-Window Multi-Burn-Rate Alerting

Rather than alerting on arbitrary instantaneous thresholds, Aegis implements the Google SRE workbook pattern of multi-window multi-burn-rate alerting to protect the monthly error budget. The "Time to Exhaust" column represents the duration until 100% of the 30-day budget is depleted at the given burn rate ($30\text{ days} / \text{Burn Rate}$):

| Alert Severity | Burn Rate | Budget Consumed | Time to Exhaust (30d / Burn Rate) | Long Window | Short Window | Paging Policy |
|---|---|---|---|---|---|---|
| **P1 — Critical** | **14.4×** | 2% of budget consumed | 50.0 hours (~2.08 days) | 1 hour | 5 minutes | PagerDuty immediately (24/7 on-call) |
| **P2 — Major** | **6.0×** | 5% of budget consumed | 5.0 days (120 hours) | 6 hours | 30 minutes | PagerDuty immediately (24/7 on-call) |
| **P3 — Moderate** | **1.0×** | 10% of budget consumed | 30.0 days (720 hours) | 3 days | 6 hours | Slack notification to `#eng-alerts` (Business hours) |

---

## 3. Incident Severity Matrix

| Severity | Definition | Impact Examples | Response SLA | Comms Cadence | Incident Commander |
|---|---|---|---|---|---|
| **P1 (Critical)** | Catastrophic failure, data corruption, total service outage, or security breach. | Sync Decision API down; 5xx error rate > 5%; data loss; unauthorized rule published. | **≤ 5 minutes** | Public status update every 15 minutes | Engineering Lead / Founder |
| **P2 (Major)** | Severe performance degradation or loss of redundancy. | Decision API p99 > 200 ms; failover to secondary AZ active; consumer lag > 60 s; Redis cluster unreachable. | **≤ 15 minutes** | Internal updates every 30 minutes | Primary On-Call Engineer |
| **P3 (Minor)** | Localized degradation with functional workaround. | Single tenant hitting rate limits; console UI slow; audit batch sealer delayed by 5 minutes. | **≤ 2 hours** (business hours) | Updates on ticket resolution | Assigned Engineer |
| **P4 (Low)** | Cosmetic, documentation, or minor operational query. | UI display glitch; non-blocking log warning; minor metrics reporting gap. | **Next sprint** | JIRA ticket | Product Owner |

---

## 4. Runbooks for Document 01 §8.4 Alerts

### 4.1 Runbook: `runbooks/decision-api-latency.md`
**Alert:** `Decision API p99 latency > 200 ms for 2 min` (Warn/Page)

#### 1. Symptoms
- Prometheus alert `DecisionApiLatencyHigh` firing.
- Payment gateway callers experiencing connection timeouts or degraded response flags.
- Grafana dashboard shows p99 server-side response times climbing above 200 ms.

#### 2. Initial Checks and Triage
1. Check whether latency is isolated to a single tenant or global across all tenants.
2. Check PostgreSQL connection pool saturation and query execution times.
3. Check Redis Lua execution latency and CPU utilization.
4. Check whether third-party enrichment calls are timing out.

#### 3. Diagnostic Commands
```bash
# 1. Check pod CPU and virtual thread saturation in Kubernetes
kubectl top pods -n aegis-prod -l app=decision-api

# 2. Check active PostgreSQL connections and long-running queries
kubectl exec -it -n aegis-prod deploy/decision-api -- \
  psql -h $DB_HOST -U aegis_app -d aegis_prod -c "
    SELECT pid, now() - query_start AS duration, query, state 
    FROM pg_stat_activity 
    WHERE state != 'idle' AND now() - query_start > interval '200 milliseconds'
    ORDER BY duration DESC LIMIT 10;"

# 3. Check Redis response latency
kubectl exec -it -n aegis-prod deploy/decision-api -- \
  redis-cli -h $REDIS_HOST -a $REDIS_AUTH --latency-history

# 4. Check Redis slow log
kubectl exec -it -n aegis-prod deploy/decision-api -- \
  redis-cli -h $REDIS_HOST -a $REDIS_AUTH slowlog get 10
```

#### 4. Remediation Actions
1. **Database Lock Contention:** If queries on `processed_events` or `decisions` are blocking, terminate stuck blocking queries:
   ```sql
   SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE pid = <blocking_pid>;
   ```
2. **Scale Horizontal Pods:** If pod CPU is > 80%, manually increase replica count:
   ```bash
   kubectl scale deployment decision-api -n aegis-prod --replicas=12
   ```
3. **Trip Failing Circuit Breaker:** If an external enrichment provider is slow, force the circuit breaker open via the Spring Boot Actuator endpoint to bypass the step and apply fail policy:
   ```bash
   curl -X POST http://localhost:8081/actuator/circuitbreakers/enrichmentService/transitionToOpen
   ```
4. **Escalation:** If p99 remains > 200 ms after 5 minutes, escalate to P1 and page the Engineering Lead.

---

### 4.2 Runbook: `runbooks/decision-api-errors.md`
**Alert:** `Decision API error rate > 1% for 1 min` (Page)

#### 1. Symptoms
- PagerDuty notification `DecisionApiErrorRateElevated`.
- Callers receiving HTTP 500, 502, or 503 responses.

#### 2. Initial Checks
1. Review HTTP status distribution in Grafana (500 Internal Error vs. 503 Service Unavailable).
2. Check application logs for uncaught exceptions, OOM errors, or database connectivity drops.

#### 3. Diagnostic Commands
```bash
# 1. Inspect recent application error logs across pods
kubectl logs -n aegis-prod -l app=decision-api --tail=200 | grep -E "ERROR|EXCEPTION"

# 2. Verify database connectivity
kubectl exec -it -n aegis-prod deploy/decision-api -- \
  pg_isready -h $DB_HOST -p 5432 -U aegis_app

# 3. Check HikariCP connection pool metrics
curl -s http://localhost:8081/actuator/metrics/hikaricp.connections.active
curl -s http://localhost:8081/actuator/metrics/hikaricp.connections.pending
```

#### 4. Remediation Actions
1. **Connection Pool Exhaustion:** If `hikaricp.connections.pending` is spiking:
   - Check if a database migration or vacuum lock is holding connections.
   - Restart unhealthy pods rolling one by one:
     ```bash
     kubectl rollout restart deployment/decision-api -n aegis-prod
     ```
2. **Database Read-Only / Failover in Progress:** If errors are `PSQLException: FATAL: the database system is in recovery mode`:
   - An RDS multi-AZ failover is in progress. Verify failover status in AWS Console:
     ```bash
     aws rds describe-db-instances --db-instance-identifier aegis-prod-pg --query "DBInstances[0].DBInstanceStatus"
     ```
   - System will auto-recover when DNS updates (< 30s).
3. **Escalation:** If 5xx error rate exceeds 5% for > 3 minutes, declare a P1 incident and post initial status update.

---

### 4.3 Runbook: `runbooks/consumer-lag.md`
**Alert:** `Kafka consumer lag > 10 s` (Page)

#### 1. Symptoms
- PagerDuty notification `KafkaConsumerLagHigh`.
- Asynchronous decision events are delayed; console live feed lags behind real time.

#### 2. Initial Checks
1. Identify which consumer group and topic partition is lagging.
2. Check whether consumer pods have crashed or are stuck in GC pauses.

#### 3. Diagnostic Commands
```bash
# 1. Check consumer group lag across all partitions
kcat -b $KAFKA_BROKERS -L -J | jq .
kafka-consumer-groups.sh --bootstrap-server $KAFKA_BROKERS \
  --describe --group aegis-stream-processor-group

# 2. Check consumer container status and GC overhead
kubectl top pods -n aegis-prod -l app=stream-processor
jstat -gcutil <pid> 1000 5
```

#### 4. Remediation Actions
1. **Consumer Pod CrashLoop:** If pods are crashing due to OOM:
   - Check recent commits for memory leaks in feature buffers.
   - Temporarily increase container memory limit:
     ```bash
     kubectl set resources deployment stream-processor -n aegis-prod --limits=memory=4Gi
     ```
2. **Partition Hotspotting:** If a single partition has 90% of the lag:
   - Check whether an upstream tenant is sending all traffic using the same `tenant:account` partition key.
   - Contact the tenant to verify key distribution.
3. **Scale Consumers:** Scale the number of consumer pods up to the total partition count (e.g., from 3 to 6):
   ```bash
   kubectl scale deployment stream-processor -n aegis-prod --replicas=6
   ```

---

### 4.4 Runbook: `runbooks/dlq.md`
**Alert:** `DLQ depth > 0` (Page)

#### 1. Symptoms
- PagerDuty alert `DeadLetterQueueNotEmpty`.
- One or more messages failed processing after maximum retries and were diverted to `dlq`.

#### 2. Initial Checks
1. Determine how many messages are in the DLQ and their rate of arrival.
2. Inspect the dead-letter headers and error payloads to identify the failure cause (e.g., malformed JSON, schema violation, poison pill).

#### 3. Diagnostic Commands
```bash
# 1. Check total number of messages in the DLQ topic
kafka-run-class.sh kafka.tools.GetOffsetShell \
  --bootstrap-server $KAFKA_BROKERS --topic dlq --time -1

# 2. Consume and print the last 5 DLQ messages with headers
kcat -b $KAFKA_BROKERS -t dlq -C -o -5 -f 'Headers: %h\nPayload: %s\n---\n'
```

#### 4. Remediation and Reprocessing Actions
1. **Schema Mismatch:** If messages were rejected due to an unknown enum or missing required field:
   - Identify whether an upstream service rolled out an unannounced schema change.
   - Update and deploy consumer schema or notify the originating tenant.
2. **Re-Drive Procedure:** Once the root cause is resolved, re-drive DLQ messages back to the `ingest` topic using the internal replay CLI:
   ```bash
   java -jar /opt/aegis/tools/dlq-redrive.jar \
     --bootstrap-servers $KAFKA_BROKERS \
     --source-topic dlq \
     --target-topic ingest \
     --max-records 500
   ```
3. **Poison Pill Discard:** If the message is unrecoverable garbage (e.g., truncated network packet):
   - Document message payload in incident notes.
   - Commit consumer offset past the poison message to clear the alert.

---

### 4.5 Runbook: `runbooks/redis-drift.md`
**Alert:** `Redis reconciliation drift > 0` (Warn within 5 min)

#### 1. Symptoms
- CloudWatch / Prometheus alert `RedisVelocityDriftDetected`.
- Periodic reconciliation job detected a discrepancy between Redis sorted-set velocity counters and PostgreSQL `decisions` records.

#### 2. Initial Checks
1. Identify which `(tenant_id, account_id)` experienced drift and the magnitude of error (typically off by 1).
2. Check whether a Redis primary failover occurred in the last 15 minutes (asynchronous replication lag window).

#### 3. Diagnostic Commands
```bash
# 1. Query the reconciliation audit log table in PostgreSQL
psql -h $DB_HOST -U aegis_app -d aegis_prod -c "
  SELECT tenant_id, entity_id_hash, window_name, redis_count, db_count, difference, reconciled_at
  FROM reconciliation_drift_audit
  ORDER BY reconciled_at DESC LIMIT 10;"

# 2. Inspect replication lag on Redis replica
redis-cli -h $REDIS_HOST -a $REDIS_AUTH info replication
```

#### 4. Remediation Actions
1. **Automated Reconciliation:** The reconciliation job automatically updates the Redis sorted set to match PostgreSQL truth. Verify that the drift cleared:
   ```bash
   # Re-run reconciliation manually for the affected tenant
   curl -X POST http://localhost:8081/actuator/jobs/reconcileVelocity?tenantId=<tenant_id>
   ```
2. **Persistent Drift:** If drift continually reappears for the same account, inspect application logs for rolled-back PostgreSQL transactions that executed Redis Lua increments without idempotency tracking ([ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md)).

---

### 4.6 Runbook: `runbooks/postgres-replication.md`
**Alert:** `Postgres replication lag > 30 s` (Page)

#### 1. Symptoms
- PagerDuty alert `PostgresReplicationLagHigh`.
- Console queries reading from read replicas display outdated transaction details.

#### 2. Initial Checks
1. Check replication lag metric in bytes and seconds.
2. Check replica CPU, disk I/O (EBS burst balance), and network saturation.

#### 3. Diagnostic Commands
```bash
# 1. Check replication status on PostgreSQL primary
psql -h $DB_PRIMARY_HOST -U aegis_app -d aegis_prod -c "
  SELECT client_addr, state, sync_state, 
         pg_wal_lsn_diff(pg_current_wal_lsn(), write_lsn) AS write_lag_bytes,
         pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn) AS replay_lag_bytes
  FROM pg_stat_replication;"

# 2. Check standby conflict status on replica
psql -h $DB_REPLICA_HOST -U aegis_app -d aegis_prod -c "
  SELECT * FROM pg_stat_database_conflicts WHERE datname = 'aegis_prod';"
```

#### 4. Remediation Actions
1. **Long-Running Analytics Query Blocking WAL Replay:** Standby replicas pause replay when a long-running read query conflicts with incoming WAL cleanup. Terminate conflicting queries:
   ```sql
   SELECT pg_terminate_backend(pid) FROM pg_stat_activity 
   WHERE state != 'idle' AND now() - query_start > interval '60 seconds';
   ```
2. **Console Fallback:** If replica lag remains > 30 seconds, toggle console data source configuration to route critical reads to the primary instance temporarily.

---

## 5. Blameless Post-Mortem Template

Every P1 incident and significant P2 incident requires a published post-mortem within 5 business days.

```markdown
# Incident Post-Mortem: [INC-YEAR-XXXX] - [Brief Descriptive Title]

**Date:** YYYY-MM-DD  
**Status:** Complete / In Review  
**Incident Commander:** [Name]  
**Lead Investigator:** [Name]  
**Severity:** P1 / P2  

---

## 1. Executive Summary
Brief non-technical overview (1-2 paragraphs) describing what happened, the customer impact, and how it was resolved.

## 2. Customer & Business Impact
- **Total Duration:** [X hours, Y minutes]
- **API Availability Impact:** [99.xx% during incident]
- **Transactions Affected:** [Number of failed or degraded decisions]
- **Error Budget Consumed:** [X% of monthly budget]
- **Regulatory / Financial Implications:** [None / Detail]

## 3. Timeline (All times in IST and UTC)
- `14:02 IST (08:32 UTC)`: Fault triggered / initial symptom.
- `14:05 IST (08:35 UTC)`: PagerDuty alert fires; Incident Commander paged.
- `14:08 IST (08:38 UTC)`: Triage confirms P1 severity; status page updated.
- `14:22 IST (08:52 UTC)`: Root cause identified; mitigation command applied.
- `14:30 IST (09:00 UTC)`: Latency and error rates normalize; monitoring recovery.
- `14:45 IST (09:15 UTC)`: Incident closed; status page marked operational.

## 4. Root Cause Analysis (5 Whys)
1. *Why did decisions fail?* -> The database rejected connections.
2. *Why did it reject connections?* -> Connection pool was fully saturated.
3. *Why was it saturated?* -> Queries on `processed_events` took 1.2s instead of 2ms.
4. *Why did queries take 1.2s?* -> An autovacuum lock occurred during index rebuild.
5. *Why did autovacuum lock during peak hours?* -> Maintenance parameters were left at default rather than scheduled for off-peak.

## 5. What Went Well
- Monitoring alerted within 3 minutes of threshold breach.
- Failover procedure executed without data loss.

## 6. What Went Poorly
- Runbook lacked command to kill conflicting vacuum locks.
- Public status page update delayed by 8 minutes.

## 7. Action Items (Preventative Corrective Actions)
| Action Item | Type | Owner | Ticket Link | Target Date |
|---|---|---|---|---|
| Schedule table vacuum to off-peak hours | Preventative | DevOps Lead | JIRA-402 | 2026-11-01 |
| Update `decision-api-latency.md` runbook | Documentation | Tech Lead | JIRA-405 | 2026-10-25 |
| Add CI test simulating autovacuum lock | Testing | QA Lead | JIRA-410 | 2026-11-15 |
```

---

## 6. Backup, Point-in-Time Recovery (PITR), and Restore Drills

### 6.1 Backup Architecture
- **Continuous WAL Archiving:** PostgreSQL WAL records are streamed continuously to an Amazon S3 bucket (`s3://aegis-pg-wal-ap-south-1`) with 35-day retention.
- **Automated Daily Snapshots:** Amazon RDS creates automated daily full snapshots at 02:00 IST.
- **RPO Target:** 5 minutes (hypothesis, to validate).
- **RTO Target:** 30 minutes (hypothesis, to validate).

### 6.2 Step-by-Step Point-in-Time Recovery (PITR) Procedure
If catastrophic data corruption or human error occurs at timestamp $T_{\text{error}}$:

```bash
# 1. Identify the target recovery timestamp (e.g., 2 minutes prior to error)
TARGET_TIME="2026-10-21T14:30:00+05:30"

# 2. Restore PostgreSQL to an isolated target instance using AWS CLI
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier aegis-prod-pg \
  --target-db-instance-identifier aegis-prod-pg-restored \
  --restore-time "$TARGET_TIME" \
  --db-subnet-group-name aegis-private-db-subnet \
  --vpc-security-group-ids sg-0123456789abcdef0 \
  --no-multi-az

# 3. Monitor restore completion
aws rds wait db-instance-available --db-instance-identifier aegis-prod-pg-restored

# 4. Verify data integrity on restored database
psql -h <restored_endpoint> -U aegis_app -d aegis_prod -c "
  SELECT count(*), max(created_at) FROM decisions;"

# 5. Swap DNS / CNAME record to point application connection pools to restored instance
aws route53 change-resource-record-sets --hosted-zone-id Z123456 --change-batch file://dns-swap.json
```

### 6.3 Monthly Restore Drill Protocol
1. On the first Tuesday of every month, DevOps initiates an automated PITR restore to a sandbox instance.
2. An automated validation script asserts:
   - All table schemas match production exactly.
   - Total decision count in restored database matches production decision count at $T_{\text{restore}}$.
   - Batch sealer Merkle roots in `decision_audit_batches` verify without errors.
3. Drill duration and result are logged in compliance records.

---

## 7. Redis Rebuild-from-Postgres Procedure

When Redis experiences catastrophic multi-node failure or data corruption, it must be rebuilt from the PostgreSQL source of truth ([doc 01 §7.3](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L333-L347)).

```
┌─────────────────────────────────────────────────────────────┐
│ Redis Rebuild Job Architecture                              │
│                                                             │
│  ┌──────────────────────┐     Streams 24h Txns     ┌─────┐  │
│  │ PostgreSQL           │─────────────────────────►│ Pod │  │
│  │ (decisions table)    │                          │     │  │
│  └──────────────────────┘                          └─┬───┘  │
│                                                      │      │
│                                         Pipelined    │      │
│                                         ZADD Batch   ▼      │
│                                                    ┌─────┐  │
│                                                    │Redis│  │
│                                                    └─────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 7.1 Rebuild Execution Steps
1. **Provision Fresh Redis Instance:** Clear corrupted keys or provision new ElastiCache cluster.
2. **Launch Rebuild Batch Task:** Execute the Spring Boot rebuild batch task:
   ```bash
   kubectl run redis-rebuilder -n aegis-prod --rm -i --tty \
     --image=aegis/reconciler:v1.0.0 -- \
     java -cp app.jar com.projectaegis.jobs.RedisRebuildJob \
       --lookbackHours=24 \
       --batchSize=5000 \
       --maxTpsLimit=10000
   ```
3. **Internal Processing Logic:**
   - Reads `decisions` rows where `created_at >= NOW() - INTERVAL '24 hours'`, ordered by `event_time ASC`.
   - Groups events by `{tenant_id:account_id}`.
   - Executes pipelined Redis `ZADD` commandspopulating `{tenant_id:account_id}:vel:{window}` with `event_id` and `event_time`.
   - Sets appropriate key TTLs (`window_duration_seconds * 2`).
444: 4. **Traffic Gate:** Rebuild completes in < 15 minutes for 50 million records (hypothesis, to validate). Normal traffic resumes once rebuild finishes.

---

## 8. Operations and Disaster Recovery Across Deployment Tiers (Decision D1)

Project Aegis shares a single codebase across three distinct deployment tiers (Decision D1). Operational procedures, SLO enforcement, and disaster recovery vary by tier:

### 8.1 Tier 0: Developer Laptop (Docker Compose)
- **Deployment Footprint:** Single-node Docker Compose running core backing services: PostgreSQL 16, Redis 7, and single-broker Kafka with KRaft (Decision D6). No Schema Registry, Keycloak, or observability stack (Prometheus/Grafana) by default.
- **Resource Envelope:** Hard memory budget $\le 50\%$ of installed RAM (max 3.89 GB on an 8 GB developer machine; measured 7.79 GB total RAM via Win32 API).
- **SLOs & Alerting:** No formal SLOs, PagerDuty, or multi-burn-rate paging. Diagnostic monitoring via Spring Boot Actuator (`/actuator/health`, `/actuator/metrics`) and standard container stdout logs (`docker compose logs -f`).
- **Disaster Recovery & Reset:** Zero data durability requirement. Fast teardown and reprovisioning via:
  ```bash
  docker compose down -v && docker compose up -d
  ```
  Schema migrations execute automatically via Flyway. Test fixtures and synthetic data are re-seeded on demand via `tools/synthetic-generator` (Decision D5). Recovery time objective: < 60 seconds.

### 8.2 Tier 1: Pilot Deployment (Single India Region, Shadow Mode)
- **Deployment Footprint:** Minimal single-AZ cloud footprint in AWS Mumbai (`ap-south-1`). Single RDS PostgreSQL (`db.t4g.medium`), single ElastiCache Redis (`cache.t4g.small`), and Amazon MSK or self-hosted KRaft Kafka.
- **Operating Mode:** Shadow mode only (Decision D1). Customer payment gateways send mirrored asynchronous traffic. Outcomes are logged and compared against the customer's legacy rules, but do not sit in the synchronous payment blocking path.
- **SLOs & Alerting:** Relaxed availability target (99.5% availability, ~3.6 hours monthly error budget; hypothesis, to validate). Alerting routed to Slack `#eng-pilot-alerts` and founder email/SMS. P1 paging response SLA: $\le 15$ minutes during business hours (10:00–19:00 IST), best-effort off-hours (Decision DR-OPS-01).
- **Disaster Recovery:**
  - PostgreSQL: Nightly automated snapshots with 7-day retention and point-in-time recovery enabled (RPO $\le 1\text{ hour}$, RTO $\le 2\text{ hours}$).
  - Redis: Derived state rebuilt on demand using `com.projectaegis.jobs.RedisRebuildJob` run as a one-off ECS/EC2 task reading from PostgreSQL `decisions` table (RTO $\le 30\text{ minutes}$).
  - Synthetic test data and imported backtest data (Decision D8) are isolated per tenant in S3 and Postgres RLS.

### 8.3 Tier 2: Production Multi-AZ Enterprise Deployment
- **Deployment Footprint:** Full multi-AZ high-availability architecture across 3 Availability Zones in `ap-south-1` as detailed in sections 2–7 of this document and doc 14.
- **Operating Mode:** Synchronous, blocking inline decision path (`POST /v1/decisions` with p99 $\le 100\text{ ms}$ server-side budget).
- **SLOs & Alerting:** Strict multi-window multi-burn-rate alerting (P1 at 14.4x / 1h window, P2 at 6x / 6h window). 24/7 on-call pager rotation with $\le 5\text{ minute}$ response SLA.
- **Disaster Recovery:**
  - Multi-AZ RDS PostgreSQL with automated standby failover (< 60s) and continuous WAL archiving to S3 with 5-minute PITR (RPO $\le 5\text{ minutes}$, RTO $\le 30\text{ minutes}$).
  - 3-shard Multi-AZ ElastiCache Redis cluster with automatic failover (< 30s).
  - Monthly automated PITR restore drills and chaos game days (§6.3).

---

## 9. Decision Register References (formerly Open Questions)
 
 All operational questions for SRE and Operations have been resolved or classified per Decision **D3** in the [Decision Register](file:///c:/dev/aegis/docs/00-decisions/decision-register.md):
 
 | # | Decision ID | Question / Topic | Status | Decision / Assumed Value |
 |---|---|---|---|---|
 | 1 | [DR-OPS-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **On-Call Escalation Window:** Maximum acceptable P1 paging response time. | Decided | 15 min during business hours (10:00–19:00 IST), best-effort off-hours for Tier 1 pilot; 5 min 24/7 for Tier 2 production. |
 | 2 | [DR-OPS-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Status Page Hosting:** Multi-region status page outside AWS `ap-south-1`. | Decided | Hosted on Instatus with DNS routed outside AWS `ap-south-1` via Cloudflare. |
 | 3 | [DR-OPS-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Cold Rebuild Latency Budget:** Decision API operation during Redis rebuild. | Decided | Fail-open / fail-closed per tenant configuration during 15-min Redis rebuild from PostgreSQL. |
 | 4 | [DR-OPS-04](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Automated DLQ Reprocessing:** Handling transient vs permanent poison pills. | Decided | Exponential backoff retry (up to 3 attempts) for transient I/O; permanent schema failures require manual triage. |

