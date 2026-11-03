# Project Aegis — Environment Variables & Secrets Reference (`ENV.md`)

This document is the authoritative single source of truth for all environment configuration variables and cryptographic secrets used across Project Aegis.

---

## 1. Prime Rules for Environment Variables & Secrets

1. **Never Commit Real Secrets:** Real `.env` files contain sensitive credentials and must never be committed to Git. Only `.env.example` template files are tracked.
2. **Path Locations:**
   - Backend: `c:\dev\aegis\backend\.env` (or repository root `c:\dev\aegis\.env`)
   - Operator Console: `c:\dev\aegis\frontend\.env`
   - Marketing Website: `c:\dev\aegis\website\.env`
3. **Execution Separation:**
   - Spring Boot backend reads `backend/.env` automatically on startup via `spring.config.import`.
   - Frontend console reads variables prefixed with `VITE_` via Vite during development and build.
   - Website reads variables prefixed with `PUBLIC_` via Astro during static generation.
4. **How to Rotate a Secret:**
   - **TOTP Encryption Key (`AEGIS_TOTP_ENC_KEY`):** Generate a new 32-byte Base64 key, update production environment variables, and re-encrypt stored user secrets via an offline migration script.
   - **JWT Signing Keys (`AEGIS_JWT_PRIVATE_KEY` / `AEGIS_JWT_PUBLIC_KEY`):** Generate a new RSA 2048-bit key pair. Update the private key on token-issuing servers, and distribute the new public key across verifiers before decommissioning the prior public key.
   - **Resend API Key (`RESEND_API_KEY`):** Create a new API key in the Resend console, update the production environment variable, restart backend instances, and revoke the previous key.

---

## 2. Environment File Map

| File Path | Target Subsystem | Committed to Git? | Description |
| :--- | :--- | :---: | :--- |
| `c:\dev\aegis\.env` | Backend (root fallback) | **No** (git-ignored) | Local backend overrides when launching from root |
| `c:\dev\aegis\.env.example` | Backend (root template) | **Yes** | Reference template for root backend configuration |
| `c:\dev\aegis\backend\.env` | Backend (`aegis-app`) | **No** (git-ignored) | Active local configuration for Spring Boot backend |
| `c:\dev\aegis\backend\.env.example` | Backend template | **Yes** | Reference template for backend developers |
| `c:\dev\aegis\frontend\.env` | Console (`frontend`) | **No** (git-ignored) | Active local configuration for Vite React console |
| `c:\dev\aegis\frontend\.env.example` | Console template | **Yes** | Reference template for console developers |
| `c:\dev\aegis\website\.env` | Website (`website`) | **No** (git-ignored) | Active local configuration for Astro marketing site |
| `c:\dev\aegis\website\.env.example` | Website template | **Yes** | Reference template for website developers |

---

## 3. Variable Tables by Service

### 3.1 Backend Configuration (`backend/.env`)

