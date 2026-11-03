# Project Aegis: Repository Status & Milestone Audit

**Audit Date:** November 3, 2026  
**Auditor:** Automated Codebase & Test Inspection Harness  
**Repository Working Directory:** `c:\dev\aegis`  
**Current Checked-Out Branch:** `feature/auth-and-site` (commit `6598636`)  
**Main Branch:** `main` (commit `f7e45e5`, 7 commits behind)

---

## 1. Verdict

**Project Aegis is currently an in-progress partial build and technical prototype, not a production-ready MVP.**

While the initial vertical slice (Milestone M1: HTTP transaction ingestion, PostgreSQL Row-Level Security persistence, atomic idempotency deduplication, and live Server-Sent Events broadcasting) and the identity/authentication subsystem (Milestone M8: signup, mobile OTP, TOTP 2FA, recovery codes, refresh token rotation, invite lifecycles, dev mailbox, and static marketing site) are implemented, tested, and working on `feature/auth-and-site`, **the decision engine itself is a static stub**. Transactions are evaluated using a hardcoded two-branch check (`amount >= 50,000 INR -> REVIEW, else APPROVE`). The CEL rule engine, atomic sliding-window velocity counters, Merkle tree cryptographic audit sealing, deterministic replay engine, and rule authoring UI are unbuilt stub modules. Furthermore, the completed identity, authentication, and marketing website work has **not been merged into `main`**.

---

## 2. Feature Implementation Status

The table below reflects the exact state of each feature based on actual code inspection across both `main` and `feature/auth-and-site`.

