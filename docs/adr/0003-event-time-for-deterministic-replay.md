# ADR-0003: Event Time for Deterministic Replay

**Status:** Accepted  
**Date:** October 2026  
**Deciders:** Founder / Engineering Lead  
**Supersedes:** —  
**Superseded by:** —

---

## Context

The replay engine is Aegis's core differentiator. It allows a risk team to
run two rule versions over the same historical dataset and see exactly which
decisions changed, with precision, recall, and latency metrics per version.
The promise to the customer is: "run this on your history and see the impact
before you ship."

That promise depends on **determinism**: running the same dataset through
the same ruleset twice must produce identical decisions. Non-determinism in
the replay engine would make its output meaningless.

There are two sources of time that a rule engine can use:

**Wall-clock time (system clock at evaluation time):**
- "Count card_id transactions in the last 24 hours" reads `NOW()` at evaluation time.
- If the replay runs on 1 October and the event occurred on 1 September,
  `NOW() - 24h` is anchored to 1 October, not 1 September.
- The window includes no transactions (they are all 30 days ago).
- Re-running the replay on 2 October gives a different result.
- The replay result does not correspond to what the rule would have done
  in production on 1 September.

**Event time (the timestamp on the event itself):**
- "Count card_id transactions in the last 24 hours" reads the event's
  own timestamp and looks back 24 hours from that timestamp.
- If the event occurred on 1 September at 14:00, the window is
  1 September 13:00 to 1 September 14:00.
- Running the replay on 1 October or 2 October produces the same result,
  because the window is always anchored to the event, not to when the
  replay runs.
- The result faithfully represents what would have happened in production.

Event time is the only option that satisfies determinism.

### What event time requires

1. **Every event must carry a trusted timestamp.** This timestamp is set by
   the originating system (the caller), not by the time Aegis receives it.
   The field name is `event_time`. Aegis also records `received_time` (when
   the event arrived) for observability, but `received_time` is never used
   in rule evaluation.

2. **Events in a replay must be ordered by `(event_time, sequence_number)`.**
   `sequence_number` is a monotonic counter assigned by the originating
   system per tenant and per entity (e.g., per card or per account). It
   breaks ties when two events have the same `event_time`. Without a
   sequence tie-breaker, the ordering is non-deterministic for simultaneous
   events, which means velocity counters are non-deterministic.

3. **The evaluation core must use a clock that is provided to it, not
   `System.currentTimeMillis()`.** In production, the provided clock is
   `event_time`. In replay, the provided clock is also `event_time` from
   the historical record. This is a `Clock` parameter injected into the
   evaluation core. No `Clock.systemUTC()` call appears inside the
   evaluation logic itself.

4. **Feature state for replay uses in-memory windowed state, not Redis.**
   The replay engine instantiates an `InMemoryVelocityWindow` (see ADR-0005)
   per `(tenant_id, entity_id, window_name)`. This eliminates Redis
   as a dependency for replay and avoids isolating per-run namespaces in Redis.
   The in-memory implementation is semantically identical to the production
   Lua script; equivalence is verified by `VelocityImplementationEquivalenceTest`.

5. **Replay results are written only to replay tables.** `replay_decisions`
   and `replay_events` are keyed by `(run_id, transaction_id)`. The live
   `decisions` table is never written by the replay engine. This isolation
   is enforced by the data model (the replay engine's database role does not
   have INSERT permissions on the live `decisions` table).

### Late-arriving events and gaps

In production, events may arrive out of order (for example, a mobile payment
recorded offline arrives late). The production stream-processor applies a
configurable out-of-order tolerance window (default 30 seconds). Events
outside this window are flagged as late and written to the DLQ.

In replay, all historical events are sorted before processing begins. There
are no late arrivals in replay. The historical events represent the complete
record.

### Clock skew in production

In production, a caller's system clock may be wrong. Aegis validates that
`event_time` is within a configurable window of `received_time` (default:
event_time must not be more than 5 minutes in the past or 1 minute in the
future). Events outside this window are rejected with HTTP 400.

This validation prevents a caller from backfilling old events into the
production decision stream (which would manipulate velocity counters in the
live system). Such backfills must go through the replay path, not the
production decision API.

### Sequence number assignment

Decision [DR-API-04](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): Sequence numbers are optional in ingest payloads (defaults to 0); synthetic ordering tie-breakers use `(event_time, received_at)`. See [doc 06 §2](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md).

## Decision

**The evaluation core uses event time, not wall-clock time, for all
feature computation and rule evaluation.**

Concretely:
- The evaluation core accepts a `Clock` parameter. In production this is
  set to the event's `event_time`. In test and replay it is set identically.
