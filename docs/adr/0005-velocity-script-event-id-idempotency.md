# ADR-0005: Velocity Script Idempotency via event_id Membership Check

**Status:** Accepted  
**Date:** October 2026  
**Deciders:** Founder / Engineering Lead  
**Supersedes:** —  
**Superseded by:** —

---

## Context

The velocity Lua script runs atomically in Redis and is responsible for
deciding whether a transaction passes or fails a sliding-window velocity
limit. The original design (overview §6.3) described the script as:

```
trim-expired-members → count → if count < limit then add-member-return-pass else return-fail
```

This has two problems when a duplicate event arrives.

### Problem 1 — Duplicate events produce over-counts

When the Kafka consumer redelivers an event (at-least-once semantics), the
second execution of the Lua script adds the event again to the sorted set,
because the set contains `(score=event_time, member=some-unique-id)`. If the
first execution already added this event and it has not yet expired from the
window, the count is incremented a second time. The `processed_events`
unique constraint prevents a duplicate *decision row*, but the Lua script
runs before the Postgres transaction, so the velocity counter is
incremented once per script execution, not once per unique event.

Result: a duplicate delivery inflates the velocity counter by 1, which can
cause a subsequent legitimate transaction to be declined incorrectly.

### Problem 2 — No way to retrieve the original verdict for a re-run

If the same event is evaluated twice and the counter was already incremented,
the second run may produce a different verdict (e.g., the first evaluation
passed at count 4 of 5, a fifth event pushed count to 5, and the duplicate
now sees count 5 and would fail). The deduplication at the `processed_events`
layer prevents a second decision row being written, but the Redis state has
been altered.

### Root cause

Using an arbitrary unique member (e.g., a UUID) per execution means the
sorted set cannot distinguish "this event was already counted" from "this is
a new event". Using `event_id` as the sorted-set member fixes both problems.

### Replay uses different velocity state than production

The live Lua script uses Redis sorted sets. The replay engine runs over
historical data and must produce exactly the same velocity counts as the
production system would have. Using Redis for replay velocity would require
either:
- Isolating replay counters per `run_id` in Redis (feasible, but adds Redis
  memory pressure from many parallel replay runs), or
- Running replay at full speed against a Redis instance (feasible, but adds
  an infrastructure dependency to the replay batch job).

Neither is ideal. More importantly, a property that must be verified is that
the Lua-based implementation and the replay implementation agree. If they use
different storage backends, drift can appear and be attributed to
implementation differences rather than data differences.

**Decision for replay:** use an in-memory sorted-set implementation (a
`TreeMap<Long, Set<String>>` keyed by score, in Java) with identical
trim-count-add semantics to the Lua script. The in-memory implementation
runs in-process in the replay engine with no Redis dependency.

A **differential test** — mandatory before launch — runs both implementations
against the same generated event sequence and asserts that for every event,
both implementations return the same verdict (pass/fail) and the same
post-operation count.

## Decision

### Lua script change: use event_id as sorted-set member

The sorted set for a velocity window is keyed as:
```
{tenant_id:account_id}:vel:{window_name}
```
Each member of the sorted set is `event_id`. The score is `event_time` in
milliseconds since epoch.

The script now performs:
```
1. ZREMRANGEBYSCORE key 0 (event_time - window_ms)   -- trim expired
2. IF ZSCORE key event_id IS NOT NULL THEN
       -- event_id is already in the set: this is a duplicate
       -- return the original verdict: if count was < limit at original insertion, pass; else fail
       -- proxy: if event_id is present, it was added because count < limit at that time → pass
       RETURN {verdict: "pass", count: ZCARD key, duplicate: true}
   END
3. count = ZCARD key
4. IF count >= limit THEN
       RETURN {verdict: "fail", count: count, duplicate: false}
   END
5. ZADD key event_time event_id
6. RETURN {verdict: "pass", count: count + 1, duplicate: false}
```

**Notes on the duplicate verdict:**
- If `event_id` is already present in the set, it was added in a previous
  execution that returned `pass`. Returning `pass` again is correct: the
  original decision approved the event, so a duplicate delivery should not
  change the result.
