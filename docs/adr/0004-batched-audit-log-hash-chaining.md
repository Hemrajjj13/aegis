# ADR-0004: Batched Audit-Log Hash-Chaining via a Background Sealer

**Status:** Accepted  
**Date:** October 2026  
**Deciders:** Founder / Engineering Lead  
**Supersedes:** —  
**Superseded by:** —

---

## Context

Every decision, rule change, login, permission change, and data export must
be recorded in a tamper-evident, hash-chained audit log (see doc 01 §9.2).
Hash-chaining means each record includes the hash of the previous record,
so any retroactive modification of a historical record breaks the chain and
is detectable on verification.

The question is where in the write path the hash-chaining happens.

### Option A — Chain synchronously, one record at a time, in the decision write transaction

Each decision write would:
1. Read the current chain head for the tenant.
2. Compute SHA-256(`previous_hash` ∥ `current_record`).
3. Write the new hash as the chain head.
4. Write the decision row with the new hash embedded.

All in one Postgres transaction.

**Problem:** Steps 1–3 require a serialised, per-tenant lock on the chain
head. At 2,000 decisions/s with N tenants each producing M decisions/s,
the chain-head row becomes a hot row. Two concurrent decisions for the same
tenant cannot both proceed until the first one commits and updates the head.
This serialises throughput per tenant to the commit rate on a single Postgres
row. At high throughput for a single tenant (e.g., a payment gateway),
this would be the primary latency bottleneck.

### Option B — Chain synchronously for management events; batch for decisions

Management events (rule changes, logins, permission changes, API key
operations, data exports) are infrequent and their immediate tamper-evidence
is a higher-priority compliance concern. These can be chained synchronously
without a throughput problem.

Decision records are written without an individual hash in the main
transaction (ADR-0002 remains unchanged). A **background sealer** job
reads committed-but-unsealed decision rows in batches, computes a Merkle-root
over the batch, chains that root to the previous batch root, and writes the
sealed batch metadata. The sealer runs on a configurable schedule (default:
every 30 seconds, configurable per tenant).

This approach is functionally equivalent for audit purposes: tampering with
any decision record within a sealed batch breaks the Merkle root verification
and the chain. The only window where a record is not yet chained is the
sealer interval (≤ 30 seconds).

### Option C — Append-only Postgres with WAL-based verification

Rely on Postgres WAL archiving and immutable S3 backups as the tamper-evidence
mechanism, without an explicit hash chain. This is operationally simpler but
does not produce a portable chain that can be verified by the customer's
auditors without full access to the WAL archives. Rejected: the chain must
be auditor-accessible.

### Option D — Event-streaming hash chain (e.g., Kafka with record hashes)

Hash each decision event in the Kafka stream. The Kafka partition offset is
monotonic and Kafka produces are idempotent. Tampering requires modifying the
broker storage, which can be detected by offset continuity checks.

Rejected for Stage 1: adds a dependency on Kafka topic compaction and offset
verification tooling. The Postgres-based sealer is simpler and keeps all
audit evidence in one system of record.

## Decision

**Implement two chaining modes:**

### Management event chain (synchronous)

For the following event types, the hash is computed and chained synchronously
within the write transaction:
- Rule change (draft, submitted, approved, published, reverted)
- Login / logout
- Permission change
- API key create / revoke
- Data export

Each management event row has a `chain_hash` column. The write path:
1. SELECT FOR UPDATE the `audit_chain_heads` row for this tenant and
   management-event stream.
2. Compute SHA-256(`previous_hash` ∥ canonical-JSON(`event`)).
3. INSERT the event row with `chain_hash`.
4. UPDATE `audit_chain_heads` with the new hash.
All four steps in one transaction.

Management events are infrequent enough (order of tens per day, not
thousands per second) that the SELECT FOR UPDATE does not produce
measurable contention.

### Decision batch sealer (background)

Decision records are written in the main transaction without a hash
(ADR-0002 unchanged). A separate background sealer job:

1. Runs every `SEAL_INTERVAL` seconds (default 30, configurable per tenant,
   minimum 5).
2. Selects all decision rows for the tenant that have `sealed = false`,
   ordered by `(created_at, decision_id)`, up to a batch limit.