- Velocity windows are computed as `event_time - window_size` to `event_time`.
- No call to `System.currentTimeMillis()`, `LocalDateTime.now()`, or
  `Instant.now()` appears in the evaluation logic or the rule DSL interpreter.
- Events in a replay run are sorted by `(event_time, sequence_number)` before
  processing begins.
- Replay feature state is isolated per `run_id`.
- The replay engine has no INSERT permission on the live `decisions` table.

A CI test (`EvaluationCoreWallClockFreeTest`) enforces that no class in
the `evaluation-core` module calls any of the following:
- `Clock.systemUTC()` or `Clock.systemDefaultZone()`
- `Instant.now()` (the zero-argument overload; `Instant.now(clock)` is allowed)
- `LocalDateTime.now()` (zero-argument overload)
- `System.currentTimeMillis()`
- `System.nanoTime()` (unless annotated `@LatencyMeasurement` and not used in evaluation logic)

All clock access inside evaluation logic goes through the injected `Clock`
parameter.

## Consequences

### Positive
- Replay is deterministic: the same dataset through the same ruleset always
  produces identical decisions, on any date, on any machine.
- Replay results faithfully represent what the rule would have done in
  production at event time.
- Running the same replay twice to validate determinism is a meaningful test.
- The evaluation core can be unit-tested with a controllable clock (no
  `Thread.sleep`, no `@Spy` on system time).
- The CI gate for "replay is deterministic" is a simple diff of two replay runs.

### Negative / Trade-offs
- The originating system must provide a reliable `event_time` and `sequence_number`.
  If the originating clock is wrong, the rule evaluation is wrong in the
  same way in both production and replay (at least consistently).
- Events with the same `event_time` and no sequence number are non-deterministic
  under concurrent ingest. The sequence number requirement must be documented
  in the integration guide.
- Out-of-order event handling in production is limited by the tolerance window.
  A caller that frequently delivers events more than 30 seconds late will have
  those events dropped to DLQ. This must be communicated in the integration guide.

### Neutral
- `received_time` (the time Aegis received the event) is still stored on
  every decision for observability (monitoring latency between originating
  system and Aegis). It is never used for rule evaluation.
- The clock-injection pattern is idiomatic in Java (see `java.time.Clock`).
  No custom infrastructure is required.

## Live vs replay divergence

Replay reproduces rule behaviour assuming events arrive in `(event_time,
sequence_number)` order. In production on the sync path, two near-simultaneous
events for the same account may arrive in a different order from their
`event_time` ordering (because concurrent HTTP threads execute in arrival order,
not event-time order). This means a replay can produce a different velocity
count than production did for a burst of concurrent events, even with the
correct event_time values.

This divergence is expected and documented, not a bug. It is bounded by the
concurrency window (events that arrive within one connection-pool dispatch
cycle, typically < 10 ms apart).

A **live-vs-replay divergence metric** is tracked per replay run:
- `replay_divergence_count`: number of transactions where the replay
  decision differs from the stored live decision.
- `replay_divergence_pct`: `replay_divergence_count / total_replayed`.

This metric appears in the replay comparison report. A divergence of < 0.1%
is considered within the expected concurrency-ordering bound (hypothesis —
validate against a design partner’s data). A divergence significantly above
0.1% indicates either an event-time data quality problem (events with wrong
timestamps) or a rule-evaluation non-determinism that must be investigated.

Decision [DR-OPS-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) / SOW baseline: Maximum acceptable replay divergence threshold is set to $< 0.1\%$ for pilot validation (Assumption [ASM-INT-10](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md)).

## Alternatives considered

| Alternative | Why not chosen |
|-------------|----------------|
| Wall-clock time for production, event time for replay | Would produce different results in production vs replay for any run not executed in real time. Defeats the purpose of replay. |
| Wall-clock time always | Replay is not deterministic. The core product differentiator does not exist. |
| Event sourcing with a projection rebuild | Rebuilding the full event stream for each replay is equivalent in principle, but more complex to implement. Event time ordering is still required inside the projection. The current approach is simpler and achieves the same result. |
| Approximate velocity counters (probabilistic, e.g., HyperLogLog) | Non-deterministic by design; incompatible with the exactness requirement of the concurrency test (CI gate: exactly L of N pass). |

## References

- [docs/05-architecture/overview.md §6.5](../05-architecture/overview.md) — replay run data flow
- [docs/01-production-readiness-discovery.md §3.5](../01-production-readiness-discovery.md) — replay determinism SLO
- Martin Kleppmann, "Designing Data-Intensive Applications", chapter on stream processing and event time
- Apache Flink documentation on event time and watermarks (reference for the pattern, not a dependency)