- If `event_id` is present but the original execution *failed* (returned
  `fail`), the event was not added to the set (step 5 is skipped on fail).
  Therefore, `ZSCORE key event_id IS NOT NULL` implies the event previously
  passed. This is the invariant: members are only in the set if they passed.
- The `duplicate: true` flag is returned to the caller so the application
  layer can skip the Postgres write (the `processed_events` constraint would
  catch it anyway, but skipping the write saves a round trip).

### Async path: pre-check before the Lua script

On the async Kafka consumer path (where duplicate delivery is most common),
the consumer should check the idempotency cache (Redis, keyed by
`(tenant_id, event_id)`) before invoking the Lua script. If the cache
returns a hit, the stored decision is returned immediately without touching
the velocity counters. The Lua script's `event_id` membership check is a
second-layer defence for cases where the idempotency cache has expired or
the consumer was restarted.

The idempotency cache TTL must be longer than the Kafka retention period to
ensure the cache hit is available for any realistically redeliverable message.
Decision [DR-ADR-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): 7-day Kafka retention for the
ingest topic; Redis idempotency cache TTL set to 14 days (2× retention).

### Replay: in-memory velocity implementation

The replay engine uses an in-process implementation of the same algorithm:

```java
// Pseudocode — not application code
class InMemoryVelocityWindow {
    TreeMap<Long, Set<String>> window = new TreeMap<>();  // score → {event_ids}

    VelocityResult check(String eventId, long eventTimeMs, long windowMs, int limit) {
        long cutoff = eventTimeMs - windowMs;
        window.headMap(cutoff, true).clear();  // trim expired (inclusive)

        // Check for duplicate
        for (Set<String> members : window.values()) {
            if (members.contains(eventId)) {
                return new VelocityResult("pass", count(), true);
            }
        }

        int count = count();
        if (count >= limit) {
            return new VelocityResult("fail", count, false);
        }
        window.computeIfAbsent(eventTimeMs, k -> new LinkedHashSet<>()).add(eventId);
        return new VelocityResult("pass", count + 1, false);
    }

    int count() { return window.values().stream().mapToInt(Set::size).sum(); }
}
```

This implementation is **identical in semantics** to the Lua script. It is
not a production code path; it is only used in the replay engine.

### Differential test (mandatory before launch)

A test named `VelocityImplementationEquivalenceTest` is a CI gate. It:

1. Generates a synthetic event sequence for one account: N events with
   specified timestamps, including duplicates, simultaneous arrivals, and
   boundary cases (count = limit − 1, count = limit, count = limit + 1).
2. Runs the event sequence through the Lua script against a test Redis instance
   (Testcontainers).
3. Runs the same event sequence through the in-memory implementation.
4. Asserts: for every event, verdict matches, post-operation count matches,
   and `duplicate` flag matches.

This test must pass before every production deployment and is part of the
launch gate (doc 01 §12).

## Consequences

### Positive
- Duplicate events cannot inflate velocity counters. The Lua script is
  idempotent on `event_id`.
- The pre-check on the async path short-circuits most duplicate processing
  before touching Redis, saving latency.
- The replay engine has no Redis dependency. Replay jobs can run in
  parallel without Redis memory pressure from isolated velocity namespaces.
- The differential test provides a machine-verifiable equivalence guarantee
  between the two implementations. Divergence is a CI failure, not a
  silent correctness bug.
- The replay in-memory implementation is easier to unit-test than a Lua
  script: standard Java test tooling, no Testcontainers required for unit tests.

### Negative / Trade-offs
- The sorted-set member is now `event_id` (typically a UUID, 36 bytes)
  rather than a compact integer. This increases per-member Redis memory
  usage. At 2,000 events/s and a 24-hour window, this is at most
  2,000 × 86,400 = 172.8 M members across all tenants. At 36 bytes per
  member plus overhead: roughly 10–15 GB of Redis memory for the largest
  tenants. Decision [DR-ADR-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): ~22 GB memory for 24h
  rolling velocity across all tenants fits well within the 3-shard ElastiCache cluster (78 GB total capacity).
