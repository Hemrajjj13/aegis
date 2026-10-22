# Identity, Authentication, and Session Specification

**Document:** 19  
**Status:** Approved (docs v1.0, assumption-based)
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

---

## 1. Purpose and System Context

This specification defines the complete identity lifecycle, multi-tenant authentication protocols, credential management, multi-factor verification, and session governance for Project Aegis.

In accordance with Decision [D4](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) and [ADR-0007](file:///c:/dev/aegis/docs/adr/0007-identity-behind-a-port.md), identity in Project Aegis is decoupled behind an `IdentityProvider` port (SPI). 
- **Tier 0 and Tier 1:** Handled by `InProcessSpringSecurityIdentityProvider` embedded in `aegis-identity`, implementing Argon2id password hashing, Time-based One-Time Password (TOTP) verification, mobile/email OTP dispatch, and JWT issuance without external identity broker dependencies.
- **Tier 2:** Pluggable adapter `KeycloakOidcIdentityProvider` implementing OpenID Connect (OIDC), SAML 2.0 federation, and SCIM 2.0 directory synchronisation.

### 1.1 Non-Negotiable Security Invariants
1. **Never Hand-Roll Cryptography:** Uses vetted standards: BouncyCastle for Argon2id (`v1.78+`), Google Authenticator library for RFC 6238 TOTP, and Nimbus-Jose-JWT for RFC 7519 tokens.
2. **Timing-Safe Evaluation:** All secret, OTP, and token comparisons use constant-time comparison (`MessageDigest.isEqual`).
3. **No Account Enumeration:** All auth endpoints return identical generic timing-equalized messages (`"If the account exists, a verification code has been dispatched"`).
4. **Zero Unsalted / Plaintext Credentials:** Passwords use Argon2id with unique 16-byte random salts. OTP codes and invitation tokens are stored strictly as SHA-256 hashes in PostgreSQL.

---

## 2. Relational Database Schema (`V9__init_auth_and_sessions.sql`)

All identity state is persisted in PostgreSQL within tenant schemas or the shared control schema (`aegis_auth`), strictly isolated via Row-Level Security (RLS) per [Document 07 §2.1](file:///c:/dev/aegis/docs/07-data-model.md).

```sql
-- PostgreSQL Schema Definition: aegis_auth
CREATE SCHEMA IF NOT EXISTS aegis_auth;

-- 1. Users Table
CREATE TABLE aegis_auth.users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    mobile VARCHAR(32) NOT NULL,
    full_name VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL, -- 'owner', 'admin', 'risk_manager', 'risk_analyst', 'auditor', 'developer'
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'PENDING_ONBOARDING', 'ACTIVE', 'LOCKED', 'DEACTIVATED'
    totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    lockout_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT uq_tenant_mobile UNIQUE (tenant_id, mobile)
);

-- 2. Credentials Table (Argon2id Hashes)
CREATE TABLE aegis_auth.user_credentials (
    user_id UUID PRIMARY KEY REFERENCES aegis_auth.users(user_id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL, -- Argon2id encoded string: $argon2id$v=19$m=65536,t=3,p=4$...
    password_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE
);

-- 3. TOTP MFA Secrets Table
CREATE TABLE aegis_auth.user_totp_secrets (
    user_id UUID PRIMARY KEY REFERENCES aegis_auth.users(user_id) ON DELETE CASCADE,
    secret_key_encrypted BYTEA NOT NULL, -- AES-256-GCM encrypted RFC 6238 Base32 secret
    initialization_vector BYTEA NOT NULL,
    recovery_codes_hash TEXT[] NOT NULL, -- Array of 10 SHA-256 hashed 8-character recovery codes
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. User Invites Table
CREATE TABLE aegis_auth.user_invites (
    invite_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    invited_by_user_id UUID NOT NULL REFERENCES aegis_auth.users(user_id),
    token_hash VARCHAR(64) NOT NULL UNIQUE, -- SHA-256 hash of single-use URL token
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED'
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at TIMESTAMPTZ
);

-- 5. Verification OTPs Table (Mobile Onboarding & Password Reset)
CREATE TABLE aegis_auth.auth_otp_codes (
    otp_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    recipient_identifier VARCHAR(255) NOT NULL, -- Normalized E.164 phone or lowercase email
    otp_purpose VARCHAR(32) NOT NULL, -- 'SIGNUP_MOBILE_VERIFY', 'PASSWORD_RESET_MOBILE', 'PASSWORD_RESET_EMAIL'
    otp_hash VARCHAR(64) NOT NULL, -- SHA-256 hash of 6-digit numeric code
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. User Sessions Table (Refresh Token Tracking & Revocation)
CREATE TABLE aegis_auth.user_sessions (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES aegis_auth.users(user_id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE, -- SHA-256 hash of opaque rotating refresh token
    ip_address INET NOT NULL,
    user_agent TEXT NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_tenant_role ON aegis_auth.users(tenant_id, role);
CREATE INDEX idx_sessions_user_active ON aegis_auth.user_sessions(user_id) WHERE is_revoked = FALSE;
CREATE INDEX idx_otp_lookup ON aegis_auth.auth_otp_codes(recipient_identifier, otp_purpose) WHERE consumed_at IS NULL;
```

---

## 3. Core Authentication State Machines and API Flows

### 3.1 Flow 1: Owner-Only Signup with Mobile OTP

Project Aegis does not support public open registration. A new tenant can only be instantiated through an explicit Organization Owner registration flow.

```
┌─────────────────┐       POST /v1/auth/signup/initiate        ┌──────────────────────┐
│                 ├───────────────────────────────────────────►│                      │
│ Unauthenticated │◄───────────────────────────────────────────┤ OTP_PENDING (Mobile) │
│                 │   200 OK + { challenge_id, cooldown: 60s } │                      │
└─────────────────┘                                            └──────────┬───────────┘
                                                                          │ POST /v1/auth/signup/verify-otp
                                                                          ▼
┌─────────────────┐        POST /v1/auth/totp/setup            ┌──────────────────────┐
│  ACTIVE (Owner) │◄───────────────────────────────────────────┤ MFA_SETUP_REQUIRED   │
│                 │       200 OK + { access_token }            │                      │
└─────────────────┘                                            └──────────────────────┘
```

#### Step-by-Step Specification
1. **Initiation (`POST /v1/auth/signup/initiate`):**
   - **Payload:** `{ organization_name, owner_name, email, mobile, password }`
   - **Validation:** 
     - Password validated against NIST SP 800-63B (min 12 chars, checked against HIBP breached passwords list).
     - Mobile normalized to E.164 (e.g., `+919876543210`).
   - **Action:** Generates 6-digit numeric OTP via CSPRNG. Stores SHA-256 hash in `auth_otp_codes` with 5-minute expiry. Dispatches SMS via configured notification provider.
   - **Response:** `200 OK` with `{ challenge_id: "<uuid>", resend_cooldown_seconds: 60 }`.
2. **OTP Verification & Account Creation (`POST /v1/auth/signup/verify-otp`):**
   - **Payload:** `{ challenge_id, otp_code }`
   - **Action:** Validates hash in constant-time. Upon success, creates row in `tenants`, `users` (role `'owner'`), and `user_credentials` (Argon2id hash).
   - **Response:** `200 OK` with temporary setup token `{ setup_token: "<jwt>", state: "MFA_SETUP_REQUIRED" }`.
3. **Mandatory TOTP Enrollment (`POST /v1/auth/totp/setup`):**
   - **Action:** Generates RFC 6238 Base32 secret. Returns `otpauth://` URI and QR image.
   - **Confirmation:** Client submits first 6-digit TOTP code. System encrypts secret with AES-256-GCM, stores in `user_totp_secrets`, and activates tenant owner.

---

### 3.2 Flow 2: Email Invite with Hashed Single-Use Token

Existing tenant administrators invite team members. Public registration into existing tenants is prohibited.

```
┌──────────────┐         POST /v1/tenant/invites (Admin)       ┌──────────────────────┐
│ Admin Action ├──────────────────────────────────────────────►│ PENDING_INVITE       │
└──────────────┘                                               └──────────┬───────────┘
                                                                          │ GET /v1/auth/invite/verify?token=...
                                                                          ▼
┌──────────────┐       POST /v1/auth/invite/accept             ┌──────────────────────┐
│ ACTIVE User  │◄──────────────────────────────────────────────┤ INVITE_VALIDATED     │
└──────────────┘                                               └──────────────────────┘
```

#### Step-by-Step Specification
1. **Invite Creation (`POST /v1/tenant/invites`):**
   - Caller must hold role `'admin'` or `'owner'`.
   - Generates 256-bit cryptographically secure random token ($32$ bytes hex-encoded).
   - Stores `SHA-256(token)` in `user_invites` with a **24-hour expiry**.
   - Sends onboarding link `https://console.aegis.internal/auth/onboarding?token=<token>` via notification provider.
2. **Invite Verification (`GET /v1/auth/invite/verify?token=<raw_token>`):**
   - Backend computes `SHA-256(raw_token)` and looks up unexpired pending invite.
   - Returns `{ valid: true, email: "vikram@example.com", role: "risk_analyst", tenant_name: "Acme" }`.
3. **Invite Completion (`POST /v1/auth/invite/accept`):**
   - **Payload:** `{ token, full_name, mobile, password, totp_code, totp_secret }`
   - Atomically creates `users` and `user_credentials`, sets `user_invites.status = 'ACCEPTED'`, and marks token permanently consumed.

---

### 3.3 Flow 3: Login by Email or Mobile with TOTP Challenge

Aegis enforces a strict two-stage authentication state machine for all interactive console logins:

```
┌──────────────────┐          POST /v1/auth/login              ┌──────────────────────┐
│  UNAUTHENTICATED ├──────────────────────────────────────────►│ MFA_CHALLENGE_ISSUED │
│                  │◄──────────────────────────────────────────┤                      │
│                  │   401 Unauthorized (Invalid credentials)  └──────────┬───────────┘
└──────────────────┘                                                      │ POST /v1/auth/totp/challenge
                                                                          ▼
┌──────────────────┐                                           ┌──────────────────────┐
│  AUTHENTICATED   │◄──────────────────────────────────────────┤ SESSION_ESTABLISHED  │
│  (JWT + Refresh) │       200 OK + Set-Cookie: refresh_token  │                      │
└──────────────────┘                                           └──────────────────────┘
```

#### Step-by-Step Specification
1. **Primary Authentication (`POST /v1/auth/login`):**
   - **Payload:** `{ identifier: "email@domain.com" | "+919876543210", password: "..." }`
   - **Verification:** Checks lockout status (`lockout_until`). Computes Argon2id hash verification.
   - **Failure:** Increments `failed_login_attempts`. If $\ge 5$, sets `lockout_until = NOW() + INTERVAL '30 minutes'`.
   - **Success:** Generates ephemeral 5-minute JWT `mfa_session_token` with claim `{ "mfa_pending": true, "sub": "<user_id>" }`.
   - **Response:** `200 OK` with `{ mfa_required: true, mfa_token: "<mfa_jwt>" }`.
2. **Secondary Verification (`POST /v1/auth/totp/challenge`):**
   - **Headers:** `Authorization: Bearer <mfa_token>`
   - **Payload:** `{ totp_code: "492118" }` (or optional `{ recovery_code: "..." }`)
   - **Verification:** Decrypts TOTP secret from `user_totp_secrets` and verifies code against RFC 6238 window ($\pm 1$ step, 30s period).
   - **Success:** 
     - Resets `failed_login_attempts = 0`.
     - Issues 15-minute Access JWT in JSON response body.
     - Issues rotating 7-day Refresh Token in `httpOnly`, `Secure`, `SameSite=Strict` cookie.
     - Logs successful login event in audit ledger.

---

### 3.4 Flow 4: Forgot Password via Multi-Channel OTP

To defend against SIM-swapping attacks while providing high operational resiliency:

1. **Request (`POST /v1/auth/password/forgot`):**
   - **Payload:** `{ identifier: "<email_or_mobile>" }`
   - **Enumeration Defense:** Regardless of whether the user exists, returns identical response: `200 OK` `{ message: "If the account exists, a verification code has been dispatched." }`.
   - **Action:** Generates 6-digit numeric OTP (expires in 15 minutes, max 3 attempts). Dispatches to registered mobile and/or email.
2. **Reset Execution (`POST /v1/auth/password/reset`):**
   - **Payload:** `{ identifier, otp_code, new_password }`
   - **Action:** Verifies OTP code hash. If user holds role `'owner'` or `'admin'`, requires an additional TOTP confirmation header (`X-Aegis-TOTP`).
   - **Revocation Cascade:** Updates `password_hash` in `user_credentials`. **Immediately revokes all active sessions** in `user_sessions` for that user (`is_revoked = TRUE`).

---

### 3.5 Flow 5: Role-Based Dashboard Redirect

Upon receiving an access token, the frontend reads the signed `role` claim and redirects immediately:

```
[Decoded JWT Claim: role]
       │
       ├──► 'risk_analyst' ──► Redirect /cases       (Triage review queue)
       ├──► 'risk_manager' ──► Redirect /rules       (Rule authoring & promotion diffs)
       ├──► 'auditor'      ──► Redirect /audit       (Immutable ledger inspection)
       ├──► 'developer'    ──► Redirect /decisions   (Live transaction feed & latency)
       └──► 'admin'|'owner'──► Redirect /settings    (Tenant administration & keys)
```

---

## 4. Token, Secret, and OTP Rules

### 4.1 Token Formats and Cryptographic Constraints

| Token Type | Lifespan | Storage Medium | Algorithm | Contents / Claims |
|---|---|---|---|---|
| **Access Token** | **15 minutes** | Client In-Memory (AuthContext) | RS256 / EdDSA | `sub` (UUID), `tenant_id` (UUID), `role`, `permissions` (array), `iss: "aegis-identity"`, `aud: "aegis-console"`, `exp`, `jti`. |
| **Refresh Token** | **7 days** (sliding) | Cookie (`httpOnly`, `Secure`, `SameSite=Strict`) | Opaque CSPRNG ($256$ bits) | SHA-256 hash stored in `user_sessions`. Rotated on every use; old token invalidated immediately. |
| **MFA Challenge Token** | **5 minutes** | Client In-Memory | RS256 | `sub` (UUID), `tenant_id` (UUID), `mfa_pending: true`, `exp`. Restricted strictly to MFA verification endpoints. |
| **Invite Token** | **24 hours** | URL Query Parameter | Opaque CSPRNG ($256$ bits) | SHA-256 hash stored in `user_invites`. Single-use; marked `ACCEPTED` upon account setup. |
| **Verification OTP** | **5 minutes** | SMS / Local Mailbox Log | Numeric 6-digit CSPRNG | SHA-256 hash stored in `auth_otp_codes`. Rate-limited to max 3 invalid submission attempts. |

### 4.2 Argon2id Password Hashing Parameters
Following OWASP Password Storage Guidelines (2024 recommendations):
- **Algorithm:** Argon2id
- **Memory Cost ($m$):** $65,536\text{ KiB}$ ($64\text{ MiB}$)
- **Time Cost / Iterations ($t$):** $3$
- **Parallelism ($p$):** $4$ threads
- **Salt Length:** $16$ cryptographically secure random bytes

---

## 5. Rate Limiting and Brute-Force Defenses

To defend against automated botnet credential stuffing and OTP exhaustion attacks (documented in [Document 08 §4.7](file:///c:/dev/aegis/docs/08-threat-model.md)):

| Endpoint Path | Rate Limit Key | Allowed Request Threshold | Penalty / Action Upon Breach |
|---|---|---|---|
| `POST /v1/auth/login` | IP Address | 10 requests / 1 minute | HTTP 429 Too Many Requests |
| `POST /v1/auth/login` | Identifier (Email/Mobile) | 5 consecutive failures | User status set to `LOCKED`; locked for 30 minutes. |
| `POST /v1/auth/totp/challenge` | User ID | 5 consecutive failures | Session invalidated; user must restart login flow. |
| `POST /v1/auth/signup/initiate`| IP Address | 3 signups / 1 hour | HTTP 429 Too Many Requests |
| `POST /v1/auth/password/forgot`| Identifier | 1 request / 60 seconds | Cooldown enforced; returns cached dispatch status. |
| `POST /v1/auth/password/reset` | Identifier | 3 invalid OTP attempts | OTP permanently invalidated; must request fresh code. |

---

## 6. Developer and Local Environment Providers

In accordance with Decision [D6](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) and [Document 17 §4](file:///c:/dev/aegis/docs/17-backend-design.md), local development (Tier 0) and shadow pilots (Tier 1) must run self-contained without requiring paid third-party SMS or transactional email vendors.

### 6.1 `LocalMailboxNotificationService`
When the Spring profile `tier0` or `tier1` is active, the `NotificationService` interface binds to `LocalMailboxNotificationService`:
- **Behavior:** Instead of invoking an external telephony/email API, generated OTPs and invitation tokens are logged directly to the local application standard output and written to a local memory mailbox:
  ```
  [LOCAL-MAILBOX-DEV] ======================================================
  [LOCAL-MAILBOX-DEV] DISPATCHED OTP TO: +919876543210
  [LOCAL-MAILBOX-DEV] PURPOSE: SIGNUP_MOBILE_VERIFY
  [LOCAL-MAILBOX-DEV] 6-DIGIT CODE: 841902 (Expires in 300 seconds)
  [LOCAL-MAILBOX-DEV] ======================================================
  ```
- **Local Dev API:** Developers can query `GET /v1/dev/mailbox/latest?recipient=+919876543210` in Tier 0 to automatically retrieve the last OTP during automated Cypress/Playwright integration tests. This endpoint is strictly excluded from Tier 2 production builds via conditional Spring bean configuration.

---

## 7. The `IdentityProvider` Port and Enterprise Tier 2 Extension

The identity subsystem is modeled as an architectural port ([ADR-0007](file:///c:/dev/aegis/docs/adr/0007-identity-behind-a-port.md)):

```java
package com.aegis.identity.port;

public interface IdentityProvider {
    AuthResult authenticate(String tenantId, String identifier, String password);
    boolean verifyTotp(UUID userId, String totpCode);
    UserSession createSession(UUID userId, UUID tenantId, String ipAddress, String userAgent);
    UserSession refreshSession(String rawRefreshToken);
    void revokeSession(String rawRefreshToken);
    void initiatePasswordReset(String identifier);
    void completePasswordReset(String identifier, String otpCode, String newPassword);
}
```

### 7.1 Tier 2 Extension: `KeycloakOidcIdentityProvider`
For Tier 2 enterprise customers requiring Corporate Single Sign-On (SSO):
1. The `InProcessSpringSecurityIdentityProvider` is replaced by `KeycloakOidcIdentityProvider` via Spring profile `tier2-sso`.
2. Interactive login redirects to the enterprise identity provider (Okta, Azure AD, Ping Identity) using standard OpenID Connect Authorization Code Flow with PKCE.
3. User role claims (`admin`, `risk_manager`, etc.) are mapped dynamically from enterprise SAML/OIDC token assertions (`groups` or `roles` claim) to Aegis tenant RBAC permissions.
4. SCIM 2.0 endpoints (`/scim/v2/Users`, `/scim/v2/Groups`) enable automated employee onboarding and immediate de-provisioning upon employee termination.
