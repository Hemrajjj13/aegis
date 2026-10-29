# ADR-0011: Auth Architecture, Tenant Isolation, and Key Lifecycle

**Status:** Accepted  
**Date:** October 2026  
**Deciders:** Founder / Engineering Lead  
**Supersedes:** —  
**Superseded by:** —  

---

## Context

Project Aegis requires an in-house authentication and user identity subsystem for Tier 0 and Tier 1 deployments (per [ADR-0007](file:///c:/dev/aegis/docs/adr/0007-identity-behind-a-port.md)) supporting console operator login, signup, invitation onboarding, TOTP multi-factor authentication, session lifecycle, and password reset flows. Because Aegis is a multi-tenant payment decision engine with strict Row-Level Security (RLS) policies (per [Document 07](file:///c:/dev/aegis/docs/07-data-model.md) and [Document 08](file:///c:/dev/aegis/docs/08-threat-model.md)), authentication introduces critical security boundaries:
1. **Pre-Tenant Identification:** When an unauthenticated user arrives at `/auth/login` or `/auth/signup`, their tenant ID is not yet established in the session or JWT context. Standard tenant RLS policies (`current_setting('app.current_tenant_id')`) would block querying user identity records unless an unauthenticated look-up mechanism is safely provisioned.
2. **Identifier Scope and Uniqueness:** Whether emails and mobile numbers should be tenant-scoped or globally unique in the database.
3. **Cryptographic Key Lifecycle and Storage:** Key rotation, algorithm pinning, dev-key auto-generation, and encrypted TOTP MFA secret persistence.
4. **Machine API Keys vs Console Tokens:** The relationship between machine caller authentication (`POST /v1/decisions`) and interactive console session tokens.
5. **Development Header Bypasses:** Transitioning `X-Tenant-Id` header overrides to a strictly guarded, dev-only configuration.

---

## Decision

The following foundational authentication and tenant isolation decisions govern Project Aegis:

### (a) Global Identity Uniqueness and Normalization
- `email` and `mobile` are globally unique across all tenants in `aegis_auth.users`:
  - Emails are normalized to lower-case (`user@domain.com`).
  - Mobile numbers are normalized to E.164 international format (e.g., `+919876543210`).
- **Single-Tenant Mapping in v1:** Each user belongs to exactly one tenant in v1 (`tenant_id NOT NULL REFERENCES tenants(tenant_id)`). Multi-tenant cross-organization membership is deferred to Stage 2.

### (b) Database Role Separation and RLS Policies for `aegis_auth`
- All tables in schema `aegis_auth` have Row-Level Security enabled (`ENABLE ROW LEVEL SECURITY`) and forced (`FORCE ROW LEVEL SECURITY`).
- A dedicated database role `app_auth_user` is created with strictly minimal privileges (no `BYPASSRLS` attribute).
- For unauthenticated lookups (login and signup):
  - `app_auth_user` is granted `SELECT` on `aegis_auth.users` (for email/mobile resolution) and `SELECT` on `aegis_auth.user_credentials`, `aegis_auth.user_totp_secrets`, and `aegis_auth.auth_otp_codes`.
  - An RLS policy permits `app_auth_user` to resolve the user's tenant ID, after which application queries set `app.current_tenant_id` to establish the tenant boundary.
- Once authenticated, all data access runs under the authenticated tenant context matching the user's verified token claims.

### (c) Cryptographic Key Lifecycle and TOTP Encryption
- **JWT Signing Keys:** Pinned strictly to asymmetric EdDSA (Ed25519) or RS256. Algorithm `none` and algorithm confusion attacks (e.g. HMAC verification with RSA public keys) are explicitly rejected by Nimbus JOSE+JWT configuration.
- **Tier 0 Key Generation:** In Tier 0, the application auto-generates ephemeral RSA 2048-bit key pairs into a gitignored `.dev-keys/` directory on startup if absent.
- **Tier 1 / Tier 2 Refusal:** In Tier 1 and Tier 2, the application refuses to start if signing keys are missing or unconfigured.
- **TOTP Encryption:** RFC 6238 TOTP secrets are encrypted with AES-256-GCM before database insertion. The encryption key is sourced from environment variable `AEGIS_TOTP_ENC_KEY` (a dev 256-bit key is auto-generated in Tier 0 if unset).

### (d) In-Memory Rate Limiting Port
- Authentication rate limiting and lockout enforcement are placed behind a `RateLimiter` port interface.
- In Tier 0 and Tier 1, an in-process memory-bounded token bucket implementation is used (sliding window with LRU eviction). Distributed Redis sliding-window rate limiting is targeted for Tier 2.

### (e) Machine API-Key Scope
- Machine API-key/HMAC signing (`/tenant/keys`) is **out of scope** for this phase.
- `POST /v1/decisions` accepts a valid console user's Bearer access token for interactive and test executions.
- Dedicated high-throughput machine client authentication (mTLS / HMAC-SHA256 API keys) is documented as a recorded gap in [Document 20](file:///c:/dev/aegis/docs/20-build-backlog.md).

### (f) Strict Dev-Header Filter Guard
- The legacy `X-Tenant-Id` header authentication filter is refactored to require explicit configuration property `aegis.auth.mode=dev-header`.
- Default is `off`.
- The application strictly refuses to start if `aegis.auth.mode=dev-header` is enabled under any profile other than `tier0`.

### (g) Flyway Out-of-Order Execution
- Flyway `outOfOrder` is set to `true` exclusively for `tier0` and CI environments to accommodate modular migration sequencing (`V9__init_auth_and_sessions.sql` and `audit_management_events`).

---

## Consequences

### Positive
- Prevents identity spoofing across tenants while allowing seamless login via email or mobile.
- Zero credential plaintext leakage: raw tokens, passwords, and OTPs never touch disk or tables unhashed or unencrypted.
- Clear cryptographic demarcation: dev keys are self-bootstrapping for zero-friction local development while production tiers strictly enforce key injection.

### Negative / Trade-offs
- One tenant per user prevents multi-tenant switching in v1; users requiring access to multiple institutions must register separate email addresses until Stage 2.
- Machine callers must use session bearer tokens until dedicated machine API-key infrastructure ships.

---

## References
- [ADR-0007: Identity Behind a Port](file:///c:/dev/aegis/docs/adr/0007-identity-behind-a-port.md)
- [Document 07: Data Model](file:///c:/dev/aegis/docs/07-data-model.md)
- [Document 08: Threat Model](file:///c:/dev/aegis/docs/08-threat-model.md)
- [Document 19: Identity and Auth Spec](file:///c:/dev/aegis/docs/19-identity-and-auth-spec.md)
- [Document 20: Build Backlog](file:///c:/dev/aegis/docs/20-build-backlog.md)