| Feature | Status | Evidence (File or Endpoint) | Notes |
| :--- | :--- | :--- | :--- |
| **Signup with Mobile OTP** | **Done** *(on `feature`)* | `backend/aegis-identity/.../AuthController.java` (`POST /v1/auth/signup/initiate`, `POST /v1/auth/signup/verify-otp`) | Enforces 12-char min password, unique email/mobile checks, 6-digit OTP hashed via SHA-256 with 5m expiry, 60s cooldown, rate-limited to 3 attempts/hour per IP. |
| **TOTP Setup & Verification** | **Done** *(on `feature`)* | `backend/aegis-identity/.../TotpService.java`, `AuthController.java` (`POST /v1/auth/totp/setup`, `POST /v1/auth/totp/verify`) | Base32 secret generation, RFC 6238 time-step verification, AES-256-GCM encryption at rest in `aegis_auth.user_totp_secrets`. |
| **Recovery Codes** | **Done** *(on `feature`)* | `TotpService.java` (`generateRecoveryCodes()`), `AuthController.java` | Issues 8 alphanumeric 10-character codes on TOTP confirmation, stored Argon2id-hashed in database, single-use burn logic. |
| **Primary Login & MFA Challenge** | **Done** *(on `feature`)* | `AuthController.java` (`POST /v1/auth/login`, `POST /v1/auth/totp/challenge`) | Primary auth returns `mfa_required=true` with a 5m scoped challenge token; TOTP challenge returns 15m RS256 Bearer JWT and sets `jwt_refresh_token` HTTP-only cookie. |
| **Refresh Token Rotation** | **Done** *(on `feature`)* | `AuthController.java` (`POST /v1/auth/refresh`), `SessionRepository.java` | Rotates refresh token on each invocation, invalidates old token family on reuse detection, requires CSRF header (`X-Aegis-CSRF` or `X-Requested-With`). |
| **Tenant Invites & Team Members** | **Done** *(on `feature`)* | `TenantController.java` (`POST /v1/tenant/invites`, `GET /v1/tenant/members`), `InviteAcceptPage.tsx` | SHA-256 hashed invite tokens with 7-day expiry, role assignment (`admin`, `risk_manager`, `rule_author`, `risk_analyst`, `auditor`, `developer`). |
| **Password Reset** | **Done** *(on `feature`)* | `AuthController.java` (`POST /v1/auth/password/forgot`, `POST /v1/auth/password/reset`) | Sends 6-digit OTP to mobile; timing-equalized generic response on initiate to prevent user enumeration. |
| **Role-Based Access Control (RBAC)** | **Done** *(on `feature`)* | `backend/aegis-identity/.../SecurityConfig.java`, `frontend/src/routes/RoleGuard.tsx` | Enforces 7 canonical roles with Spring Security authority checks on API endpoints and React route guards in console. |
| **Decision API (`POST /v1/decisions`)** | **Done** | `backend/aegis-decision-api/.../DecisionController.java` | Ingests transaction events, runs Luhn PAN scanner on merchant/custom fields, executes evaluation core, writes to partitioned table with RLS context. |
| **Atomic Idempotency Deduplication** | **Done** | `backend/aegis-storage/.../JdbcDecisionPersistenceService.java` | Atomic write across `decisions`, `processed_events`, and `outbox` in single transaction; identical payloads return cached decision; mismatched duplicate event ID throws exception. |
| **Live Decision Stream (SSE)** | **Done** | `backend/aegis-decision-api/.../EventStreamController.java` (`GET /v1/events/stream`), `DecisionStreamService.java` | Streams `decision.evaluated` events in real time to connected console clients; suppresses duplicates. |
| **Velocity Counters** | **Not started** *(Stub)* | `backend/aegis-velocity/` | Submodule skeleton exists. No Redis Lua sliding-window scripts or atomic counter logic implemented in application flow. |
| **Rules Engine (CEL)** | **Not started** *(Stub)* | `backend/aegis-rules/` | Submodule skeleton exists. No CEL parser, AST complexity validator, or dynamic evaluator implemented. |
| **Rule Authoring UI** | **Placeholder** | `frontend/src/pages/NotAvailableYetPage.tsx` (`/rules`) | Route displays "CEL Rule Authoring & Simulation - Coming soon" placeholder card. |
| **Backtesting & Deterministic Replay** | **Not started** *(Stub)* | `backend/aegis-replay/` | Submodule skeleton exists. No event-time replay store or differential version comparator implemented. |
| **Manual Review Case Queue** | **Placeholder** | `frontend/src/pages/NotAvailableYetPage.tsx` (`/cases`) | Route displays "Manual Review Case Queue - Coming soon" placeholder card. |
| **Cryptographic Audit Ledger & Sealing**| **Not started** *(Stub)* | `backend/aegis-audit/` | Submodule skeleton exists. Decisions are marked `sealed: false`; Merkle tree leaf hashing and periodic sealing daemon are unbuilt. |
| **Tenant Settings & Governance UI** | **Placeholder** | `frontend/src/pages/NotAvailableYetPage.tsx` (`/settings`) | Route displays "Tenant Administration & Governance - Coming soon" placeholder card. |
| **Public Marketing Website** | **Done** *(on `feature`)* | `website/src/pages/` (Astro static) | Multi-page static site (`/`, `/features`, `/how-it-works`, `/security`, `/pricing`, `/docs`, `/about`, `/contact`, `/privacy`, `/terms`). Zero JS, zero tracking cookies. |
| **Contact Form via Backend Resend** | **Done** *(on `feature`)* | `ContactController.java` (`POST /v1/contact`), `ResendEmailSender.java` | Form posts to backend API; validated with rate-limiting; dispatches via Resend transactional email API in production or dev mailbox in local dev. |
| **Dev Mailbox UI** | **Done** *(on `feature`)* | `DevMailboxController.java` (`GET /dev/mailbox`), `DevMailboxPage.tsx` (`/dev/mailbox`) | Captures OTPs and invites in memory for local testing; disabled and forbidden under `prod` profile. |

---

## 3. Milestones Mapping (Backlog Doc 20 vs. Code Reality)