| Variable | Required? | Local Default / Example | Used By (File / Class) | What It Does | Where To Get It |
| :--- | :---: | :--- | :--- | :--- | :--- |
| `SERVER_PORT` | Optional | `8080` | `application.yml` (`server.port`) | HTTP port bound by the Spring Boot server | Developer choice (default: `8080`) |
| `SPRING_PROFILES_ACTIVE` | **Required** | `tier0` | `application.yml` (`spring.profiles.active`) | Selects operational profile: `tier0` (dev), `tier1` (hardened), `tier2`/`prod` (production) | Set to `tier0` locally; `prod` in production |
| `AEGIS_PG_URL` | **Required** | `jdbc:postgresql://127.0.0.1:5432/aegis` | `application.yml`, `PostgresIntegrationTest` | JDBC connection URL for PostgreSQL database | From local PostgreSQL or RDS connection string |
| `AEGIS_PG_USER` | **Required** | `aegis` | `application.yml`, `PostgresIntegrationTest` | PostgreSQL username | From database administrator |
| `AEGIS_PG_PASSWORD` | **Required** | `aegis` | `application.yml`, `PostgresIntegrationTest` | PostgreSQL password (**Secret**) | From database administrator |
| `AEGIS_REDIS_HOST` | Tier 1/2 only | `127.0.0.1` | `application.yml` | Redis host for sliding-window velocity counters | Redis host IP or AWS ElastiCache endpoint |
| `AEGIS_REDIS_PORT` | Tier 1/2 only | `6379` | `application.yml` | Redis port | Redis port (default: `6379`) |
| `AEGIS_KAFKA_BOOTSTRAP_SERVERS` | Tier 1/2 only | `127.0.0.1:9092` | `application.yml` | Kafka broker endpoints for event streaming | Kafka cluster bootstrap string |
| `AEGIS_TOTP_ENC_KEY` | **Required in Prod** | Auto-generated in Tier 0 | `TotpService.java` | 32-byte Base64 key for AES-256-GCM encryption of user TOTP secrets (**Secret**) | Generated via PowerShell script (see §5) |
| `AEGIS_JWT_PRIVATE_KEY` | **Required in Prod** | Auto-generated in Tier 0 (`.dev-keys/`) | `JwtService.java` | PKCS#8 RSA private key for signing user access tokens (**Secret**) | Generated via OpenSSL / PowerShell (see §5) |
| `AEGIS_JWT_PUBLIC_KEY` | **Required in Prod** | Auto-generated in Tier 0 (`.dev-keys/`) | `JwtService.java` | X.509 RSA public key for verifying access tokens | Generated alongside private key |
| `RESEND_API_KEY` | **Required in Prod** | `re_dummy_dev_key` (Tier 0 uses dev mailbox) | `ResendEmailSender.java` | Bearer API token for transactional emails (**Secret**) | From Resend dashboard (see §4.1) |
| `CONTACT_TO_EMAIL` | Optional | `contact@projectaegis.in` | `ResendEmailSender.java` | Inbox address receiving inbound contact form submissions | Company team mailbox |
| `CONTACT_FROM_EMAIL` | Optional | `onboarding@resend.dev` | `ResendEmailSender.java` | Verified sending address for transactional emails | Verified sender in Resend |
| `PUBLIC_SITE_URL` | Optional | `http://localhost:4321` | `ContactController.java` | Marketing website base URL for 303 form redirects | Public domain of marketing site |
| `PUBLIC_CONSOLE_URL` | Optional | `http://localhost:5173` | `NotificationService.java` | Console base URL for generating invite links | Public domain of operator console |
| `AEGIS_ALLOW_NO_DOCKER` | Optional (Dev) | `""` | `build.gradle.kts`, `DockerAvailability.java` | Allows running Gradle tests against local PostgreSQL without Docker | Set to `true` when Docker Desktop is absent |
| `aegis.auth.mode` | Optional (Dev) | `jwt` | `TenantHeaderFilter.java`, `JwtAuthenticationFilter.java` | Authentication mode: `jwt` (standard) or `dev-header` (Tier 0 only) | Default `jwt`; never use `dev-header` in prod |
| `AEGIS_DB_MAX_POOL_SIZE` | Optional (Prod) | `10` | `application-prod.yml` | HikariCP maximum connection pool size | Default `10` (use `5` for small/free DBs) |
| `AEGIS_DB_MIN_IDLE` | Optional (Prod) | `2` | `application-prod.yml` | HikariCP minimum idle connections | Default `2` |
| `DOMAIN` | **Required in Prod** | `projectaegis.in` | `docker-compose.prod.yml`, `Caddyfile` | Apex domain used by Caddy reverse proxy for SSL and routing | Your registered domain name |
| `ACME_EMAIL` | Optional (Prod) | `admin@example.com` | `Caddyfile` | Contact email for Let's Encrypt TLS certificate expiry notices | Administrator email |

---

### 3.2 Frontend Console Configuration (`frontend/.env`)

| Variable | Required? | Local Default / Example | Used By (File / Class) | What It Does | Where To Get It |
| :--- | :---: | :--- | :--- | :--- | :--- |
| `VITE_API_URL` | Optional in Dev | `http://127.0.0.1:8080` | `vite.config.ts` | Upstream backend URL targeted by the Vite dev proxy | Local backend port |
| `VITE_DEV_TENANT_ID` | Optional (Dev only) | `00000000-0000-0000-0000-000000000001` | `client.ts` | Fixed development tenant UUID for mock sessions | Hardcoded fixed dev tenant UUID |

---

### 3.3 Marketing Website Configuration (`website/.env`)

