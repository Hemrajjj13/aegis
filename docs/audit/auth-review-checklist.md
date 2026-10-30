# Aegis Authentication Human-Review Checklist

> **STATUS: DRAFT — PENDING HUMAN REVIEW (DO NOT MARK APPROVED)**  
> **Scope:** Authentication, Cryptography, Session Lifecycle, and Row-Level Security in `aegis-identity` and PostgreSQL migrations.  
> **Authority:** Project Aegis AGENTS.md Section 2 (Human-Only Review Zones), ADR-0007, ADR-0011, and Document 19.  
> **Date:** October 2026  

Per **AGENTS.md Section 2**, AI coding agents may author draft code in designated Human-Review Zones, but the code **must be explicitly flagged for human inspection before merging**. This document provides the exact files, line numbers, and verification checks that a human security reviewer must inspect and approve.

---

## 1. Password Hashing & Timing Equalization

- **File:** [`backend/aegis-identity/src/main/java/com/aegis/identity/crypto/Argon2Service.java`](file:///c:/dev/aegis/backend/aegis-identity/src/main/java/com/aegis/identity/crypto/Argon2Service.java)
- **Lines to Inspect:**
  - Lines 12–15: Argon2id parameter instantiation (`saltLength = 16` bytes, `hashLength = 32` bytes, `parallelism = 4`, `memory = 65536` KiB (64 MiB), `iterations = 3`).
  - Lines 14, 35–40: Dummy hash precomputation and `verifyDummy(String rawPassword)` execution to equalize CPU work on non-existent accounts, preventing timing-based account enumeration.
- **Verification Checklist:**
  - [ ] Memory parameter is exactly 64 MiB ($65536$ KiB) and iterations $= 3$, aligning with OWASP 2024 guidance.
  - [ ] `verifyDummy` runs the full Argon2id computation on failed lookups.

---

## 2. JWT Token Signing, Verification & Algorithm Pinning

- **File:** [`backend/aegis-identity/src/main/java/com/aegis/identity/crypto/JwtService.java`](file:///c:/dev/aegis/backend/aegis-identity/src/main/java/com/aegis/identity/crypto/JwtService.java)
- **Lines to Inspect:**
  - Lines 56–77: Tier-based key loading and startup safety enforcement: tier1/tier2 fail fast if `AEGIS_JWT_PRIVATE_KEY` or `AEGIS_JWT_PUBLIC_KEY` are unset; tier0 generates ephemeral local RSA keys in `.dev-keys/`.
  - Lines 160–183: Token issuance pinning `JWSAlgorithm.RS256`, issuing access tokens with 15-minute validity, MFA challenge tokens with 5-minute validity, and setup tokens with 15-minute validity.
  - Lines 218–268: Token parsing and strict cryptographic verification:
    - Verifies header algorithm is strictly `JWSAlgorithm.RS256` (explicitly rejects `none`, `HS256`, or symmetric confusion).
    - Verifies cryptographic signature using `RSASSAVerifier` and public key.
    - Validates `iss` equals `aegis-identity`, `aud` contains `aegis-console`, and `exp` is in the future.
- **Verification Checklist:**
  - [ ] Rejection of `alg: none` and algorithm switching attacks.
  - [ ] Strict issuer, audience, and expiration verification.
  - [ ] In tier1/tier2, missing keys throw `IllegalStateException` preventing service startup.

---

## 3. TOTP Secret Generation, AES-256-GCM Encryption & RFC 6238

- **File:** [`backend/aegis-identity/src/main/java/com/aegis/identity/crypto/TotpService.java`](file:///c:/dev/aegis/backend/aegis-identity/src/main/java/com/aegis/identity/crypto/TotpService.java)
- **Lines to Inspect:**
  - Lines 27–29: AES-GCM parameters (`GCM_TAG_LENGTH = 128` bits, `GCM_IV_LENGTH = 12` bytes, random IV per encryption).
  - Lines 48–65: Tier-based startup safety enforcement: tier1/tier2 fail fast if `AEGIS_TOTP_ENC_KEY` is unset.
  - Lines 116–141: `encryptSecret` and `decryptSecret` using AES-256-GCM authenticated encryption.
  - Lines 144–157: Recovery codes generation: 10 cryptographically random codes (alphanumeric), hashed with SHA-256 before database storage.
  - Lines 160–166: RFC 6238 validation using GoogleAuthenticator with time step 30s and window size 3 ($\pm 1$ step).
- **Verification Checklist:**
  - [ ] Plaintext Base32 TOTP secrets are NEVER stored unencrypted.
  - [ ] IV is uniquely generated per encryption and prepended or stored alongside ciphertext.
  - [ ] Recovery codes are hashed with SHA-256 before storage; raw recovery codes are never stored.

---

## 4. Session Lifecycle, Refresh Token Rotation & Reuse Detection

- **File:** [`backend/aegis-identity/src/main/java/com/aegis/identity/service/InProcessSpringSecurityIdentityProvider.java`](file:///c:/dev/aegis/backend/aegis-identity/src/main/java/com/aegis/identity/service/InProcessSpringSecurityIdentityProvider.java)
- **Lines to Inspect:**
  - Lines 295–351 (`refreshToken`):
    - Constant-time SHA-256 comparison of incoming refresh token.
    - Sliding window expiry check (7 days max lifetime).
    - **Reuse Detection:** If an already-rotated or revoked session token is presented, all sessions belonging to the user are cascade-revoked immediately.
    - Single-use rotation: existing token is marked rotated and a fresh crypto-random token is issued.
  - Lines 354–370 (`logout`):
    - Session revoked and refresh token cleared.
- **Verification Checklist:**
  - [ ] Single-use rotation on every refresh.
  - [ ] Reuse detection revokes all sessions for that user and logs security alert to `audit_management_events`.
  - [ ] Raw refresh tokens are never persisted in PostgreSQL (stored as SHA-256 hashes).

---

## 5. Constant-Time Comparisons & Rate Limiting

- **Files:**
  - [`backend/aegis-identity/src/main/java/com/aegis/identity/service/InProcessSpringSecurityIdentityProvider.java`](file:///c:/dev/aegis/backend/aegis-identity/src/main/java/com/aegis/identity/service/InProcessSpringSecurityIdentityProvider.java) (Lines 447–451)
  - [`backend/aegis-identity/src/main/java/com/aegis/identity/service/InMemoryRateLimiter.java`](file:///c:/dev/aegis/backend/aegis-identity/src/main/java/com/aegis/identity/service/InMemoryRateLimiter.java)
- **Lines to Inspect:**
  - Constant-time secret equality check: `MessageDigest.isEqual(...)` used for OTPs, recovery codes, and tokens.
  - Rate limiting rules:
    - Login attempts: 5 per 15 minutes per IP + identifier.
    - Account lockout: 5 consecutive failed attempts trigger a 30-minute lockout.
    - OTP attempts: maximum 3 failed attempts invalidates the OTP.
    - Signup: 3 per hour per IP.
- **Verification Checklist:**
  - [ ] Constant-time comparison is strictly applied to prevent timing side-channels.
  - [ ] Account lockout and OTP burn limits match Document 19 Section 5.

---

## 6. HTTP Security Filter Chain & CSRF Defence

- **Files:**
  - [`backend/aegis-identity/src/main/java/com/aegis/identity/security/SecurityConfig.java`](file:///c:/dev/aegis/backend/aegis-identity/src/main/java/com/aegis/identity/security/SecurityConfig.java)
  - [`backend/aegis-identity/src/main/java/com/aegis/identity/security/JwtAuthenticationFilter.java`](file:///c:/dev/aegis/backend/aegis-identity/src/main/java/com/aegis/identity/security/JwtAuthenticationFilter.java)
- **Lines to Inspect:**
  - `SecurityConfig.java` (Lines 35–64): Stateless session management (`SessionCreationPolicy.STATELESS`), CSRF disabled for stateless REST endpoints, public endpoints whitelist (`/v1/auth/signup`, `/v1/auth/login`, `/v1/auth/login/challenge`, `/v1/auth/forgot-password`, `/v1/auth/reset-password`, `/v1/auth/refresh`, `/v1/auth/logout`, `/v1/auth/invite/accept`), all other endpoints require authenticated JWT.
  - `JwtAuthenticationFilter.java` (Lines 47–88):
    - Extracts `Bearer <token>` and verifies signature via `JwtService`.
    - Populates `TenantContextHolder` and Spring Security `Authentication` with tenant ID and assigned user role.
    - Rejects tokens with `mfa_pending = true` on fully authenticated endpoints.
    - Dev-header fallback mode: strictly restricted to `tier0` (`aegis.auth.mode=dev-header`), throws startup error on higher tiers.
  - `AuthController.java` (Lines 185–196):
    - Enforces custom header `X-Aegis-CSRF: 1` on `POST /v1/auth/refresh` to prevent cross-site cookie submission.
    - Refresh token cookie flags: `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/v1/auth`.
- **Verification Checklist:**
  - [ ] Cookie flags meet strict browser isolation standards.
  - [ ] `X-Aegis-CSRF` custom header defense is verified.
  - [ ] Dev-header filter cannot be enabled in production/tier2.

---

## 7. Database Migrations & Row-Level Security (RLS)

- **Files:**
  - [`backend/aegis-storage/src/main/resources/db/migration/V9__init_auth_and_sessions.sql`](file:///c:/dev/aegis/backend/aegis-storage/src/main/resources/db/migration/V9__init_auth_and_sessions.sql)
  - [`backend/aegis-storage/src/main/resources/db/migration/V5__audit_management_events.sql`](file:///c:/dev/aegis/backend/aegis-storage/src/main/resources/db/migration/V5__audit_management_events.sql)
- **Lines to Inspect:**
  - `V9`: Creation of `aegis_auth` schema and tables (`users`, `credentials`, `totp_secrets`, `recovery_codes`, `otps`, `invites`, `sessions`).
  - `V9`: Row-Level Security enabled and enforced (`ALTER TABLE ... ENABLE ROW LEVEL SECURITY; ALTER TABLE ... FORCE ROW LEVEL SECURITY;`).
  - `V9`: RLS policies checking `tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid`.
  - `V9`: Minimal-privilege database role `app_auth_user` with grants restricted to `SELECT` on pre-tenant lookup columns and `NOBYPASSRLS`.
  - `V5`: Creation of `audit_management_events` table with RLS and hash chain columns.
- **Verification Checklist:**
  - [ ] Every multi-tenant table has both `ENABLE ROW LEVEL SECURITY` and `FORCE ROW LEVEL SECURITY`.
  - [ ] Role `app_auth_user` does not possess `BYPASSRLS`.
  - [ ] Unicity constraints on normalized email and E.164 phone numbers.

---

## 8. Frontend Console Authentication & Session State (TSK-040)

- **Files:**
  - [`frontend/src/api/authTokens.ts`](file:///c:/dev/aegis/frontend/src/api/authTokens.ts)
  - [`frontend/src/api/client.ts`](file:///c:/dev/aegis/frontend/src/api/client.ts)
  - [`frontend/src/contexts/AuthContext.tsx`](file:///c:/dev/aegis/frontend/src/contexts/AuthContext.tsx)
  - [`frontend/src/components/auth/TotpQrCode.tsx`](file:///c:/dev/aegis/frontend/src/components/auth/TotpQrCode.tsx)
  - [`frontend/e2e/auth-lifecycle.spec.ts`](file:///c:/dev/aegis/frontend/e2e/auth-lifecycle.spec.ts)
- **Verification Checklist:**
  - [x] Access tokens stored in memory only; `localStorage` and `sessionStorage` never touched.
  - [x] OpenAPI client automatically injects `Authorization: Bearer <token>` and retries once on 401 after silent refresh.
  - [x] Local QR code generation via `qrcode` library; zero external network requests to third-party QR generation services.
  - [x] All authentication error messages are generic to prevent account enumeration.
  - [x] 12/12 Playwright E2E tests passing with 0 serious or critical axe WCAG 2.1 AA violations in light and dark themes.
  - [x] Production bundle size verified: 335.92 kB (102.57 kB gzip).

---

## 9. Human Reviewer Sign-Off Block

> **Note:** This block must only be filled out by a human security reviewer. Coding agents must leave this unsigned.

- **Reviewer Name:** _______________________________________
- **Reviewer Title:** ______________________________________
- **Date of Review:** ______________________________________
- **Status:** [ ] APPROVED   [ ] CHANGES REQUESTED
- **Notes / Observations:**

