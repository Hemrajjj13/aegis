# Glossary
**Status:** Draft-complete (docs v1.0, machine-checked, not yet reviewed)
**Last updated:** October 2026

Terms are defined as they are used within Aegis documentation and the codebase.
When a term from a regulation or standard is used, the authoritative source is cited.
Terms in **bold** in a definition are themselves defined in this glossary.

---

| Term | Definition |
|------|-----------|
| **account_id** | The identifier for an account within a **tenant**. Aegis stores a tenant-keyed hash of this identifier, not the raw value. |
| **admin** | Canonical **human role**: Administrative management of users, API keys, environment configuration, and tenant settings. Cannot approve rule changes; cannot approve own invitation. See doc 01 §11.1, doc 19 §2. |
| **audit trail** | An append-only, hash-chained log of every **decision**, rule change, login, permission change, and data export. Stored per tenant in Postgres and exported daily to S3 with Object Lock. |
| **auditor** | Canonical **human role**: Read-only compliance and regulatory audit access, including full immutable audit log inspection, Merkle proof verification, and export generation. Cannot modify any configuration or data. See doc 01 §11.1, doc 19 §2. |
| **batch sealer** | A background scheduled job that runs every 30 seconds (configurable) and seals unsealed **decision** records into a **Merkle root**-based hash chain. See ADR-0004. The batch sealer does not block the decision write path. |
| **canary** | A rollout stage in which a new **ruleset** is applied to a configurable percentage of live traffic before full promotion. |
| **circuit breaker** | A mechanism that stops calling a downstream service (e.g., an enrichment provider) after a threshold of failures, to prevent cascading timeouts. During a circuit-open state, the step is skipped and the **decision** is marked **degraded**. |
| **claims discipline** | The engineering standard (Decision D9) requiring that any numeric performance claim (latency, throughput, memory) stated in documentation, marketing, or code comments must be directly measured on documented hardware using a recorded benchmark harness. Unmeasured numeric targets must be explicitly marked `[hypothesis, to validate]`. |
| **clock (injected)** | A `java.time.Clock` instance passed as a parameter to the evaluation core. In production it is set to the event's **event_time**. It is never `Clock.systemUTC()` inside evaluation logic. See ADR-0003. |
| **consumer lag** | The difference between the latest offset in a Kafka topic and the offset last committed by a consumer. Measured per partition; reported as the maximum across all partitions for a consumer group. |
| **Dataset Importer** | A streaming data ingestion pipeline in `aegis-dataset-importer` capable of reading historical CSV and JSONL transaction logs up to 100,000 rows without heap exhaustion, applying schema mappings, and enforcing the zero-PAN invariant. See Decision D8, ADR-0008, and doc 17 §3.8. |
| **dataset_imports** | A Postgres table and S3 ingestion capability tracking prospective customer backtest datasets, schema mapping configurations, validation status, and automated 30-day retention windows for the free backtest offer. See Decision D8 and doc 07 §6.3. |
| **dead-letter queue (DLQ)** | A Kafka topic to which messages are written when they cannot be processed after N retries. DLQ depth > 0 triggers a page. |
| **decision** | The output of the evaluation core for one transaction event: one of `approve`, `decline`, or `review`, accompanied by a score, the rules that fired and their contributions, the **ruleset** version hash, latency, and a **degraded** flag. |
| **decision_audit_batches** | A Postgres table written by the **batch sealer**. Each row records one sealed batch: `(tenant_id, batch_id, first_decision_id, last_decision_id, batch_size, merkle_root, batch_chain_hash, sealed_at)`. The chain of `batch_chain_hash` values provides tamper-evidence for decision records. |
| **decision_id** | A UUID assigned to each **decision** by Aegis. Globally unique. Used for idempotency checks and downstream deduplication. |
| **degraded** | A flag on a **decision** indicating that the evaluation could not complete normally (e.g., a feature step timed out). The **fail policy** was applied to produce the outcome. |
| **deployment tiers** | The three operational footprints sharing the identical unified codebase (Decision D1): **Tier 0** (developer workstation Docker Compose, RAM budget $\le 3.89$ GB), **Tier 1** (single-AZ AWS shadow mode pilot, ~$450–$650/mo), and **Tier 2** (multi-AZ managed enterprise production). See doc 14 §2.1. |
| **developer** | Canonical **human role**: Technical platform integration role managing API keys, webhooks, and sandbox environments; denied production PII view access by default. See doc 01 §11.1, doc 19 §2. |
| **design partner** | A customer who uses Aegis in shadow or pilot mode in exchange for discounted or free access, feedback, and case-study rights. |
| **DPDP Act 2023** | Digital Personal Data Protection Act 2023 (India). Governs collection, use, and storage of personal data of Indian residents. *Needs counsel verification for its application to Aegis.* |
| **EvaluationCore** | The pure Java 21 evaluation kernel in `aegis-core` that executes compiled CEL rules and velocity sliding windows against payment events with zero runtime external I/O or wall-clock dependencies. See doc 17 §3.1. |
| **event_id** | The identifier for a transaction event, assigned by the originating system. Combined with **tenant_id**, must be globally unique within Aegis. Used to enforce exactly-once **decisions** via unique constraint on `(tenant_id, event_id)`. |
| **event_time** | The timestamp at which a transaction occurred, as reported by the originating system. Used as the clock for all rule evaluation and **velocity** window computation. Never the wall-clock time at which Aegis received the event. See ADR-0003. |
| **evaluation core** | A Java library compiled into `decision-api`, `stream-processor`, and `replay-engine`. Contains rule loading, feature extraction, **velocity** check, rule evaluation, and **decision** assembly. There is exactly one copy of this code; all three paths execute it. |
| **fail-closed** | A **fail policy** in which a **degraded** evaluation produces a `decline` outcome. |
| **fail-open** | A **fail policy** in which a **degraded** evaluation produces an `approve` outcome, flagged for review. |
| **fail policy** | A tenant-level configuration specifying whether a **degraded** evaluation is **fail-open** or **fail-closed**. There is no system default; every tenant must set this explicitly before going live. |
| **Free Backtest Offer** | The primary zero-cost commercial go-to-market wedge: prospective customers upload a historical anonymized dataset to receive an executive backtest comparison report showing newly caught fraud, reduced false alarms, and latency deltas without writing code or touching live production traffic. See doc 15 §2.2. |
| **Gate G1** | The validation gate governing pre-customer data entry (Decision D2): requires 10+ completed discovery interviews and full legal review of data protection (DPDP Act 2023), India data localisation, PCI DSS scope, and DPA terms before ANY real customer data enters Aegis. |
| **Gate G2** | The validation gate governing commercial contracts (Decision D2): requires signed pilot SOW and completed security questionnaire responses before executing a paid commercial contract. |
| **hard rule** | A rule that overrides the score and immediately produces a `decline` outcome. Block-listed entity membership is a common hard rule. Hard rules are applied after soft-rule scoring but before threshold evaluation. |
| **hash-chaining** | A tamper-evidence technique in which each record includes the SHA-256 hash of the previous record. Aegis uses two modes: (1) **synchronous chaining** for management events (rule changes, logins, etc.) — each event is chained in the same write transaction; (2) **batch chaining** for decision records — the **batch sealer** chains Merkle roots of batches every 30 seconds. See ADR-0004. |
| **Human-Only Zone** | A designated critical code or schema domain (Authentication, Cryptography/Sealing, Velocity Lua scripts, and Flyway database migrations) where AI coding agents may draft changes but cannot merge or deploy without explicit human architect review and sign-off. See AGENTS.md §2. |
| **ICP (Ideal Customer Profile)** | The description of the customer type for whom the product delivers the most value and who is most likely to buy: mid-size Indian fintechs and payment companies (~1–10 M transactions/month, risk team 1–5 people, as of Stage 1 hypothesis). |
| **idempotency cache** | A Redis cache keyed by `(tenant_id, event_id)` that stores the decision outcome for recently processed events. The async Kafka consumer checks this cache before invoking the velocity Lua script. TTL is at least 2× the Kafka ingest topic retention period. |
| **idempotency key** | A caller-supplied identifier for a request. Combined with a payload hash, it allows the Decision API to return a stored **decision** for a repeated request without re-evaluating. See architecture overview §6.1. |
| **IdentityProvider Port** | The Java Service Provider Interface (SPI) decoupling user authentication, session issuance, and credential hashing from specific identity brokers. Backed by `InProcessSpringSecurityIdentityProvider` in Tier 0/1 and `KeycloakOidcIdentityProvider` in Tier 2. See ADR-0007 and doc 19 §7. |
| **InMemoryVelocityWindow** | A Java in-process implementation of the **velocity** sliding-window algorithm, used exclusively by the **replay** engine. Has identical semantics to the production **velocity Lua script**. Equivalence is verified by `VelocityImplementationEquivalenceTest`. See ADR-0005. |
| **Legal Review Brief** | The formal scope of engagement and dossier submitted to qualified Indian legal counsel at Gate G1 to obtain definitive legal opinions regarding DPDP Act Data Processor posture, RBI data localisation, zero-PAN PCI DSS scoping, and bilateral DPA terms. See doc 13 §4. |
| **live-vs-replay divergence** | The fraction of transactions in a **replay** run where the replay decision differs from the stored live decision. Expected to be < 0.1% due to concurrent-arrival ordering differences on the sync path. Tracked as `replay_divergence_count` and `replay_divergence_pct` in the replay report. See ADR-0003. |
| **maker-checker** | An authorisation pattern requiring that the person who authors a change cannot also approve it. Enforced in application code for rule publishing. |
| **Merkle root** | The root hash of a binary hash tree (Merkle tree) over a batch of **decision** records. Each leaf is SHA-256 of one decision's canonical JSON. Used by the **batch sealer** to provide tamper-evident, efficiently verifiable audit of decision batches. See ADR-0004. |
| **Modular Monolith** | The chosen application architecture for Project Aegis (ADR-0001): a single deployable JVM application partitioned internally into strictly decoupled modules with enforced architectural boundaries, avoiding microservices distributed network latency and operational complexity in early stages. |
| **multi-tenancy** | The architecture property by which a single Aegis deployment serves multiple independent customers (**tenants**). Isolation is enforced by row-level security in Postgres and by scoping every Redis key and Kafka key with **tenant_id**. |
| **outbox** | A Postgres table written in the same transaction as a **decision**. A relay process reads committed outbox rows and publishes them to Kafka, ensuring that every persisted decision eventually produces a downstream event without requiring a distributed transaction. See ADR-0002. |
| **owner** | Canonical **human role**: Organization superuser with full authority over billing, tenant configuration, user management, and tenant deletion. See doc 01 §11.1, doc 19 §2. |
| **PAN (Primary Account Number)** | A card number conforming to ISO 7812. Aegis never accepts, stores, or logs a raw PAN. Protection uses a two-layer approach: strict typed schema validation (rejecting unexpected strings in numeric/typed fields) and Luhn-valid 13–19 digit scanning on free-text/unknown fields. |
| **PCI DSS** | Payment Card Industry Data Security Standard. A security standard for organisations that handle branded payment cards. *Needs counsel verification for scope applicability to Aegis.* |
| **processed_events** | A Postgres table with a unique constraint on `(tenant_id, event_id)`. An insert into this table and an insert into `decisions` occur in the same database transaction, providing exactly-once **decision** semantics under at-least-once Kafka delivery. |
| **RBI** | Reserve Bank of India. The central bank and primary financial regulator. Issues circulars on payment system operations, data localisation, and fraud risk management that apply to Aegis's customers. |
| **received_time** | The timestamp at which Aegis received an event. Stored for observability but never used in rule evaluation. |
| **reconciliation job** | A periodic job that rebuilds **velocity** counters in Redis from the source-of-truth data in Postgres, detects drift, and alerts if drift exceeds a threshold. |
| **replay** | The process of running a historical dataset through the evaluation core with a specified **ruleset** (or two rulesets for comparison), producing isolated results in `replay_*` tables keyed by **run_id**. |
| **replay determinism** | The property that running the same dataset through the same **ruleset** produces identical **decisions** on any two runs, regardless of when or on which machine the replay executes. Achieved by using **event_time** and ordering events by `(event_time, sequence_number)`. |
| **risk_analyst** | Canonical **human role**: Frontline risk investigator who operates the case review queue, reviews flagged payments, submits approve/decline dispositions, and labels transactions. Prohibited from modifying production rules or system settings. See doc 01 §11.1, doc 19 §2. |
| **risk_manager** | Canonical **human role**: Dual-control checker who reviews, approves, and promotes rulesets to canary and live stages, configures thresholds and fail policies, and executes replays. Cannot approve a ruleset they authored (maker-checker enforced). See doc 01 §11.1, doc 19 §2. |
| **roles (canonical)** | The seven mutually agreed human roles across Aegis: **owner**, **admin**, **risk_manager**, **rule_author**, **risk_analyst**, **auditor**, **developer**. See doc 01 §11.1, doc 19 §2. |
| **rule_author** | Canonical **human role**: Dual-control maker who drafts CEL detection rules, tests them against historical and synthetic datasets in simulation, and submits rulesets for approval. Cannot approve or publish rulesets directly to production. See doc 01 §11.1, doc 19 §2. |
| **ruleset** | A named, versioned, and cryptographically hashed set of rules that produces a **decision** for a transaction event. Rulesets are immutable once published; changes create a new version. |
| **ruleset version hash** | A SHA-256 hash of the complete ruleset content. Stored on every **decision** so that the exact rule logic that produced the decision can always be retrieved and reproduced. |
| **sequence_number** | A monotonic counter per `(tenant_id, entity_id)` assigned by the originating system. Used as a tie-breaker when two events share the same **event_time**, ensuring deterministic ordering in **replay**. |
| **shadow mode** | An operating mode in which Aegis evaluates transactions and produces **decisions** but does not transmit them to any downstream payment system. Used during pilot engagements to measure rule performance without affecting live transactions. |
| **SLO (Service Level Objective)** | A target metric for system reliability or performance, such as p99 latency < 100 ms. Aegis's SLOs are hypotheses until validated by load tests. See `docs/01-production-readiness-discovery.md §3`. |
| **SOC 2** | Service Organization Control 2. An auditing standard covering security, availability, processing integrity, confidentiality, and privacy. Aegis roadmap: Type I target within 9–12 months of pilot launch, Type II in 18 months, evaluated pre-commercial contract (Assumption [ASM-SEC-02](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) / Gate G2). |
| **tenant** | A single customer organisation using Aegis. All data, rules, decisions, and configuration are isolated by **tenant_id**. |
| **tenant_id** | A UUID assigned by Aegis to each **tenant**. Present in every Postgres row, every Redis key, and every Kafka message key. |
| **transactional outbox** | See **outbox**. |
| **velocity** | A feature class measuring the count or sum of a field (e.g., transaction amount) for a given entity over a sliding time window. Velocity checks use an atomic Redis Lua script (production) or an **InMemoryVelocityWindow** (replay). |
| **velocity Lua script** | A Redis Lua script that performs: (1) trim expired members, (2) check if `event_id` is already a sorted-set member (return `pass` without incrementing if so), (3) count, (4) compare to limit, (5) add `event_id` as member with `event_time` as score. Atomic; idempotent on `event_id`. See ADR-0005. |
| **vertical slice** | A thin, end-to-end implementation of one feature path: one transaction event in, one **decision** out, one audit row visible in the console. The first milestone of the Stage 1 build. |

---

## Canonical Role List

To prevent role drift across specifications and runtime checks, Project Aegis defines exactly seven canonical roles:

1. **`owner`**: Complete authority over the tenant organization, including billing, user lifecycle, and organization deletion.
2. **`admin`**: Administrative management of users, API keys, settings, and environments. Cannot approve rule changes; cannot approve their own invitation.
3. **`risk_manager`**: Dual-control checker authorized to approve and promote rulesets to canary/live, set risk thresholds and fail policies, and run replays. Cannot approve rules they authored (maker-checker).
4. **`rule_author`**: Dual-control maker authorized to draft CEL rules, run simulations against synthetic datasets, and submit rules for peer approval. Cannot publish or approve rules.
5. **`risk_analyst`**: Frontline analyst operating the case review queue, performing transaction triage, submitting case dispositions, and labeling training data. Cannot edit rules or settings.
6. **`auditor`**: Read-only compliance auditor with access to full tamper-evident audit ledgers, Merkle proofs, and JSON export logs. Cannot mutate any state.
7. **`developer`**: Platform integration engineer managing API credentials, webhooks, and sandbox testing. Denied access to production PII fields by default.