| Variable | Required? | Local Default / Example | Used By (File / Class) | What It Does | Where To Get It |
| :--- | :---: | :--- | :--- | :--- | :--- |
| `PUBLIC_SITE_URL` | **Required in Prod** | `http://localhost:4321` | `siteConfig.ts`, `astro.config.mjs` | Canonical URL of the marketing website (used for Open Graph & sitemap) | Production domain name |
| `PUBLIC_CONSOLE_URL` | **Required in Prod** | `http://localhost:5173` | `siteConfig.ts`, `Header.astro` | Target URL for "Log in" and "Get started" navigation buttons | Production console URL |
| `PUBLIC_API_URL` | **Required in Prod** | `http://localhost:8080` | `siteConfig.ts`, `contact.astro` | Target URL for native HTML contact form submission (`POST /v1/public/contact`) | Production API gateway URL |
| `PUBLIC_SECURITY_EMAIL` | Optional | `security@example.com` | `siteConfig.ts`, `security.astro` | Displayed on the Responsible Disclosure security page | Company security team address |
| `PUBLIC_INDEXABLE` | Optional | `true` | `robots.txt.ts`, `BaseLayout.astro` | Controls crawler indexing (`false` emits `Disallow: /` and `noindex`) | Set to `false` on staging/preview sites |

---

## 4. Third-Party Services Setup

