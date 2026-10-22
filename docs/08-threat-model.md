# Threat Model (STRIDE) and Security Controls Matrix

**Document:** 08  
**Status:** Draft-complete (docs v1.0, machine-checked, not yet reviewed)
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

> **Assumption note:** Threat classifications, trust boundaries, and mitigations in this document reflect the Stage 1 modular monolith architecture (Java 21, Spring Boot, PostgreSQL, Redis, Kafka, React). Any requirement marked `[interview-driven]` depends on pilot customer enterprise security reviews and penetration testing mandates. Mark all latency and timeout targets as "hypothesis, to validate".

---

## 1. Purpose and Methodology

This document establishes the threat model and defensive architecture for Project Aegis. Because Aegis is a real-time financial decision engine returning verdicts (`approve`, `decline`, `review`) on high-throughput payment traffic, security failures directly cause financial loss, regulatory fines (RBI, DPDP Act 2023), or catastrophic reputation damage.

The analysis uses Microsoft's **STRIDE** methodology (Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege) evaluated across:
1. **Trust Boundaries** between external callers, internal application runtimes, and persistence layers.
2. **Container Decomposition** covering all 11 architecture components defined in [overview.md §4](file:///c:/dev/aegis/docs/05-architecture/overview.md#L91-L160).
3. **Dedicated Abuse Cases** modeling sophisticated adversary tactics (e.g., maker-checker bypass, CEL ReDoS, RLS leakage, replay dataset poisoning).
4. **Security Controls Matrix** mapping mitigations to commitments in [01-production-readiness-discovery.md](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md).

---

## 2. Trust Boundaries and Data Flow

### 2.1 Trust Boundaries

```
[ External Callers: Tenant Payment Gateway / Core Banking ]
                        │
                        │ TB-1: Public Internet / Ingest Perimeter (mTLS / HMAC / TLS 1.3)
                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ AWS VPC — Public Subnet (ALB / WAF / Cloudflare)                            │
└───────────────────────┬─────────────────────────────────────────────────────┘
                        │
                        │ TB-2: Internal Ingress Perimeter
                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ AWS VPC — Private App Subnet (Compute)                                      │
│                                                                             │
│  ┌──────────────────────┐  TB-3: Inter-Service  ┌────────────────────────┐  │
│  │ decision-api         │◄─────────────────────►│ console-api (BFF)      │  │
│  └──────────┬───────────┘                       └───────────┬────────────┘  │
│             │                                               │               │
│  ┌──────────┴───────────┐                       ┌───────────┴────────────┐  │
│  │ stream-processor     │                       │ identity-service       │  │
│  └──────────┬───────────┘                       └───────────┬────────────┘  │
│             │                                               │               │
│  ┌──────────┴───────────┐                       ┌───────────┴────────────┐  │
│  │ rules-service        │                       │ replay-engine (batch)  │  │
│  └──────────┬───────────┘                       └───────────┬────────────┘  │
└─────────────┼───────────────────────────────────────────────┼───────────────┘
              │                                               │
              │ TB-4: Managed Data Perimeter (IAM / VPC Endpoints / RLS)
              ▼                                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ AWS VPC — Isolated Data Subnet (Persistence)                                │
│                                                                             │
│  ┌──────────────┐     ┌──────────────────────┐     ┌─────────────────────┐  │
│  │ Redis        │     │ PostgreSQL           │     │ Kafka (MSK)         │  │
│  │ (Counters)   │     │ (Decisions/Rules/RLS)│     │ (Ingest/Decisions)  │  │
│  └──────────────┘     └──────────────────────┘     └─────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ AWS S3 (WORM Audit Anchors, Parquet Archives, Replay Datasets)        │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

- **TB-1: External Perimeter:** Untrusted external callers communicating over TLS 1.3 to AWS Application Load Balancers / WAF. Requires HMAC-SHA256 request signing or OAuth2 mTLS.
- **TB-2: Browser / Console Perimeter:** Human operators across the seven canonical roles (`owner`, `admin`, `risk_manager`, `rule_author`, `risk_analyst`, `auditor`, `developer`) accessing the React single-page application. Authenticated via Keycloak/Spring Authorization Server session tokens (HTTP-only, Secure, SameSite cookies + MFA).
- **TB-3: Internal Container Boundary:** Service-to-service communication within the private VPC subnet. All communication strictly enforced over private VPC DNS; mTLS in production.
- **TB-4: Data Persistence Perimeter:** Application containers accessing managed data stores (Postgres, Redis, Kafka, S3). Access is governed by least-privilege database roles, IAM execution roles, and PostgreSQL Row-Level Security (RLS).
- **TB-5: Replay Isolation Boundary:** Replay jobs operate in an isolated compute/sandbox boundary. Replay engine database role (`app_replay_user`) is strictly blocked from writing live tables ([ADR-0003](file:///c:/dev/aegis/docs/adr/0003-event-time-for-deterministic-replay.md), [07-data-model.md §2](file:///c:/dev/aegis/docs/07-data-model.md#L30-L50)).

### 2.2 Data Flow Table

| Flow ID | Source | Destination | Protocol / Auth | Data Transferred | Sensitivity | Controls & Mitigations |
|---|---|---|---|---|---|---|
| **DF-01** | Payment Core / Gateway | `decision-api` | HTTPS (TLS 1.3), HMAC-SHA256 or OAuth2 mTLS | Transaction payload (`amount`, `card_token`, `account_id_hash`, `device_id`) | Confidential — Business / Personal | Strict schema validation; Luhn scan on free-text fields; replay cache ([06-api-and-event-schemas.md](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md)) |
| **DF-02** | External Core / Gateway | Kafka `ingest` | Kafka mTLS / SASL_SSL SCRAM-512 | Asynchronous payment events | Confidential | Schema registry enforcement; topic ACLs; consumer group partition locks |
| **DF-03** | `decision-api` / `stream-proc` | Redis | Redis TLS (AUTH + Hash Tags) | Velocity counter Lua scripts; idempotency keys | Restricted — Operational | Key namespacing `{tenant_id:account_id}`; Lua atomicity; no cleartext PII in Redis |
| **DF-04** | `decision-api` / `stream-proc` | PostgreSQL | JDBC over TLS (SSLMode=verify-full) | Decision rows, `processed_events`, `outbox` records | Confidential — Financial Record | HikariCP pool sets `app.current_tenant_id`; RLS enforced; synchronous commit ([ADR-0002](file:///c:/dev/aegis/docs/adr/0002-durable-decision-write-before-response.md)) |
| **DF-05** | `outbox-relay` | Kafka `decisions` | Kafka mTLS | Evaluated decision events, reason codes | Confidential | Outbox pattern; TLS; consumer access restricted to tenant-specific consumers |
| **DF-06** | Browser Console | `console-api` | HTTPS (TLS 1.3), Secure Session Cookie | Case reviews, rule drafts, simulation requests | Internal / Restricted | CSRF tokens; RBAC checks; rate limiting; audit log of all analyst actions |
| **DF-07** | `console-api` | `identity-service` | HTTPS (internal mTLS), OIDC / OAuth2 | User credentials, MFA codes, JWT exchange | Highly Restricted | Argon2id only password hashing; TOTP MFA; brute-force lockouts |
| **DF-08** | `rules-service` | PostgreSQL | JDBC over TLS | Ruleset DSL, AST, version hashes, maker-checker signoffs | Internal — Critical Policy | DB check constraints enforcing author != approver; canonical SHA-256 versioning |
| **DF-09** | `replay-engine` | PostgreSQL / S3 | JDBC (read-only live, write replay_*), HTTPS S3 SigV4 | Historical transaction streams, replay reports | Confidential | Role `app_replay_user` has NO live write privileges; isolated S3 replay prefixes |
| **DF-10** | Sealer Daemon | AWS S3 (WORM) | HTTPS S3 SigV4 (VPC Gateway Endpoint) | Merkle batch chain roots, audit anchor JSON | Critical — Compliance Audit | S3 Object Lock (Compliance mode); bucket policy prohibits deletion/overwrite |

---

## 3. STRIDE Threat Analysis Across All Containers

Below is the STRIDE analysis conducted individually for each of the 11 containers described in [overview.md §4](file:///c:/dev/aegis/docs/05-architecture/overview.md#L91-L160).

### 3.1 `decision-api`
- **Spoofing:** Attacker impersonates a legitimate tenant payment gateway to inject fraudulent transactions or exhaust velocity limits.
  - *Mitigation:* Dual authentication: OAuth2 Client Credentials or HMAC-SHA256 request signing with API keys; 5-minute timestamp validity window; IP allowlisting per tenant [interview-driven].
- **Tampering:** Attacker alters transaction payload in transit (e.g., lower amount to bypass review threshold).
  - *Mitigation:* Strict TLS 1.3 with PFS (P-384 / RSA-4096); HMAC-SHA256 signature covers the full request body (`event_id`, `event_time`, `amount`, `account_id`).
- **Repudiation:** Tenant claims Aegis generated an unauthorized `decline` or failed to process an event.
  - *Mitigation:* Synchronous database commit to `decisions` and `processed_events` before returning HTTP response ([ADR-0002](file:///c:/dev/aegis/docs/adr/0002-durable-decision-write-before-response.md)); full audit log record with `payload_hash` and rules fired.
- **Information Disclosure:** Unmasked PAN or bank account numbers logged in application logs or returned in error payloads.
  - *Mitigation:* Zero raw PAN policy (doc 01 §5.1); typed schema validation rejects strings in numeric fields; regex redaction filter in logback pipeline; RFC 7807 problem details omit internal stack traces.
- **Denial of Service:** Volumetric DDoS attack flooding `/v1/decisions` to exhaust Spring Boot virtual threads or database connections.
  - *Mitigation:* AWS WAF rate-limiting; per-tenant token bucket rate limits in Redis (HTTP 429); maximum payload size limit (64 KB); circuit breakers on feature lookups.
- **Elevation of Privilege:** Caller sends a different `tenant_id` in payload than authenticated API key identity.
  - *Mitigation:* `decision-api` strictly binds `tenant_id` from the authenticated security context, overriding any client-supplied tenant identifier.

### 3.2 `stream-processor`
- **Spoofing:** Unauthorized producer injects counterfeit messages into Kafka `ingest` topic.
  - *Mitigation:* Kafka SASL_SSL / mTLS; Kafka ACLs enforce that only authorized gateway producers can write to `ingest`.
- **Tampering:** Corrupted or out-of-order event payload delivered from Kafka partition.
  - *Mitigation:* Confluent/Apicurio Schema Registry enforces Avro schema validation; payloads out-of-order beyond 30 seconds are routed to DLQ.
- **Repudiation:** Consumer commits Kafka offset without writing to Postgres, losing transactions without a trace.
  - *Mitigation:* Offset is committed strictly *after* the PostgreSQL transaction (including `decisions` and `processed_events`) commits.
- **Information Disclosure:** Sensitive payload dumped to consumer crash logs or DLQ topic.
  - *Mitigation:* DLQ topic encrypted with KMS; DLQ messages masked of PII; consumer logs redact payload bodies.
- **Denial of Service:** Massive message backlog (lag > 10,000) causes consumer memory exhaustion.
  - *Mitigation:* Partition-scaled consumer groups; backpressure controls; alert at lag > 5 seconds (hypothesis, to validate).
- **Elevation of Privilege:** Compromised stream consumer attempts to modify tenant configuration.
  - *Mitigation:* Database user `app_live_user` has SELECT on `ruleset_versions` and INSERT on `decisions`/`processed_events`; no DDL or config write rights.

### 3.3 `rules-service`
- **Spoofing:** Malicious user masquerades as a Risk Manager to approve a compromised ruleset.
  - *Mitigation:* Identity token validation; strict RBAC checks; re-authentication required for rule approval actions.
- **Tampering:** Direct SQL modification of a published rule to insert a fraud backdoor.
  - *Mitigation:* Rulesets are immutable and identified by canonical SHA-256 hash ([09-rule-dsl-spec.md §6](file:///c:/dev/aegis/docs/09-rule-dsl-spec.md#L285-L315)); evaluation core verifies SHA-256 against database on reload; any discrepancy triggers fatal alert.
- **Repudiation:** Rule Author denies creating a rule that caused operational decline storms.
  - *Mitigation:* Synchronously hash-chained `audit_management_events` captures author identity, IP, diff payload, and timestamp ([ADR-0004](file:///c:/dev/aegis/docs/adr/0004-batched-audit-log-hash-chaining.md)).
- **Information Disclosure:** Proprietary fraud detection logic leaked to unauthorized developers.
  - *Mitigation:* Rule editor access restricted to Rule Author and Risk Manager roles; RLS ensures tenant rules are isolated.
- **Denial of Service:** Ingestion of massive rule definitions with exponential AST complexity.
  - *Mitigation:* Maximum ruleset size limit (500 KB); AST node depth limit (15 levels); CEL compilation validation in sandbox before commit.
- **Elevation of Privilege:** Rule Author self-approves their own draft rule.
  - *Mitigation:* Database-level check constraint `chk_maker_checker` (`author_id <> approver_id`) plus application-layer enforcement ([07-data-model.md §5.2](file:///c:/dev/aegis/docs/07-data-model.md#L210-L240)).

### 3.4 `replay-engine`
- **Spoofing:** Rogue job pretends to be a valid replay worker to access historical financial records.
  - *Mitigation:* Replay workers run as ephemeral ECS tasks authenticated via AWS IAM Task Roles; job creation restricted to Risk Manager role.
- **Tampering:** Replay run modifies production decisions or updates live velocity counters.
  - *Mitigation:* Replay engine connects using `app_replay_user`, which is explicitly REVOKED of all INSERT/UPDATE/DELETE rights on live tables; uses in-memory velocity window, never Redis ([ADR-0003](file:///c:/dev/aegis/docs/adr/0003-event-time-for-deterministic-replay.md), [ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md)).
- **Repudiation:** Analyst runs backtest on sensitive dataset and exports results without audit trail.
  - *Mitigation:* Every replay run invocation is logged in `replay_runs` and `audit_management_events` with initiator ID, dataset source, and ruleset version hashes.
- **Information Disclosure:** Replay reports expose customer PII in evaluation diffs.
  - *Mitigation:* Replay tables store only `event_id`, score, and rule firing metadata; PII fields are never duplicated into `replay_decisions`.
- **Denial of Service:** Massive replay job starves production database of CPU and disk I/O.
  - *Mitigation:* Replay reads historical events strictly from read replicas or S3 cold archives; primary production Postgres is never targeted by replay queries.
- **Elevation of Privilege:** Replay job attempts to promote a candidate ruleset to production.
  - *Mitigation:* Replay engine has read-only access to rule configurations; promotion API lives exclusively in `rules-service` behind maker-checker.

### 3.5 `console-api` (BFF)
- **Spoofing:** Session hijacking or cookie theft allowing unauthorized access to the Aegis management console.
  - *Mitigation:* Secure, HttpOnly, SameSite=Strict cookies; session idle timeout (15 minutes); IP/User-Agent binding; absolute timeout (8 hours).
- **Tampering:** Cross-Site Request Forgery (CSRF) triggering unauthorized rule changes or user role changes.
  - *Mitigation:* Double-submit CSRF tokens on all state-changing endpoints (`POST`, `PUT`, `DELETE`).
- **Repudiation:** Admin changes tenant fail policy (e.g., switches to fail-open) and denies it after fraud occurs.
  - *Mitigation:* All settings updates committed to `audit_management_events` with previous/new value diff and actor identity.
- **Information Disclosure:** IDOR (Insecure Direct Object Reference) allowing tenant A analyst to view tenant B transaction details.
  - *Mitigation:* Global tenant context filter; PostgreSQL RLS policies enforce `tenant_id` at the database level on every query ([07-data-model.md §7](file:///c:/dev/aegis/docs/07-data-model.md#L285-L345)).
- **Denial of Service:** Analyst runs unindexed query across 50 million decisions.
  - *Mitigation:* Strict pagination limits (max 100 records); forced query filters (`created_at` date range required, max 30-day window on live search).
- **Elevation of Privilege:** Analyst crafts API request to escalate their role to Admin or Risk Manager.
  - *Mitigation:* RBAC enforced via Spring Security `@PreAuthorize`; role assignments verified against cryptographically validated JWT claims from `identity-service`.

### 3.6 `identity-service`
- **Spoofing:** Credential stuffing or password guessing targeting Aegis console users.
  - *Mitigation:* Argon2id password hashing; mandatory multi-factor authentication (TOTP or FIDO2 WebAuthn); account lockout after 5 consecutive failures.
- **Tampering:** Attacker tampers with user session tokens or JWT claims.
  - *Mitigation:* JWTs signed using RS256 with private keys stored in AWS KMS; short token expiry (15 minutes); asymmetric key rotation.
- **Repudiation:** User denies logging in or initiating password reset.
  - *Mitigation:* All auth events (login, logout, failed attempt, password reset, MFA challenge) recorded in `audit_management_events`.
- **Information Disclosure:** Password reset tokens or invite links leaked via URL referrer or logs.
  - *Mitigation:* Cryptographically random tokens (256-bit); single-use; 24-hour expiry for invites; 15-minute expiry for password resets; hashed in database.
- **Denial of Service:** Flooding OTP or login endpoints to trigger rate limits or third-party SMS/email charges.
  - *Mitigation:* Strict IP-based and user-based rate limiting on auth endpoints (max 5 requests/minute).
- **Elevation of Privilege:** Attacker modifies role claim in forged token to gain `Owner` privileges.
  - *Mitigation:* Token signature verified against JWKS on every internal API call; `kid` header checked against active keys.

### 3.7 `console` (React Frontend)
- **Spoofing:** Phishing site mimicking Aegis console to capture credentials.
  - *Mitigation:* Strict Content Security Policy (CSP); HSTS with long max-age and preload; domain registration defensive monitoring.
- **Tampering:** Cross-Site Scripting (XSS) injecting malicious JavaScript into transaction review notes.
  - *Mitigation:* DOMPurify sanitization; React default escaping; CSP headers disabling `unsafe-inline` and `unsafe-eval`.
- **Repudiation:** Analyst changes transaction label and claims it was performed by another user.
  - *Mitigation:* Action signed by session cookie; backend records actor ID directly from authenticated session.
- **Information Disclosure:** Sensitive customer data cached in browser localStorage or sessionStorage.
  - *Mitigation:* Architecture rule: No PII or transaction data in `localStorage` or `sessionStorage`; in-memory React state only; cache cleared on logout.
- **Denial of Service:** Massive transaction list crash browser tab memory.
  - *Mitigation:* Virtualized rendering (TanStack Virtual / react-window) for all data tables; DOM node count capped.
- **Elevation of Privilege:** UI displays hidden navigation items if user toggles client-side role flags.
  - *Mitigation:* Client-side role checks only affect UI visibility; every single API endpoint enforces backend authorization independently.

### 3.8 `Redis`
- **Spoofing:** Rogue container connects to Redis without authentication.
  - *Mitigation:* Redis in private isolated subnet; TLS required; strong AUTH password stored in AWS Secrets Manager; security groups restrict access to `decision-api` and `stream-processor`.
- **Tampering:** Attacker alters velocity counter keys or sorted-set members.
  - *Mitigation:* Redis commands restricted (e.g., `FLUSHALL`, `CONFIG`, `KEYS` disabled via `rename-command`); execution restricted to vetted Lua scripts ([ADR-0005](file:///c:/dev/aegis/docs/adr/0005-velocity-script-event-id-idempotency.md)).
- **Repudiation:** Counter updates fail silently without audit trail.
  - *Mitigation:* Redis is explicitly derived state; PostgreSQL is source of truth; reconciliation job runs every 5 minutes to verify alignment ([ADR-0002](file:///c:/dev/aegis/docs/adr/0002-durable-decision-write-before-response.md)).
- **Information Disclosure:** Memory dump exposes cleartext customer card numbers.
  - *Mitigation:* Zero PAN policy; sorted-set members are opaque `event_id` strings and hashes; Redis in-transit and at-rest encryption enabled (AWS ElastiCache encryption).
- **Denial of Service:** Redis memory exhausted by unbounded velocity window keys.
  - *Mitigation:* Lua script enforces TTL on every key; `maxmemory` eviction policy `volatile-lru`; memory usage alerting at 75% capacity (hypothesis, to validate).
- **Elevation of Privilege:** Command injection via malicious Lua script parameters.
  - *Mitigation:* All Lua scripts pre-loaded with `SCRIPT LOAD`; executed via `EVALSHA` only; arguments strictly passed as parameterized strings/numbers.

### 3.9 `PostgreSQL`
- **Spoofing:** Attacker uses stolen DB credentials from one pod to access another tenant's tables.
  - *Mitigation:* IAM database authentication or rotating Secrets Manager credentials; RLS enforced on all tables; superuser permissions revoked from runtime users.
- **Tampering:** SQL Injection altering decisions or dropping audit records.
  - *Mitigation:* 100% parameterized queries via Spring Data JPA / jOOQ; zero string concatenation for SQL; dynamic DDL prohibited at runtime.
- **Repudiation:** Malicious insider updates historical decision outcome from `DECLINE` to `APPROVE`.
  - *Mitigation:* Batch sealer Merkle hash chaining ([ADR-0004](file:///c:/dev/aegis/docs/adr/0004-batched-audit-log-hash-chaining.md)); any retroactive modification invalidates the Merkle root and batch chain; daily anchors published to S3 WORM.
- **Information Disclosure:** Direct database query by rogue DBA exposes tenant financial activity.
  - *Mitigation:* AWS KMS encryption at rest; column-level masking for PII; audit logging via `pgaudit`; DBA access requires dual-authorization break-glass.
- **Denial of Service:** Connection pool exhaustion from runaway queries.
  - *Mitigation:* HikariCP pool sized per container; PgBouncer connection pooling; statement timeouts (500 ms for operational transactions, 10 s for console queries).
- **Elevation of Privilege:** Tenant A bypasses RLS by setting `app.current_tenant_id` to Tenant B's UUID.
  - *Mitigation:* Application connection wrapper manages `SET LOCAL app.current_tenant_id` within a transactional boundary; application users have no permission to alter session config outside the wrapper.

### 3.10 `Kafka` (MSK)
- **Spoofing:** Rogue host joins Kafka cluster as a broker or consumer.
  - *Mitigation:* AWS MSK multi-AZ deployment; TLS encryption for client-broker and broker-broker communication; mTLS / SASL_SSL authentication.
- **Tampering:** Malicious modification of messages on Kafka disk.
  - *Mitigation:* EBS volume encryption with AWS KMS; Kafka record CRC checks; schema registry compatibility checks prevent malformed writes.
- **Repudiation:** Producer publishes message but claims it was never sent.
  - *Mitigation:* `acks=all` required on producers; producer record metadata logged with topic, partition, and offset.
- **Information Disclosure:** Eavesdropping on unencrypted Kafka traffic across VPC.
  - *Mitigation:* TLS enforced on all Kafka listeners (TLS 1.3 [verify against AWS docs]; TLS 1.2 minimum supported); no plaintext listeners enabled.
- **Denial of Service:** Single tenant floods Kafka partition, starving other tenants sharing the topic.
  - *Mitigation:* Kafka key is `{tenant_id:account_id}` ensuring fair partition distribution; Kafka producer client-side rate limits and quotas per tenant.
- **Elevation of Privilege:** Consumer group reads messages belonging to another tenant's dedicated topic.
  - *Mitigation:* Kafka ACLs restrict read/write access per service account; Stage 1 shared topics use tenant isolation in evaluation layer.

### 3.11 `AWS S3`
- **Spoofing:** Attacker generates presigned S3 URLs to upload poisoned replay datasets.
  - *Mitigation:* S3 Block Public Access enabled at account level; bucket policies enforce SigV4; presigned URLs generated only by backend with 15-minute validity.
- **Tampering:** Retroactive alteration of archived decision batches or audit roots.
  - *Mitigation:* AWS S3 Object Lock in Compliance Mode (WORM — Write Once, Read Many); retention period locked for 7 years; bucket versioning enabled.
- **Repudiation:** Deletion of historical audit logs to conceal fraudulent activity.
  - *Mitigation:* Object Lock prevents deletion even by AWS root account until retention period expires; MFA Delete enabled on bucket [verify against AWS docs for compatibility with Object Lock Compliance mode].
- **Information Disclosure:** Accidental public exposure of S3 bucket containing replay datasets.
  - *Mitigation:* AWS Macie scanning; S3 Block Public Access; AWS Config rule `s3-bucket-public-read-prohibited` with automated remediation.
- **Denial of Service:** Excessive S3 API calls causing throttling or billing spikes.
  - *Mitigation:* S3 lifecycle rules transition older partitions to Glacier Instant Retrieval; requests routed via VPC Gateway Endpoints.
- **Elevation of Privilege:** IAM role escalation to obtain `s3:PutObjectRetention` or `s3:BypassGovernanceRetention`.
  - *Mitigation:* IAM Service Control Policies (SCPs) explicitly deny `s3:Bypass*` permissions across all member accounts.

---

## 4. In-Depth Abuse Cases and Attack Scenarios

### 4.1 Abuse Case 1: API Key Theft and Exfiltration
- **Adversary Goal:** An attacker exfiltrates an active tenant API secret (e.g., from an insecure CI pipeline or developer laptop) and attempts to submit spoofed transactions or probe rule boundaries.
- **Attack Vector:**
  1. Attacker calls `/v1/decisions` using the stolen key from a foreign IP.
  2. Attacker runs automated dictionary attacks against threshold values to reverse-engineer fraud limits.
- **Defensive Mitigations:**
  - **HMAC Request Signing:** In addition to the API key ID, every request must include an HMAC-SHA256 signature calculated over `timestamp + "\n" + method + "\n" + path + "\n" + SHA256(body)`. A compromised key alone is insufficient without the signing implementation.
  - **IP Allowlisting:** Tenants can restrict valid API key usage to their designated egress CIDR blocks [interview-driven].
  - **Anomaly Detection:** Rapid geographic or rate shifts in API key usage trigger automated credential revocation and security alerts.
  - **Fast Key Rotation:** Console provides zero-downtime dual-key rotation: a new key is provisioned while the old key remains valid for a 24-hour overlap window.

### 4.2 Abuse Case 2: Replayed Signed Requests
- **Adversary Goal:** Intercept a legitimate high-value payment decision request and replay it multiple times to manipulate account velocity or force unexpected application behavior.
- **Attack Vector:**
  1. Adversary captures a valid signed HTTP payload from network telemetry or proxy logs.
  2. Adversary replays the exact payload 30 seconds later.
- **Defensive Mitigations:**
  - **Timestamp Expiration Window:** The `X-Aegis-Timestamp` header is verified on every request. If `abs(received_time - timestamp) > 300 seconds`, the request is rejected immediately with HTTP 401.
  - **Idempotency Cache Check:** The `Idempotency-Key` header and payload hash are cached in Redis. If a duplicate request arrives within the window, the engine returns the stored decision without re-evaluating or touching velocity counters ([06-api-and-event-schemas.md §4.1](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md#L450-L480)).
  - **PostgreSQL Unique Constraint:** In the event of Redis cache eviction, the unique constraint on `processed_events(tenant_id, event_id)` prevents duplicate processing ([07-data-model.md §3.2](file:///c:/dev/aegis/docs/07-data-model.md#L110-L135)).

### 4.3 Abuse Case 3: Tenant Isolation Bypass via Row-Level Security (RLS) Gap
- **Adversary Goal:** A compromised or malicious tenant crafts queries or API inputs that leak another tenant's decisions, rulesets, or velocity profiles.
- **Attack Vector:**
  1. Tenant A invokes a console API endpoint passing a transaction UUID belonging to Tenant B.
  2. If the application layer forgets to include `WHERE tenant_id = :tenantId`, the record is exposed.
- **Defensive Mitigations:**
  - **Forced Database RLS:** PostgreSQL Row-Level Security is enabled and forced (`FORCE ROW LEVEL SECURITY`) on all multi-tenant tables.
  - **Session Context Binding:** Spring Boot's connection interceptor executes `SET LOCAL app.current_tenant_id = :tenantId` on every connection checkout from HikariCP.
  - **Zero BYPASSRLS Roles:** Runtime database roles (`app_live_user`, `app_console_user`, `app_replay_user`) are strictly denied the `BYPASSRLS` privilege ([07-data-model.md §7](file:///c:/dev/aegis/docs/07-data-model.md#L285-L345)).
  - **Automated Multi-Tenant Penetration Tests:** CI test `CrossTenantIsolationSecurityTest` queries every table using Tenant A credentials while asserting Tenant B data returns 0 rows.

### 4.4 Abuse Case 4: Malicious CEL Rule (ReDoS and Resource Exhaustion)
- **Adversary Goal:** A rogue Rule Author writes a rule with catastrophic regex backtracking (Regular Expression Denial of Service) or extreme memory allocation to freeze the `evaluation-core` and cause decision latency timeouts.
- **Attack Vector:**
  1. Author submits a rule condition containing an evil regex:
     ```cel
     event.merchant_name.matches('^([a-zA-Z0-9]+)*$')
     ```
  2. A transaction with a long, non-matching string causes catastrophic backtracking in the JVM regex engine, pinning the CPU core for seconds and exhausting the 80 ms latency budget.
- **Defensive Mitigations:**
  - **CEL Non-Turing Completeness:** CEL natively disallows loops, recursion, and user-defined functions, bounding theoretical complexity.
  - **AST Complexity & Static Limits:** Compilation fails if AST node count exceeds 250 nodes or depth exceeds 15 levels. Regex patterns are restricted to RE2 linear-time safety rules.
  - **Per-Ruleset Evaluation Budget:** The evaluation core executes CEL rules with static limits as primary protection against thread exhaustion, bounded by a per-ruleset evaluation budget in milliseconds (hypothesis: 10 ms total deadline for all rules, to validate by benchmark). If the ruleset evaluation deadline is exceeded, remaining rules are aborted, and the tenant's configured fail policy is triggered with `degraded = true` ([doc 01 §4](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L121-L145)).

### 4.5 Abuse Case 5: Rule-Publish Maker-Checker Bypass
- **Adversary Goal:** A rogue employee or compromised account with `Rule Author` or `Risk Manager` permissions publishes a backdoored rule that approves fraudulent transactions without secondary review.
- **Attack Vector:**
  1. Attacker drafts a rule that forces `APPROVE` on a specific account or card token.
  2. Attacker attempts to call the `/approve` or `/publish` endpoint directly using their own authentication token.
- **Defensive Mitigations:**
  - **Database Constraint Enforcement:** The `ruleset_versions` table enforces a hard SQL check constraint:
    ```sql
    CONSTRAINT chk_maker_checker CHECK (
        lifecycle_state NOT IN ('APPROVED', 'SHADOW', 'CANARY', 'LIVE') 
        OR (author_id <> approver_id AND approver_id IS NOT NULL)
    )
    ```
    Even if application-layer checks fail, PostgreSQL aborts the transaction if `author_id == approver_id` ([07-data-model.md §5.2](file:///c:/dev/aegis/docs/07-data-model.md#L210-L240)).
  - **Role Segregation:** Only a distinct `Risk Manager`, `Admin`, or `Owner` can transition rules to `APPROVED`. A `Rule Author` cannot execute approvals, and no user can approve a ruleset version they authored.
  - **Immutable Audit Trail:** All approval submissions, rejections, and publications write to `audit_management_events` with cryptographic hash chaining ([ADR-0004](file:///c:/dev/aegis/docs/adr/0004-batched-audit-log-hash-chaining.md)).

### 4.6 Abuse Case 6: Poisoned Replay Dataset
- **Adversary Goal:** An adversary tampers with historical datasets used in the replay engine to misrepresent rule performance, artificially deflating false-positive rates or hiding fraud leakage.
- **Attack Vector:**
  1. Adversary modifies synthetic or historical transaction timestamps or ground-truth chargeback labels in S3.
  2. Backtest indicates the candidate ruleset is superior, leading to production promotion of an ineffective ruleset.
- **Defensive Mitigations:**
  - **Cryptographic Dataset Manifests:** Replay runs accept datasets only when accompanied by a signed manifest containing SHA-256 hashes of all input files.
  - **S3 Object Lock & Versioning:** Historical transaction archives are stored in S3 with Object Lock; past records cannot be edited or overwritten.
  - **Replay Determinism Check:** CI runs `ReplayDeterminismTest` asserting that two runs of identical dataset and ruleset yield bit-for-bit identical decision outputs ([ADR-0003](file:///c:/dev/aegis/docs/adr/0003-event-time-for-deterministic-replay.md)).

### 4.7 Abuse Case 7: Invite-Token and OTP Abuse
- **Adversary Goal:** An attacker intercepts or brute-forces user invitation tokens or password reset OTPs to take over console accounts.
- **Attack Vector:**
  1. Attacker floods the password reset endpoint and attempts to guess 6-digit OTP codes via distributed botnets.
  2. Attacker uses an expired or previously used invitation token to register an unauthorized user.
- **Defensive Mitigations:**
  - **High Entropy Tokens:** Invite tokens are 256-bit cryptographically secure random values (CSPRNG); stored as SHA-256 hashes in PostgreSQL.
  - **Strict Lifetime & Single-Use:** Invite tokens expire in 24 hours; password reset OTPs expire in 15 minutes; tokens are consumed immediately upon first validation.
  - **Brute-Force Lockouts:** OTP endpoints enforce rate limiting: 5 incorrect attempts locks the target account for 30 minutes; requires admin unlocking or re-authentication.

### 4.8 Abuse Case 8: Insider Access to PII and Cardholder Data
- **Adversary Goal:** A database administrator, support engineer, or internal developer queries production databases or application logs to harvest card numbers or customer transaction records.
- **Attack Vector:**
  1. Internal user connects to PostgreSQL replica or inspects log aggregation dashboards (e.g., CloudWatch, OpenSearch).
- **Defensive Mitigations:**
  - **Zero Raw PAN Invariant:** Aegis never stores raw 13–19 digit PANs in any database, cache, or log file ([doc 01 §5.1](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L101-L130)). Only tokenised card identifiers or masked BIN/last4 are permitted.
  - **Dynamic Masking & RBAC:** In the console, account identifiers and customer metadata are masked by default. Only users with the explicit `Auditor` or `Risk Manager` role can unmask fields, and every unmask action is recorded in `audit_management_events`.
  - **Database Encryption & Query Auditing:** `pgaudit` logs all queries executed by administrative roles. Direct access to production database instances requires ephemeral break-glass access approved by two engineers.

---

## 5. Threat, Mitigation, Owner, and Verification Test Table

| Threat ID | Target Container | Threat Description | Mitigation Strategy | Owner | Verification Test / CI Gate |
|---|---|---|---|---|---|
| **TH-01** | `decision-api` | Spoofed payment transaction via forged API request | Dual authentication: HMAC-SHA256 request signing + OAuth2 Client Credentials; 5-min timestamp window | Tech Lead | `ApiAuthenticationSecurityTest` (asserts missing/bad signature returns 401) |
| **TH-02** | `decision-api` | Replayed payment requests altering velocity counters | Idempotency cache in Redis + PostgreSQL unique constraint on `(tenant_id, event_id)` | Backend Lead | `DuplicateDeliveryIntegrationTest` (asserts identical event 3x yields 1 decision) |
| **TH-03** | `decision-api` | Accidental logging of raw PAN or sensitive PII | Typed schema validation; free-text Luhn filter; Logback regex redaction mask | Security Lead | `PanPreventionSecurityTest` (asserts Luhn-valid string in free-text is rejected with 400) |
| **TH-04** | `decision-api` | Request flood exhausting application threads | AWS WAF rate-limiting; Redis token-bucket per tenant (HTTP 429) | DevOps Lead | `RateLimitingLoadTest` (asserts burst > limit receives 429) |
| **TH-05** | `stream-proc` | Unauthorized message injection into Kafka `ingest` | Kafka mTLS and SASL_SSL SCRAM-512; Confluent Schema Registry Avro validation | Platform Lead | `KafkaSecurityAclTest` (asserts unauthorized producer rejected by broker) |
| **TH-06** | `stream-proc` | Unchecked duplicate Kafka message processing | Consumer checks Redis idempotency cache, then Lua sorted-set check, then Postgres unique constraint | Backend Lead | `KafkaConsumerIdempotencyTest` (asserts redelivered offset produces zero drift) |
| **TH-07** | `rules-service` | Unauthorized or unilateral rule publication | Database-level `chk_maker_checker` constraint (`author_id <> approver_id`) | Backend Lead | `MakerCheckerBypassTest` (asserts author self-approval throws SQL constraint exception) |
| **TH-08** | `rules-service` | Malicious CEL rule causing ReDoS / thread hang | AST node limit (250); depth limit (15); RE2 linear regex validation; per-ruleset 10 ms execution budget [hypothesis, to validate by benchmark] | Tech Lead | `CelExecutionSafetyTest` (asserts evil regex fails compilation or budget exceeded cleanly) |
| **TH-09** | `rules-service` | Tampering with historical ruleset version logic | Rulesets are canonically serialized (RFC 8785) and hashed (SHA-256); verified before execution | Security Lead | `RulesetHashIntegrityTest` (asserts modified AST triggers hash mismatch error) |
| **TH-10** | `replay-engine` | Replay batch worker writes to live decision tables | `app_replay_user` role REVOKED of all INSERT/UPDATE permissions on `decisions` and `outbox` | Data Lead | `ReplayIsolationSecurityTest` (asserts replay connection attempting live insert fails with 42501) |
| **TH-11** | `replay-engine` | Non-deterministic replay invalidating comparison | Injected event-time clock (`java.time.Clock`); banned wall-clock calls in `evaluation-core` | QA Lead | `EvaluationCoreWallClockFreeTest` (ArchUnit test banning `Instant.now()`) |
| **TH-12** | `console-api` | Cross-tenant data leakage via missing WHERE clause | PostgreSQL Row-Level Security (`FORCE ROW LEVEL SECURITY`) with `app.current_tenant_id` | Security Lead | `CrossTenantIsolationSecurityTest` (asserts tenant A query on tenant B ID returns 0 rows) |
| **TH-13** | `console-api` | CSRF attack on rule modification or user management | SameSite=Strict cookies; double-submit anti-CSRF token on all mutating requests | Frontend Lead | `CsrfProtectionSecurityTest` (asserts mutating request without CSRF token fails with 403) |
| **TH-14** | `identity-svc` | Credential stuffing and brute-force account takeover | Argon2id hashing; mandatory TOTP/WebAuthn MFA; 5-attempt lockout for 30 minutes | Security Lead | `AuthenticationBruteForceTest` (asserts 6th failed attempt locks user) |
| **TH-15** | `console` | XSS attack stealing analyst session context | Strict CSP; DOMPurify sanitization; zero PII stored in browser `localStorage` | Frontend Lead | `CspAndXssSecurityTest` (static AST scan and automated Playwright XSS injection) |
| **TH-16** | `Redis` | Redis command injection or unauthenticated access | TLS required; strong AUTH password; dangerous commands renamed; private subnet only | DevOps Lead | `RedisSecurityConfigurationTest` (asserts unauthenticated/unencrypted access denied) |
| **TH-17** | `PostgreSQL` | SQL injection via dynamic query concatenation | 100% parameterized queries via Spring Data JPA / jOOQ; zero ad-hoc string concatenation | Tech Lead | `SqlInjectionStaticAnalysisTest` (Semgrep / CodeQL CI scan) |
| **TH-18** | `PostgreSQL` | Retroactive decision tampering concealing fraud | Background batch sealer hash-chains decisions using Merkle roots; daily S3 WORM anchor | Tech Lead | `AuditSealerVerificationTest` (asserts modified DB row breaks Merkle root verification) |
| **TH-19** | `Kafka` | Message eavesdropping across VPC network | Mandatory TLS encryption across all broker communication and client listeners (TLS 1.3 [verify against AWS docs]; TLS 1.2 minimum) | Platform Lead | `KafkaTlsEnforcementTest` (asserts plaintext connections dropped immediately) |
| **TH-20** | `AWS S3` | Deletion or modification of sealed audit logs | S3 Object Lock in Compliance Mode (WORM); bucket versioning; MFA Delete [verify against AWS docs] | DevOps Lead | `S3ObjectLockComplianceTest` (asserts delete API call on locked object returns 403) |

---

## 6. Controls Matrix Mapped to Document 01

This matrix maps every defensive control in this threat model to the commitments and operational standards defined in [01-production-readiness-discovery.md](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md).

| Threat Model Control | Reference in Doc 01 | Section Title in Doc 01 | Verification Mechanism in Doc 01 |
|---|---|---|---|
| **Fail-Open vs. Fail-Closed Policy Enforcement** | [doc 01 §4](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L121-L145) | Failure Policy | Chaos test simulating Redis/enrichment timeout; assert policy matches tenant config |
| **Zero Raw PAN Schema & Free-Text Scanning** | [doc 01 §5.1](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L103-L135) | What Aegis Stores / PAN Prevention | CI PAN prevention test: typed schema validation + Luhn-valid string rejection |
| **Data Classification & Tenant Isolation** | [doc 01 §5.2](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L140-L202) | Data Classification | Database Row-Level Security policy verified per release in launch gate §12 |
| **Data Retention & Crypto-Shredding** | [doc 01 §5.3](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L203-L215) | Retention and Deletion | Deletion job runs; KMS key revocation confirmation test |
| **Encryption at Rest & In-Transit** | [doc 01 §5.4](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L216-L226) | Encryption | AWS KMS per-tenant key management; TLS 1.3 enforcement; Secrets Manager audit |
| **India Data Localisation Alignment** | [doc 01 §6.1](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L234-L265) | India Data Localisation | AWS ap-south-1/ap-south-2 region constraint verified via AWS SCPs |
| **DPDP Act 2023 Controls** | [doc 01 §6.2](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L267-L277) | DPDP Act 2023 | DPA legal mapping; data principal deletion workflow verified |
| **PCI DSS Scope Minimisation** | [doc 01 §6.3](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L278-L287) | PCI DSS Scope | QSA assessment confirming zero PAN ingress and token-only processing |
| **Disaster Recovery & Multi-AZ Failover** | [doc 01 §7.1–§7.5](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L313-L373) | Reliability and Disaster Recovery | Game day chaos drills: Postgres multi-AZ failover (RTO < 30 min, RPO < 5 min) |
| **Audit Trail & Merkle Batch Sealing** | [doc 01 §9.1–§9.3](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L376-L425) | Audit Trail / Tamper-Evidence | Batch sealer CLI verification tool run against synthetic 10,000 decisions |
| **Maker-Checker Rule Lifecycle** | [doc 01 §11.2](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L450-L485) | Maker-Checker for Rule Publishing | Automated test verifying author cannot approve or publish rule |
| **Launch Gate Verification Checklist** | [doc 01 §12](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L490-L545) | Launch Gate Checklist | All security gates signed off by Tech Lead and Security Reviewer |

---

## 7. Decision Register References (formerly Open Questions)

All security questions have been resolved or classified per Decision **D3** in the [Decision Register](file:///c:/dev/aegis/docs/00-decisions/decision-register.md):

| # | Decision ID | Question / Topic | Status | Decision / Assumed Value |
|---|---|---|---|---|
| 1 | [DR-SEC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Client IP Allowlisting:** Enforcing caller IP ranges at ALB/WAF. | Decided | Optional tenant configuration; enforced at AWS WAF / ALB layer via CIDR match. |
| 2 | [DR-SEC-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **KMS Key Isolation Pricing:** Shared vs dedicated KMS CMKs. | Decided | Tier 0/1 uses single shared master KMS key with tenant UUID encryption context; Tier 2 supports dedicated CMKs. |
| 3 | [DR-SEC-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Public Transparency Log:** Merkle root anchoring in S3 Object Lock vs public ledger. | Decided | Daily Merkle roots written to AWS S3 Object Lock (Compliance WORM mode). Public blockchain deferred. |
| 4 | [DR-SEC-04](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Penetration Testing Vendor:** CERT-In / CREST accredited security assessment. | Deferred | Contract a CERT-In / CREST-accredited firm prior to Gate G2 (pre-paid commercial contract). |

