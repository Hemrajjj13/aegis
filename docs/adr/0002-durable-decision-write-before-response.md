# ADR-0002: Durable Decision Write Before Response

**Status:** Accepted  
**Date:** October 2026  
**Deciders:** Founder / Engineering Lead  
**Supersedes:** —  
**Superseded by:** —

---

## Context

When the `decision-api` has evaluated a transaction and produced a decision,
it must do two things before it can return a response to the caller:

1. Durably record the decision (for audit, for idempotency, for replay).
2. Notify downstream consumers (case queue, webhooks, analytics) that a
   decision was made.

These two concerns can be ordered in different ways, each with a different
failure window:

**Option A — Write to Postgres, then respond, then publish to Kafka.**
If the process crashes after the Postgres write but before the Kafka publish,
the decision exists in the database but no downstream consumer knows about it.

**Option B — Write to Postgres and an outbox row in the same transaction,
respond, then publish from the outbox asynchronously.**
If the process crashes after committing the transaction but before the
outbox relay runs, the outbox row is present and will be picked up when
the relay restarts. The downstream event is eventually delivered with no
data loss.

**Option C — Acknowledge a Kafka write, respond, then persist to Postgres
asynchronously.**
The caller gets a fast response. But if the Postgres write fails after the
caller has already received "approved", the decision is lost from the
system of record. The audit trail has a gap. Reconciliation is required
to recover. This violates the audit guarantee.

**Option D — Respond first, then write asynchronously.**
Callers may retry with the same idempotency key and receive inconsistent
answers if the write has not yet landed. The audit trail is incomplete
during the window between response and write. Unacceptable.

Aegis's audit trail is a first-class correctness requirement: **every
decision that was returned to a caller must be in the audit log.** A
regulator or customer may ask for the decision on any specific transaction.
"We think we made that decision but it may not have been persisted" is
not an acceptable answer.

The target p99 latency is 100 ms. The question is whether a synchronous
Postgres write fits in that budget.

### Latency analysis

A Postgres write on a single-region managed cluster (same AZ) typically
completes in 2–5 ms for a single-row insert with a prepared statement.
The write path goes through a connection pool (PgBouncer or JDBC pool),
inserts one row in `decisions` and one row in `processed_events` and one
row in `outbox` in the same transaction. Estimated: 5–15 ms including
connection acquisition.

This fits within the latency budget in §3.1 of the production-readiness
discovery. The concern is a latency spike when Postgres is under load.
That spike must be measured in the load test.

### Velocity-counter failure mode

Redis replication is asynchronous. In a Redis primary failure and failover,
the last few writes to the velocity counter can be lost. This means the
velocity counter could be under-counted by a small number of increments
immediately after failover. The reconciliation job corrects this within
5 minutes.

The alternative — waiting for Redis replica acknowledgment before continuing —
adds 2–5 ms to the critical path. This may or may not be acceptable
depending on load-test results.

## Decision

**Use the transactional outbox pattern.**

For every decision:

1. Within a single Postgres transaction, insert:
   - one row in `decisions`
   - one row in `processed_events` (unique constraint on `(tenant_id, event_id)`)
   - one row in `outbox`
2. Commit the transaction.
3. Return HTTP 200 with the decision to the caller.
4. A separate outbox relay process reads committed outbox rows and publishes
   them to the `decisions` Kafka topic. On successful publish, the outbox
   row is marked as delivered.

This provides:
- **Audit completeness:** every returned decision is in Postgres before the
  caller receives it.
- **Exactly-once decision:** the unique constraint on `(tenant_id, event_id)`
  means a duplicate delivery of the same event produces a unique constraint
  violation, not a second decision row.
- **At-least-once downstream delivery:** the outbox relay retries until the
  Kafka publish succeeds. Downstream consumers must be idempotent (they receive
  the decision's `decision_id` and can deduplicate).
- **Failure resilience:** if the `decision-api` process crashes after committing
  the transaction but before the outbox relay runs, the relay picks up the
  undelivered outbox row on restart.

### Redis write trade-off

The velocity counter in Redis is incremented **before** the Postgres write
(as part of the atomic Lua script). If the Postgres transaction rolls back
(e.g., unique constraint violation on a duplicate event), the Redis sorted
set retains the `event_id` member that was added. This is intentional: the
`event_id` membership check in the Lua script (see ADR-0005) means that if
the same event is retried, the script detects it as a duplicate and returns
`pass` without a second increment. The net result is correct: the counter
stays at the level set by the first (rolled-back) execution.

A residual over-count can still occur if the Redis primary fails between the
Lua write and the Postgres commit, and the replica that promotes has not yet
received the Lua write. In that failure window, the event_id is not in the
replica's sorted set, so a retry would increment the counter again. This
error is bounded by the Redis async-replication lag (typically < 1 s), affects
only the window between the Lua write and the Postgres commit, and is
corrected by the reconciliation job within 5 minutes. It is a documented
bounded error.

### Replica lag on the durable write

The Postgres write is made to the primary. Read replicas used by the console
may have replication lag (typically < 1 s). The console uses a read-your-writes
pattern: immediately after a decision is made, the caller can query it back
from the primary endpoint (or via a short-lived consistency token). The
console's transaction detail screen reads from the primary for the first 5
seconds after a decision is made.

## Consequences

### Positive
- The audit trail is always complete for decisions returned to callers.
- Idempotency is enforced at the database layer, not just in application memory.
- Downstream event delivery is reliable without requiring a two-phase commit
  across Postgres and Kafka.
- The outbox relay is a well-understood pattern with battle-tested implementations
  (Debezium CDC or a polling relay).

### Negative / Trade-offs
- The synchronous Postgres write adds latency to the critical path (estimated
  5–15 ms). This must be validated in the load test. If p99 > 100 ms under
  load, the following mitigations are available before abandoning this decision:
  - Batch multiple outbox rows into one transaction (not applicable to sync path,
    but useful for the async consumer)
  - Reduce connection-pool contention by right-sizing the pool
  - Use Postgres `UNLOGGED` tables for the outbox (reduces durability guarantee
    on the outbox rows specifically; decisions table remains logged)
- The outbox relay is an additional component to operate and monitor.
- If the outbox relay falls behind, downstream consumers receive decisions late.
  This is acceptable for async consumers but requires an alert on outbox depth.

### Neutral
- The outbox relay can be replaced with Debezium CDC (reads the Postgres WAL
  directly) without changing the decision-writing logic. This is an operational
  upgrade path if the polling relay becomes a bottleneck.

## Alternatives considered

| Alternative | Why not chosen |
|-------------|----------------|
| Acknowledge Kafka write, respond, then persist to Postgres | Fastest for the caller but violates the audit completeness requirement; unrecoverable if Kafka is available but Postgres is not |
| Write to Postgres asynchronously after responding | Creates a window where the caller has "approved" but the system of record disagrees; idempotency key responses would be inconsistent during that window |
| Two-phase commit across Postgres and Kafka | Kafka does not support XA transactions; distributed 2PC is complex, slow, and fragile |
| Respond from Redis only, batch-persist to Postgres | Bounded by Redis durability (async replication); audit completeness cannot be guaranteed |

## References

- [docs/01-production-readiness-discovery.md §9](../01-production-readiness-discovery.md) — audit trail requirements
- [docs/05-architecture/overview.md §6.1](../05-architecture/overview.md) — synchronous decision data flow
- [docs/adr/0005-velocity-script-event-id-idempotency.md](./0005-velocity-script-event-id-idempotency.md) — velocity counter idempotency (updates the over-count analysis above)
- Chris Richardson, "Transactional Outbox Pattern"
- Debezium documentation