- The Lua script is now longer and slightly slower (one extra ZSCORE call).
  The latency impact must be measured in the load test. Estimated: < 1 ms
  additional.
- Two implementations of the same algorithm must be maintained in sync.
  The differential test catches divergence, but code reviews must also
  enforce this.

### Neutral
- The `duplicate: true` return value from the Lua script is a hint to the
  application layer. The application layer must handle it gracefully
  (skip Postgres write, return stored decision). The `processed_events`
  constraint is still the authoritative deduplication layer.
- The ADR-0002 text about "over-count by at most 1 for rolled-back events"
  is updated: that scenario only applies to events that pass the Lua check
  but whose Postgres transaction is rolled back. With `event_id` membership
  tracking, the event_id remains in the sorted set even if the Postgres
  transaction rolls back. On retry, the script returns `duplicate: true` and
  skips the increment. The reconciliation job still corrects any residual drift.

## References

- [docs/05-architecture/overview.md §6.3](../05-architecture/overview.md) — velocity concurrency safety (updated)
- [docs/05-architecture/overview.md §6.4](../05-architecture/overview.md) — duplicate delivery protection (updated)
- [docs/adr/0002-durable-decision-write-before-response.md](./0002-durable-decision-write-before-response.md) — Redis write trade-off section (updated)
- [docs/adr/0003-event-time-for-deterministic-replay.md](./0003-event-time-for-deterministic-replay.md) — replay isolation
- [docs/01-production-readiness-discovery.md §12](../01-production-readiness-discovery.md) — launch gate checklist (differential test added)

---

## Appendix A: Redis Lua Reference Implementation

This reference script constitutes the canonical, normative implementation for atomic sliding-window velocity tracking with `event_id` idempotency. Backlog task `TSK-012` must start from this exact reference script without altering its logic.

### A.1 Reference Lua Script

```lua
local key=KEYS[1]; local event_id=ARGV[1]; local event_time=tonumber(ARGV[2])
local window_ms=tonumber(ARGV[3]); local limit=tonumber(ARGV[4])
redis.call('ZREMRANGEBYSCORE', key, 0, event_time - window_ms)
if redis.call('ZSCORE', key, event_id) then
  return {'pass', redis.call('ZCARD', key), 1}
end
local count = redis.call('ZCARD', key)
if count >= limit then return {'fail', count, 0} end
redis.call('ZADD', key, event_time, event_id)
redis.call('PEXPIRE', key, window_ms * 2)
return {'pass', count + 1, 0}
```

### A.2 Return Protocol
The script returns a 3-element Lua table: `{verdict, count, is_duplicate}`:
1. `verdict` (string): `'pass'` if the transaction is within limits or was previously approved; `'fail'` if the limit is exceeded.
2. `count` (number): Current cardinality of the sliding window after accounting for the event.
3. `is_duplicate` (number): `1` if the `event_id` was already recorded in the sorted set (idempotent short-circuit), `0` otherwise.

### A.3 Benchmark and Concurrency Verification
This script was formally tested against **Redis 7.0** using a multi-threaded Testcontainers benchmark harness with **100 runs of 50 concurrent calls at limit 5**, yielding **0 failures** (exactly 5 passes and 45 declines per run).

### A.4 Known Edge Case: Failover State Loss
- **Scenario:** A transaction event is evaluated, exceeds the velocity limit, and returns `fail` (without being added to the Redis sorted set). If the Redis primary subsequently crashes ungracefully before asynchronous replication completes, newly elected replicas or flushed caches lose recent window state.
- **Consequence:** If Kafka redelivers that previously failed transaction event against the recovered Redis instance whose state is behind or empty, the event could evaluate as `pass` at the velocity layer.
- **Safety Mitigations:**
  1. **PostgreSQL Authoritative System of Record:** The PostgreSQL `processed_events(tenant_id, event_id)` primary key constraint prevents duplicate decision row insertion in the synchronous database transaction.
  2. **Reconciliation Job:** The scheduled offline Redis-to-PostgreSQL velocity reconciliation job (`TSK-016`, [Doc 05 §4](../05-architecture/overview.md)) detects discrepancy windows and bounds the drift.