| Milestone | Target Scope | Code Status | Completed? |
| :--- | :--- | :--- | :--- |
| **M0** | Scaffolding, build topology, Gradle multi-module root, memory budgets | Multi-module Gradle build compiles cleanly; Docker compose topology defined. | **Complete** |
| **M1** | Vertical slice: `POST /v1/decisions`, Postgres RLS persistence, live SSE stream, React decisions table | Full end-to-end slice working with sub-millisecond evaluation on stub logic. | **Complete** |
| **M2** | CEL rule engine, AST complexity validator ($\le 250$ nodes), rule compiler | `aegis-rules` is an empty skeleton. Rule execution uses `StubEvaluationCore`. | **Not Started** |
| **M3** | Atomic Redis Lua sliding-window velocity counters, pipeline caching | `aegis-velocity` is an empty skeleton. Redis container defined but unused in app. | **Not Started** |
| **M4** | Cryptographic audit ledger, Merkle tree sealing (`AuditSealer`), tamper verification | `aegis-audit` is an empty skeleton. Migration V5 creates table; no sealer daemon. | **Not Started** |
| **M5** | Deterministic event-time replay engine (`InMemoryReplayVelocityStore`) | `aegis-replay` is an empty skeleton. No replay engine implemented. | **Not Started** |
| **M6** | Streaming CSV/JSONL dataset importer without heap exhaustion | `aegis-dataset-importer` is an empty skeleton. | **Not Started** |
| **M7** | Full console UI: rule editor, case management queue, audit verification | Decisions feed virtualized table is done; all other operational views are placeholders. | **Partial** |
| **M8** | Identity subsystem, credentials, TOTP 2FA, session lifecycle, RBAC | Auth backend, migrations V5/V9, token security, and console auth UI implemented on `feature/auth-and-site`. | **Complete** *(Unmerged)* |
| **M9** | Pilot readiness, load verification (1,000 req/s), SOC2/PCI gap audit | Not begun. | **Not Started** |

---

## 4. What the Decision Engine Really Does Today

