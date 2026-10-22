# ADR-0001: Modular Monolith First

**Status:** Accepted  
**Date:** October 2026  
**Deciders:** Founder / Engineering Lead  
**Supersedes:** —  
**Superseded by:** —

---

## Context

Aegis must serve multiple tenants, handle synchronous and asynchronous
transaction paths, run a batch replay engine, expose a management console,
and maintain a separate identity service. The naive decomposition of these
concerns yields more than ten candidate microservices.

The team in Stage 0–1 is one or two people building with AI coding agents.
Network boundaries between microservices impose costs in proportion to team
size: distributed tracing must span services, integration tests require
orchestration (Testcontainers or a running cluster), schema evolution
requires coordination, and every cross-service call adds a failure mode
to reason about.

The system also has a hard correctness requirement: the sync API, the async
Kafka consumer, and the replay engine must execute identical evaluation
logic. If these three paths live in separate services, keeping them in sync
is a coordination and deployment problem. If they share a library, that
library must be versioned and deployed consistently across three services.

Several production systems with similar problem shapes (Shopify, Basecamp,
Stack Overflow at significant scale) have operated successfully as modular
monoliths. The modular monolith is a default, not a permanent architecture.

## Decision

Deploy Aegis as **six separately deployable JVM processes** (plus one static
front-end build):

| Deployable | Responsibility |
|-----------|----------------|
| `decision-api` | Synchronous REST decision endpoint; authentication; idempotency check |
| `stream-processor` | Kafka consumer for the async ingest path |
| `rules-service` | Rule DSL parsing, versioning, maker-checker workflow |
| `replay-engine` | Batch worker; deterministic replay runs |
| `console-api` | Backend-for-frontend; serves the React console |
| `identity-service` | Authentication, tokens, MFA, invite flows |
| `console` | React + TypeScript static build; served via CDN |

The **evaluation core** is a Java library (`evaluation-core`) compiled into
`decision-api`, `stream-processor`, and `replay-engine`. It is not a
separate network service. All three call the same compiled code; no network
hop separates them.

Within each deployable, code is organised into modules with enforced
dependency rules (ArchUnit tests prevent circular dependencies and prevent
module A from directly importing module B's internals).

A service is extracted into a separately deployable unit only when at least
one of the following is true:
- The service needs to scale independently and is a measured bottleneck
- The team reaches a size where the shared codebase slows independent delivery
- A compliance requirement mandates separate deployment or network isolation

## Consequences

### Positive
- One repository, one CI pipeline, one deployment per environment in Stage 1
- The evaluation core is never out of sync between sync, async, and replay paths
- Integration tests use Testcontainers once, not per service
- Debugging a decision touches one codebase, one log stream, one trace per process
- Changing the evaluation core requires one deployment, not three coordinated deployments
- No distributed transaction coordination between services (Postgres handles atomicity)

### Negative / Trade-offs
- All six deployables share the same release train in Stage 1. A bug in the
  console-api or rules-service triggers a full redeploy. Mitigated by fast CI
  and blue/green deployments.
- If the evaluation core must be deployed at a different frequency (e.g.,
  an urgent rule-engine fix), only the affected deployable needs to be
  rebuilt — but the library version bump touches three build units.
- Horizontal scaling applies per deployable, not per module. This is sufficient
  for Stage 1 throughput targets.

### Neutral
- The six deployables still require separate health checks, metrics namespaces,
  and log streams. Observability is per-process, not per-monolith.
- If a future team wants to extract a service, the module boundaries in the
  monolith become the starting point for the split.

## Alternatives considered

| Alternative | Why not chosen |
|-------------|----------------|
| Full microservices from day one (10+ services) | Disproportionate coordination overhead for a 1–2 person team; makes evaluation-core sharing a versioning and deployment problem; integration testing complexity is quadratic in services |
| Single deployable (true monolith, all six concerns in one process) | Prevents independent scaling of `decision-api` vs `replay-engine`; replay runs would compete with live traffic on the same process |
| Serverless / function-per-endpoint | Cold starts are incompatible with a p99 < 100 ms latency target; stateful velocity counters and compiled rule caches are awkward to manage per function invocation |
| Event sourcing / CQRS for the core domain | Adds significant complexity (event store, projection rebuilds) before the domain model is understood; can be layered in later if needed |

## References

- [docs/05-architecture/overview.md](../05-architecture/overview.md) — container view
- Martin Fowler, "MonolithFirst" pattern
- "Modular Monolith" — Sam Newman
