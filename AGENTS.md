# Project Aegis: Rules for AI Coding Agents

**Scope:** All autonomous and semi-autonomous AI coding agents (Antigravity, Claude, Codex, etc.) operating in this repository.  
**Authority:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md)).  
**Last updated:** October 2026  

---

## 1. Prime Directives for Coding Agents

You are pair programming on **Project Aegis**, a mission-critical, real-time transaction decision engine and deterministic backtest platform. Because this platform sits directly in the payment flow of financial institutions, bugs, race conditions, or uncoordinated schema drift can lead to direct financial loss or regulatory non-compliance.

You must strictly obey the following seven laws:

### Law 1: Read the Documentation Before Touching Code
Before writing, modifying, or refactoring any code in this repository, you **MUST** read the relevant specification documents:
- **Backend Architecture & SPIs:** Read [Document 17](file:///c:/dev/aegis/docs/17-backend-design.md).
- **API Contracts & Event Schemas:** Read [Document 06](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md).
- **PostgreSQL Schemas & Data Model:** Read [Document 07](file:///c:/dev/aegis/docs/07-data-model.md).
- **Threat Model & Security Controls:** Read [Document 08](file:///c:/dev/aegis/docs/08-threat-model.md).
- **Testing & Concurrency Strategies:** Read [Document 11](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md).
- **Frontend Architecture & Design Tokens:** Read [Document 10](file:///c:/dev/aegis/docs/10-design-system-and-ux.md) and [Document 18](file:///c:/dev/aegis/docs/18-frontend-architecture.md).
- **Identity, Credentials & Sessions:** Read [Document 19](file:///c:/dev/aegis/docs/19-identity-and-auth-spec.md).
- **Build Sequence & Task Acceptance Criteria:** Read [Document 20](file:///c:/dev/aegis/docs/20-build-backlog.md).
- **Local Dev Environment & Resource Budgets:** Read [Document 21](file:///c:/dev/aegis/docs/21-local-dev-environment.md).

### Law 2: Protected Documents (Never Edit Without an Accepted ADR)
The following documents represent fundamental architectural contracts. **AI coding agents are strictly forbidden from modifying these files unless an explicit, accepted Architecture Decision Record (ADR) in `docs/adr/` commands the change:**
1. `docs/06-api-and-event-schemas.md` (External API and event payload contracts)
2. `docs/07-data-model.md` (PostgreSQL schemas, partitioning, and RLS policies)
3. `docs/09-rule-dsl-spec.md` (CEL DSL dialect, variables, and AST limits)

*If you believe an API endpoint, database schema, or rule variable must change, you must propose an ADR first, get human approval, and only then update the specification.*

---

## 2. Human-Only Review Zones (High-Risk Code)

The following areas are designated as **Human-Review Zones**. An AI coding agent may author draft code or proposed edits in these zones, but the commit or pull request must be explicitly flagged for human inspection before merging:

1. **Authentication and Identity (`aegis-identity`):**
   - JWT token signing, verification, and claims serialization.
   - Argon2id password hashing parameters and salt generation.
   - TOTP secret generation, AES-256-GCM encryption, and RFC 6238 window validation.
   - Session lifecycle, refresh token rotation, and invalidation logic.
2. **Cryptography and Audit Sealing (`aegis-audit`):**
   - Merkle tree leaf and root hashing algorithms (`AuditSealer`).
   - Constant-time secret comparison methods (`MessageDigest.isEqual`).
   - Request signature generation and validation (HMAC-SHA256).
3. **Atomic Velocity Scripts (`aegis-velocity`):**
   - The Redis Lua sliding window script (`velocity_sliding_window.lua`).
   - Any modification to sorted set score calculation, member deduplication, or window eviction.
4. **Database Migrations (`V*__*.sql`):**
   - Any Flyway SQL migration script.
   - Row-Level Security (`ALTER TABLE ... ENABLE ROW LEVEL SECURITY`) policies and tenant tenant-isolation triggers.

---

## 3. Engineering Constraints and Behavioral Rules

### 3.1 Test-First Development (TDD)
- No feature or bug fix is complete without automated tests.
- When fixing a reported bug, the agent must first write a failing test reproducing the issue, assert the failure, and then apply the minimal fix to turn the test green.
- **Never Mock Backing Services for Velocity or Persistence:**
  - Tests exercising PostgreSQL, Redis Lua, or Kafka must use **Testcontainers** (`PostgresIntegrationTest`, `RedisIntegrationTest`, `KafkaIntegrationTest`).
  - Never mock Redis responses for Lua script execution. The script must execute against a real Redis instance in Docker.

### 3.2 Strict Determinism in `aegis-core`
- The core decision engine must remain pure and 100% deterministic.
- **Forbidden Calls:** Never call `System.currentTimeMillis()`, `Instant.now()`, `new Date()`, or `new Random()` within `aegis-core`.
- **Mandatory Injection:** All temporal operations must resolve through the `com.aegis.core.EventClock` SPI passed in the evaluation context.

### 3.3 Zero PAN Invariant
- Project Aegis never handles or stores Primary Account Numbers (PANs).
- All card numbers must be card network tokens (`tok_...`).
- Strictly adhere to schema typing: card tokens must never be mapped to numeric or integer fields. Free-text inputs must pass through the Luhn-scanning validation filter.

### 3.4 Language and Claims Discipline
- Never write "compliant"; always write **"designed to align with"**.
- Never state a numeric performance result (latency, TPS, memory) unless it has been directly measured on documented hardware using a recorded benchmark harness.
- In documentation and comments, label all untested assumptions explicitly with `[assumption]` or reference the [Assumptions Register](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md).

---

## 4. Git Commit and Pull Request Conventions

All commits by coding agents must strictly adhere to the **Conventional Commits** specification:

```
<type>(<scope>): <short description in imperative present tense> [TSK-xxx]

[Optional detailed body explaining the non-obvious rationale, architectural
trade-offs, or references to relevant documentation sections]

[Optional issue reference / footer]
```

### 4.1 Allowed Commit Types
- `feat`: A new user-facing or API feature.
- `fix`: A bug fix.
- `docs`: Documentation updates or additions only.
- `test`: Adding missing tests or refactoring test suites.
- `refactor`: A code change that neither fixes a bug nor adds a feature.
- `perf`: A code change that improves performance without modifying behavior.
- `chore`: Changes to build scripts, CI pipelines, or auxiliary tools.

### 4.2 Commit Examples
- `feat(decision-api): implement POST /v1/decisions vertical slice [TSK-004]`
- `test(velocity): add 50-thread concurrent limit bypass test [TSK-014]`
- `fix(core): inject EventClock SPI into CEL rule evaluation context [TSK-009]`
- `docs(gtm): outline design partner terms and bilateral DPA [TSK-046]`