The decision engine currently executes inside [`StubEvaluationCore.java`](file:///c:/dev/aegis/backend/aegis-decision-api/src/main/java/com/aegis/decision/service/StubEvaluationCore.java):

```java
public class StubEvaluationCore implements EvaluationCore {
    public static final double REVIEW_THRESHOLD_INR = 50000.0;

    @Override
    public EvaluationResult evaluate(PaymentEvent event, EventClock clock) {
        double amountInr = event.getAmountInr();

        if (amountInr >= REVIEW_THRESHOLD_INR) {
            outcome = DecisionOutcome.REVIEW;
            score = 75.0;
            rulesFired = List.of(new RuleOutcome(
                    "STUB-HIGH-AMOUNT",
                    "Transaction amount exceeds 50,000 INR threshold",
                    75.0,
                    "AMOUNT_GE_50K"
            ));
        } else {
            outcome = DecisionOutcome.APPROVE;
            score = 15.0;
            rulesFired = List.of();
        }
        ...
    }
}
```

### Reality Breakdown:
1. **Source of Rules:** 100% hardcoded in Java code. Rules do **not** come from a database, configuration file, or CEL expression.
2. **Tenant Modifiability:** Tenants **cannot** change rules, add thresholds, or customize scoring without modifying the Java source code and recompiling the application.
3. **Outcomes Emitted:** The engine only emits `APPROVE` or `REVIEW`. It **never emits `DECLINE`** under any transaction payload today.
4. **Active Validation Checks:**
   - Schema field constraints on `event_id`, `event_time`, `amount_minor_units`, `currency`.
   - Secondary PAN scan: `LuhnPanScanner` inspects `merchant_name` and all `custom_fields.*` entries. If a contiguous 13–19 digit Luhn-valid card number is found, it immediately throws `RawPanProhibitedException`, returning HTTP `400 Bad Request` with `urn:problem:pan-prohibited`.
   - Multi-tenant boundary: If the payload's `tenant_id` does not match the active session / `X-Tenant-Id` header, the controller silently overrides it to the authenticated tenant.

---

## 5. Test and Build Results

### 5.1 Backend Gradle Test Suite
- **Command:** `.\backend\gradlew.bat -p backend test`
- **Total Tests:** 28 completed
- **Passed:** 27
- **Failed:** 1
- **Failure Detail:** `com.aegis.storage.test.ContainerBootstrapTest > testContainersBootstrapHarness` failed because Docker daemon was not installed/running on the Windows host. Setting `-DAEGIS_ALLOW_NO_DOCKER=true` skips this Docker check and allows all remaining 7 database tests in `aegis-storage` to execute and pass against Zonky Embedded PostgreSQL.
- **Module Breakdown:**
  - `aegis-app`: 1 passed, 0 failed
  - `aegis-core`: 3 passed, 0 failed
  - `aegis-decision-api`: 16 passed, 0 failed
  - `aegis-storage`: 7 passed, 1 failed (Docker requirement)
  - `aegis-identity` *(when evaluated on feature branch)*: 10 test suites, 32 passed, 0 failed

### 5.2 Frontend Console Test Suite
- **Command:** `npm --prefix frontend test` (Vitest v3.2.7)
- **Test Files:** 3 passed (3)
- **Total Tests:** 15 passed (15), 0 failed
- **Duration:** 33.12s
- **Suites:** `DecisionBadge.test.tsx` (5 tests), `decisionReducer.test.ts` (7 tests), `ringBuffer.test.ts` (3 tests). *(Additional auth test suite `authMiddlewareAndState.test.ts` passes on feature branch).*

### 5.3 Frontend Console Build
- **Command:** `npm --prefix frontend run build` (`tsc && vite build`)
- **Status:** **SUCCESS** (Exit Code 0)
- **Output:** Built 1,611 modules into `frontend/dist/` in 6.40 seconds (`index.html` 1.09 kB, JS bundle 221.48 kB).

### 5.4 Backend Assembly Build
- **Command:** `.\backend\gradlew.bat -p backend assemble -x test`
- **Status:** **SUCCESS** (Exit Code 0)
- **Actionable Tasks:** 19 tasks (3 executed, 16 up-to-date) in 27s. Executable Spring Boot JAR produced at `backend/aegis-app/build/libs/aegis-app-0.1.0-SNAPSHOT.jar`.

---

## 6. Known Gaps and Technical Risks

1. **Idempotency Conflict Returns 500 Instead of 409:**
   In `JdbcDecisionPersistenceService.java` (line 67), when a duplicate `event_id` is submitted with a different payload hash, it throws `IllegalStateException("Payload hash mismatch for duplicate event_id: ...")`. In `GlobalExceptionHandler.java`, `IllegalStateException` is not mapped to HTTP 409 Conflict; Spring Boot handles it as an unhandled exception and returns HTTP `500 Internal Server Error`.
2. **`dev-up.ps1` Health Check Loop Failure with JWT Auth:**
   In `scripts/dev-up.ps1` (lines 115-124), the startup loop polls `POST /v1/decisions` expecting HTTP 200 or 400. Once `JwtAuthenticationFilter` is present, unauthenticated requests return HTTP 401 Unauthorized, causing `dev-up.ps1` to loop 40 seconds and throw a startup timeout error even though the application is healthy. The health probe should poll `GET /actuator/health`.
3. **Hardcoded Secure Cookie Flag in `AuthController.java`:**
   In `backend/aegis-identity/.../AuthController.java`, the refresh token cookie builder contains `.secure(false)`. This must be bound to `@Value("${aegis.auth.cookie.secure:true}")` before HTTPS deployment so modern browsers do not reject the cookie or expose it over plain HTTP.
4. **`LuhnPanScanner` Regex Word Boundary Edge Case:**
   `LuhnPanScanner.java` compiles `\b(\d{13,19})\b`. If an alphanumeric prefix immediately precedes the digits without whitespace or hyphen (e.g. `Store4111111111111111`), the regex word boundary `\b` fails to match the candidate string.
5. **No Free SMS Gateway for Indian Mobile OTPs:**
   `AuthController.java` dispatches SMS OTPs on user signup. In India, TRAI mandates commercial DLT registration (~₹5,900 one-time fee + SMS charges). No free SMS API exists. The application must fall back to email OTP via Resend when SMS is unconfigured.
6. **TODO and FIXME Audit:**
   No `TODO` or `FIXME` comments exist in backend Java or frontend TypeScript source code. Only `website/CONTENT-TODO.md` exists on the feature branch, documenting marketing copy placeholders (`siteConfig.legalName`, `siteConfig.contactEmail`, `siteConfig.about.founderName`).

---

## 7. Git Repository State

| Property | State | Notes |
| :--- | :--- | :--- |
| **Current Branch** | `feature/auth-and-site` | Head at `6598636` (`origin/feature/auth-and-site`). |
| **Main Branch** | `main` | Head at `f7e45e5` (`origin/main`). |
| **Branch Merge Status** | **NOT MERGED** | `feature/auth-and-site` is 7 commits ahead of `main`. Core identity, auth UI, migrations V5/V9, and website sources exist only on the feature branch. |
| **Remote Configured** | `origin` | `https://github.com/Hemrajjj13/aegis.git` (fetch & push). |
| **Uncommitted / Untracked Files** | Present | `STATUS.md`, `USAGE.md`, `scripts/sample-transactions.json`, `scripts/send-sample-transactions.ps1`, modified `README.md`. |
| **Tracked / Unignored `.env` & `.astro` Risk** | **Critical Gitignore Gap on `main`** | On branch `main`, `.gitignore` **does not include** `.env`, `.dev-keys/`, or `.astro/`! Real local `.env` files containing DB credentials and generated cryptographic keys in `.dev-keys/` are currently untracked and unignored on `main`. *(This is fixed in `.gitignore` on `feature/auth-and-site`).* |

---

## 8. Launch Readiness Checklist (Ordered by Priority)

- [ ] **P0: Merge `feature/auth-and-site` into `main`**  
  Bring identity, authentication, migrations V5/V9, marketing site, deployment configurations, and updated `.gitignore` into the primary branch.
- [ ] **P0: Add `.env` and `.dev-keys` to root `.gitignore`**  
  Prevent accidental commit of real secrets, signing keys, and environment files.
- [ ] **P0: Fix `AuthController.java` Secure Cookie Flag**  
  Ensure refresh cookie sets `.secure(true)` under production HTTPS.
- [ ] **P0: Add Email OTP Fallback for Signup**  
  Allow user signup verification via Resend email OTP when SMS provider credentials are absent.
- [ ] **P1: Map `IllegalStateException` to HTTP 409 in `GlobalExceptionHandler.java`**  
  Return standard RFC 7807 problem detail with status 409 Conflict when duplicate event IDs have payload hash mismatches.
- [ ] **P1: Update `scripts/dev-up.ps1` Health Probe**  
  Change health check URI to `GET http://127.0.0.1:8080/actuator/health` to avoid 401 authentication rejection.
- [ ] **P2: Replace Hardcoded `StubEvaluationCore` with CEL Rule Engine (Milestone M2)**  
  Implement CEL compilation and evaluation so tenants can configure rules without code changes.
- [ ] **P2: Implement Redis Velocity Counters (Milestone M3)**  
  Add sliding-window velocity aggregation script to evaluate transaction frequency rules.
- [ ] **P3: Replace Console Placeholder Views with Real Implementations**  
  Build out `/rules`, `/cases`, `/replay`, `/audit`, and `/settings` console pages.