### 4.1 Resend (Transactional Email & Inbound Contact Inquiries)
Project Aegis uses [Resend](https://resend.com) for sending user signup verification OTPs, tenant member invitations, password reset links, and routing inbound contact inquiries to the engineering team.

1. **Sign Up:** Create an account at [https://resend.com](https://resend.com).
2. **Generate API Key:** Navigate to **API Keys** in the dashboard and create an API Key with "Full Access" or "Sending Access".
3. **Configure Environment Variable:**
   - In `backend/.env` (production only):
     ```properties
     RESEND_API_KEY=re_1234567890abcdef...
     ```
   - **Crucial Invariant:** `RESEND_API_KEY` goes **exclusively** into `backend/.env`. It must never be placed in `frontend/.env` or `website/.env`.
4. **Development Testing Mode:**
   - For local development with `SPRING_PROFILES_ACTIVE=tier0`, you do **not** need a real Resend API key. The backend automatically routes all OTPs, invites, and contact messages to the in-memory simulated mailbox accessible at `GET /dev/mailbox` or [http://localhost:5173/dev/mailbox](http://localhost:5173/dev/mailbox).
   - For sandbox testing with the real Resend API, use `CONTACT_FROM_EMAIL=onboarding@resend.dev` (which can deliver only to the email address used to register the Resend account).
5. **Production Domain Verification:**
   - Go to **Domains** in the Resend dashboard and click **Add Domain** (e.g. `projectaegis.in`).
   - Add the specified DNS records to your domain provider:
     - **SPF:** `TXT` record with value `v=spf1 include:resend.com ~all`
     - **DKIM:** Three `CNAME` records matching the keys provided by Resend
     - **DMARC:** `TXT` record `_dmarc.yourdomain.com` with `v=DMARC1; p=none;`
   - Once verified, update `CONTACT_FROM_EMAIL=notifications@yourdomain.com`.
6. **Free Tier Limits:**
   - 100 emails / day
   - 3,000 emails / month
   - Exceeding the rate limit triggers HTTP 429 errors from Resend, which the backend will log.

---

### 4.2 Cloudinary (Future Asset Storage — Not Currently Used)
Project Aegis **does not currently use Cloudinary**. All images and diagrams are embedded as optimized inline SVGs or local assets to maintain the zero third-party network request invariant.

If Cloudinary is integrated in the future:
1. **Planned Environment Variables:**
   - `CLOUDINARY_CLOUD_NAME`: Cloud account name
   - `CLOUDINARY_API_KEY`: Server-side API key
   - `CLOUDINARY_API_SECRET`: Server-side API secret (**Secret**, backend only)
   - `PUBLIC_CLOUDINARY_BASE_URL`: Base CDN distribution URL
2. **Architectural & Privacy Disclosures:**
   - Loading images from Cloudinary introduces third-party network requests that transmit visitor IP addresses to Cloudinary infrastructure.
   - If enabled, this integration **must** be documented in `website/src/pages/privacy.astro` under Third-Party Service Providers, and Content Security Policy (CSP) headers must be updated.

---

## 5. How to Generate Cryptographic Secrets

Use these copy-pasteable PowerShell commands on Windows to generate secrets:

### 5.1 Generate 32-Byte Base64 Key (for `AEGIS_TOTP_ENC_KEY`)
```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```
*Example Output:* `TkvHlggbIGt9MIde4QffDwgt5f4dffinpO0AS4Kuvbg=`

### 5.2 Generate RSA 2048-bit Key Pair (for `AEGIS_JWT_PRIVATE_KEY` and `AEGIS_JWT_PUBLIC_KEY`)
If OpenSSL is installed:
```powershell
# Generate private key (PKCS#8)
openssl genpkey -algorithm RSA -out jwt-private.pem -pkeyopt rsa_keygen_bits:2048

# Extract public key (X.509)
openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem
```
Alternatively, in Tier 0 local development, the backend automatically generates a valid RSA key pair on startup in `backend/.dev-keys/` if none is configured.

---

## 6. Local Development vs. Production Matrix

| Variable | Local Development Value | Production Value / Requirement |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `tier0` | `prod` (or `tier2`) |
| `SERVER_PORT` | `8080` | `8080` (or container internal port) |
| `AEGIS_PG_URL` | `jdbc:postgresql://127.0.0.1:5432/aegis` | `jdbc:postgresql://<rds-host>:5432/<db_name>` |
| `AEGIS_PG_USER` | `aegis` | Dedicated database application user (e.g. `aegis_prod`) |
| `AEGIS_PG_PASSWORD` | `aegis` | Strong, random password from AWS Secrets Manager |
| `AEGIS_REDIS_HOST` | `127.0.0.1` | AWS ElastiCache / Redis cluster endpoint |
| `AEGIS_KAFKA_BOOTSTRAP_SERVERS` | `127.0.0.1:9092` | AWS MSK / Kafka cluster bootstrap endpoints |
| `AEGIS_TOTP_ENC_KEY` | Auto-generated into `.dev-keys/totp.key` | **Mandatory:** 32-byte Base64 key |
| `AEGIS_JWT_PRIVATE_KEY` | Auto-generated into `.dev-keys/jwt-private.pem` | **Mandatory:** 2048-bit RSA PKCS#8 PEM string |
| `AEGIS_JWT_PUBLIC_KEY` | Auto-generated into `.dev-keys/jwt-public.pem` | **Mandatory:** 2048-bit RSA X.509 PEM string |
| `RESEND_API_KEY` | `re_dummy_dev_key` (ignored under Tier 0) | **Mandatory:** Real Resend API Key (`re_...`) |
| `CONTACT_TO_EMAIL` | `contact@projectaegis.in` | Dedicated operational inbox |
| `CONTACT_FROM_EMAIL` | `onboarding@resend.dev` | Verified custom domain (e.g. `notifications@projectaegis.in`) |
| `PUBLIC_SITE_URL` | `http://localhost:4321` | Production domain (`https://projectaegis.in`) |
| `PUBLIC_CONSOLE_URL` | `http://localhost:5173` | Production console (`https://console.projectaegis.in`) |
| `PUBLIC_API_URL` | `http://localhost:8080` | Production API gateway (`https://api.projectaegis.in`) |
| `PUBLIC_INDEXABLE` | `true` (or unset) | `true` for public site; `false` for staging |

### "Before Going to Production" Checklist
- [ ] Change `SPRING_PROFILES_ACTIVE` from `tier0` to `prod`.
- [ ] Set `AEGIS_PG_URL`, `AEGIS_PG_USER`, and `AEGIS_PG_PASSWORD` to production PostgreSQL credentials.
- [ ] Generate and set `AEGIS_TOTP_ENC_KEY` (32-byte base64 secret).
- [ ] Generate and set `AEGIS_JWT_PRIVATE_KEY` and `AEGIS_JWT_PUBLIC_KEY` (2048-bit RSA key pair).
- [ ] Set `RESEND_API_KEY` with a verified Resend domain and configure `CONTACT_FROM_EMAIL`.
- [ ] Set `PUBLIC_SITE_URL`, `PUBLIC_CONSOLE_URL`, and `PUBLIC_API_URL` to production HTTPS endpoints.
- [ ] Confirm `/dev/mailbox` returns HTTP 404 (verifying the dev mailbox is disabled).
- [ ] Confirm `frontend/` production build (`npm run build`) contains zero `/dev/mailbox` traces.

---

## 7. Security Notes & Invariants

1. **Client-Exposed vs. Private Variables:**
   - Any variable prefixed with `PUBLIC_` (Astro) or `VITE_` (Vite) is bundled directly into HTML or client-side JavaScript. **Never put passwords, private keys, or API tokens into `PUBLIC_` or `VITE_` variables.**
   - All secrets (`AEGIS_PG_PASSWORD`, `AEGIS_TOTP_ENC_KEY`, `AEGIS_JWT_PRIVATE_KEY`, `RESEND_API_KEY`) belong strictly in backend server environment variables.
2. **What to Do if a Key Leaks:**
   - Immediately revoke the leaked key at the provider (Resend, AWS, database).
   - Generate a replacement key following the procedures in §5.
   - Update production environment configurations and restart all backend services.
   - If an RSA private key leaked, revoke all active JWT tokens by clearing the refresh token table or updating the key ID claim.

