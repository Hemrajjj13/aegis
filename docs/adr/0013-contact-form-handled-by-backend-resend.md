# ADR-0013: Contact Form Handled by Backend via Resend

**Status:** Accepted  
**Date:** 2026-10-30  
**Context:** Public marketing website (`website/`) requires a working contact inquiry form without compromising its architectural invariants (zero client-side JavaScript, zero cookies, zero third-party client trackers/scripts).

---

## Context

The Aegis marketing site is built as a static Astro site with strict zero-JS and zero third-party request constraints. Integrating client-side form submission libraries, external hosted forms (Formspree, HubSpot), or client-side CAPTCHAs would violate these invariants and leak visitor IP addresses to third parties.

At the same time, receiving sales, pricing, and security disclosure inquiries requires reliable transactional email delivery to the engineering team.

---

## Decision

1. **Native HTML Form Submission:** The contact form on `website/src/pages/contact.astro` uses standard HTML `<form method="POST" action="{PUBLIC_API_URL}/v1/public/contact">` with `enctype="application/x-www-form-urlencoded"`. No client-side JavaScript is executed.
2. **Backend Gateway Endpoint:** A public endpoint `POST /v1/public/contact` is hosted within the Spring Boot backend (`aegis-identity` module).
   - Exempt from `TenantHeaderFilter` and Spring Security authentication.
   - Enforces IP-based rate limiting (5 submissions per hour per IP) via `RateLimiter`.
   - Strips CR (`\r`) and LF (`\n`) characters from header fields to eliminate email header injection.
   - Escapes HTML entities in inbound inquiry content to prevent email client HTML injection.
   - Includes a visually hidden honeypot input (`_hp_company`); if populated by a bot, the submission returns HTTP 303 to `/contact/thanks` without dispatching an email.
3. **Pluggable Notification Port:**
   - Dispatches via `NotificationService.sendContactMessage(...)`.
   - **Production / Tier 2 (`@Profile({"prod", "tier2"})`):** Uses `ResendEmailSender`, calling the Resend REST API (`POST https://api.resend.com/emails`) via JDK `HttpClient` with a single retry on 5xx status codes and reply-to set to the visitor's email.
   - **Local Dev / Tier 0 (`@Profile({"tier0", "tier1", "default", "test"})`):** Uses `LocalMailboxNotificationService`, recording the inquiry into an in-memory deque accessible at `GET /dev/mailbox`. No outbound emails are sent.
4. **Dev Mailbox Production Isolation:**
   - `/dev/mailbox` is strictly unregistered under `prod` profile, returning HTTP 404.
   - Frontend console dynamically imports `DevMailboxPage` under `import.meta.env.DEV`, ensuring it is completely tree-shaken in production builds with zero route traces.

---

## Consequences

- The marketing site remains 100% static, cookie-free, and zero-JS.
- Inbound inquiries are validated and rate-limited server-side before email dispatch.
- Resend API keys are isolated exclusively to server-side backend environments and never exposed to client browsers.
- Local developer workflow remains entirely self-contained without requiring active Resend credentials.

