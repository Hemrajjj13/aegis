# ADR-0007: Identity Behind a Port (In-House for Tier 0/1, Pluggable SSO for Tier 2)

**Status:** Accepted  
**Date:** October 2026  
**Deciders:** Founder / Engineering Lead  
**Supersedes:** —  
**Superseded by:** —  

---

## Context

Project Aegis requires user authentication, password management, role-based access control (RBAC), multi-factor authentication (TOTP), invitation workflows, and password resets for console operators, risk analysts, and rule authors.

In enterprise software, running an external identity provider like Keycloak or Okta is standard. However:
1. **Resource Burden on Developer Workstations:** Keycloak requires a minimum of 1.5–2.0 GB of heap/container RAM and adds significant startup time (30–60s), which severely violates the Tier 0 RAM envelope of $\le 3.89$ GB (Decision D6).
2. **Operational Overhead for Pilots:** Operating Keycloak in Tier 1 pilot environments introduces additional database instances, clustering configuration, and backup maintenance for an early-stage startup with 1–2 pilot tenants.
3. **Enterprise SSO Demand in Production:** Enterprise tier (Tier 2) banking and fintech customers will eventually require SAML 2.0 or OIDC integration with their corporate identity providers (Azure AD, Okta, Ping Identity).

Tightly coupling application logic to either a specific vendor (Keycloak) or hardcoding custom authentication directly throughout business controllers creates an expensive refactoring liability.

## Decision

Project Aegis places authentication and user identity behind a hexagonal architectural port: the **`IdentityProvider` interface** (Decision D4):

```java
public interface IdentityProvider {
    AuthToken authenticate(String username, String password);
    AuthToken verifyMfa(String mfaSessionToken, String totpCode);
    UserPrincipal validateToken(String bearerToken);
    InviteResult createInvitation(CreateInviteRequest request);
    void resetPassword(PasswordResetRequest request);
    void revokeToken(String tokenId);
}
```

### Tier Implementations

1. **Tier 0 and Tier 1: In-House Spring Security Module (`LocalSpringSecurityIdentityProvider`)**
   - Implemented as a lightweight, in-process Spring Security module directly within `identity-service`.
   - **Credentials & Storage:** Argon2id password hashing stored in PostgreSQL `users` table; TOTP generation and secret verification using standard RFC 6238 CSPRNG algorithms.
   - **Tokens:** Emits short-lived (15-minute) signed JWTs (RS256) with public key validation.
   - **Notification Port:** Pluggable `NotificationService` for email and SMS delivery. In Tier 0, dev providers write tokens and invite links to a local structured mailbox log (`mailbox.log` or memory buffer). In Tier 1, pluggable providers integrate with Amazon SES / SNS or Twilio.
   - **Resource Footprint:** Negligible added memory overhead (~50 MB in-process).

2. **Tier 2: External Enterprise Provider Adapter (`OidcSamlIdentityProvider`)**
   - Implements the identical `IdentityProvider` port by delegating to Keycloak, AWS Cognito, or an enterprise customer's corporate IdP via standard OIDC / SAML 2.0 federation.
   - Activated via Spring profile `identity-oidc`.
   - Supports automated SCIM 2.0 provisioning and centralized enterprise directory sync.

## Consequences

### Positive
- **Tier 0 Lightweight Execution:** Developer laptops run without heavy external identity containers, staying well under the 3.89 GB RAM budget.
- **Self-Contained Pilots:** Pilot customers can be onboarded rapidly with email/SMS invite links without requiring complex enterprise SSO setup before contract validation (Gates G1 and G2).
- **Clean Decoupling:** Console controllers and internal service-to-service security interceptors depend strictly on the `IdentityProvider` interface and standard Spring Security `SecurityContext`.

### Negative
- The in-house module must properly maintain cryptographic safety for token issuance, Argon2id salting, and TOTP verification without introducing custom crypto anti-patterns. Vetted standard libraries (`org.bouncycastle`, `com.warrenstrange.googleauth`, `nimbus-jose-jwt`) will be used.