3. Computes a **Merkle root** over the batch: each leaf is SHA-256 of the
   canonical JSON of one decision row. Parent nodes combine two children:
   SHA-256(`left` ∥ `right`).
4. Chains the Merkle root to the previous batch root:
   `batch_chain_hash = SHA-256(previous_batch_chain_hash ∥ merkle_root)`.
5. Writes a row to `decision_audit_batches`:
   `(tenant_id, batch_id, first_decision_id, last_decision_id, batch_size, merkle_root, batch_chain_hash, sealed_at)`.
6. Marks the decision rows as `sealed = true` and records their `batch_id`.
7. Publishes the `batch_chain_hash` and `merkle_root` for this batch to
   S3 (Object Lock, WORM) as the daily anchor. Daily anchors are also
   published to an append-only transparency log endpoint (Decision [DR-SEC-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md):
   daily Merkle roots written to AWS S3 Object Lock in Compliance WORM mode; external public blockchain/bulletin board deferred).

Verification: to verify that decision D has not been tampered with, an
auditor:
1. Fetches D's canonical JSON from the database.
2. Recomputes D's leaf hash.
3. Fetches the Merkle proof (sibling hashes) for D from `decision_audit_batches`.
4. Recomputes the Merkle root and confirms it matches the stored `merkle_root`.
5. Chains from the stored `batch_chain_hash` backward to the S3-anchored
   daily hash, confirming the chain is unbroken.

A verification CLI tool is shipped with Aegis and documented in the
operations guide. Auditors can run it without access to application code.

### Sealer failure handling

If the sealer fails mid-batch (e.g., the process crashes), it restarts
from the last fully committed batch. Decision rows with `sealed = false`
are reprocessed. The Merkle computation is deterministic over the same
ordered set of rows, so a partial batch is simply retried. The sealer
is a separate Spring Boot scheduled task; it does not block the decision
write path.

Alert: if `sealed = false` rows exist for more than `3 × SEAL_INTERVAL`
seconds, an alert fires. This indicates the sealer is stuck.

## Consequences

### Positive
- Decision write throughput is not serialised by the audit chain. The main
  transaction (ADR-0002) is unchanged.
- Management events are chained immediately; their tamper-evidence window
  is zero.
- The Merkle structure allows verification of individual decisions without
  reading the entire audit log.
- The sealer is a background job that can be scaled separately from the
  decision API.
- Batch anchoring to S3 Object Lock and a transparency log provides
  external verifiability without requiring access to the Postgres instance.

### Negative / Trade-offs
- Decision records are not chained in real-time; there is a sealer interval
  (≤ 30 seconds by default) during which a newly written decision is not yet
  included in the chain. This is acceptable for audit purposes; real-time
  fraud investigations use the unsealed records, which are already in
  Postgres.
- The sealer is an additional component to operate, monitor, and test.
- The Merkle proof verification requires tooling beyond a simple hash check.
  The CLI tool addresses this, but it must be maintained.
- Re-running the sealer on a failure window must be tested explicitly;
  an incorrect retry could produce a broken chain if batch boundaries are
  not correctly reconstructed.

### Neutral
- The `decisions` table gains two columns: `sealed` (boolean, default false)
  and `batch_id` (nullable, FK to `decision_audit_batches`). These are
  indexed for the sealer's `WHERE sealed = false` query.

## Alternatives considered

| Alternative | Why not chosen |
|-------------|----------------|
| Synchronous per-record chaining for all events | Serialises decision throughput per tenant at scale; becomes the dominant latency bottleneck |
| WAL-based tamper-evidence only | Does not produce an auditor-accessible, portable chain without WAL access |
| Kafka-based hash chain | Adds Kafka compaction and offset-verification complexity; keeps audit evidence split across two systems |
| No hash chaining (append-only Postgres + S3 backup immutability) | Simpler, but does not produce a verifiable chain that meets compliance auditor needs without full system access |

## References

- [docs/01-production-readiness-discovery.md §9.2](../01-production-readiness-discovery.md) — audit trail tamper-evidence requirements
- [docs/adr/0002-durable-decision-write-before-response.md](./0002-durable-decision-write-before-response.md) — decision write path (unchanged)
- Certificate Transparency (RFC 9162) — reference design for append-only transparency logs
- RFC 6962 — Merkle hash tree structure
