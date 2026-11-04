# Project Aegis — Local Execution & Run Guide

This guide contains complete, step-by-step instructions for running all Project Aegis components locally on Windows using PowerShell.

---

## 1. Overview of Runnable Components

Project Aegis consists of three primary runnable applications and a backing infrastructure layer:

| Service / App | Directory | Default Port | Description | Start Command |
| :--- | :--- | :--- | :--- | :--- |
| **PostgreSQL 16** | `infra/` or local | `5432` | Primary database (multi-tenant, Row-Level Security, Flyway migrations) | `docker compose -f infra/docker-compose.yml --profile core up -d postgres` or `.\scripts\dev-up.ps1` |
| **Redis 7** | `infra/` | `6379` | Sliding-window velocity counters (Tier 1/2) | `docker compose -f infra/docker-compose.yml --profile core up -d redis` |
| **Kafka (KRaft)** | `infra/` | `9092` | Transaction event streaming log (Tier 1/2) | `docker compose -f infra/docker-compose.yml --profile core up -d kafka` |
| **Backend API Engine** | `backend/` | `8080` | Spring Boot 3.3 REST API (Decisions, SSE, Identity, Contact ingestion) | `cd backend; .\gradlew.bat :aegis-app:bootRun` |
| **Operator Console** | `frontend/` | `5173` | React 18 + Vite SPA for risk analysts and operators | `cd frontend; npm run dev` |
| **Marketing Website** | `website/` | `4321` | Static Astro site (Zero JS, Zero Cookies, Contact form) | `cd website; npm run dev` |

---

## 2. Prerequisites & Environment Check

Verify all required tool versions before running the project:

| Tool | Required Version | Verification Command (PowerShell) |
| :--- | :--- | :--- |
| **Java JDK** | `21` (Eclipse Adoptium or OpenJDK 21) | `java -version` |
| **Gradle** | `8.10.2` (managed via `./gradlew.bat`) | `cd backend; .\gradlew.bat --version` |
| **Node.js** | `>= 20.0.0` (LTS recommended) | `node -v` |
| **npm** | `>= 10.0.0` | `npm -v` |
| **Git** | `>= 2.30.0` | `git --version` |
| **Docker** (Optional) | Docker Desktop with Compose v2 | `docker compose version` |

> [!NOTE]
> Docker Desktop is optional for local development. If Docker is not installed or running, set `AEGIS_ALLOW_NO_DOCKER=true` and run against a local PostgreSQL 16 server.

---

## 3. First-Time Setup

Clone the repository and prepare all local environment configuration files:

```powershell
# 1. Clone repository
git clone https://github.com/Hemrajjj13/aegis.git
cd aegis

# 2. Copy environment templates to active local .env files
Copy-Item backend\.env.example backend\.env
Copy-Item backend\.env.example .env
Copy-Item frontend\.env.example frontend\.env
Copy-Item website\.env.example website\.env

# 3. Install frontend dependencies
cd frontend
npm install
cd ..

# 4. Install website dependencies
cd website
npm install
cd ..
```

*Refer to [ENV.md](file:///c:/dev/aegis/ENV.md) for full descriptions of all variables in each `.env` file.*

---

## 4. Infrastructure (PostgreSQL, Redis, Kafka)

### Option A: Using Docker Compose (Recommended if Docker Desktop is running)
Start backing services in the background:
```powershell
docker compose -f infra/docker-compose.yml --profile core up -d
```

Verify health status:
```powershell
docker compose -f infra/docker-compose.yml ps
```

### Option B: Local PostgreSQL (Without Docker)
If running a local PostgreSQL 16 instance on Windows:
```powershell
# Test PostgreSQL connectivity
& "pg_isready" -h 127.0.0.1 -p 5432
```
Ensure a database named `aegis` and user `aegis` with password `aegis` exists, or adjust `AEGIS_PG_URL`, `AEGIS_PG_USER`, and `AEGIS_PG_PASSWORD` in `backend/.env`.

### Database Migrations
Migrations are managed automatically by Flyway (`V1__...sql` through `V9__...sql` located in `backend/aegis-storage/src/main/resources/db/migration`). They run automatically on Spring Boot application startup.

### Resetting the Database
To wipe and reset the local database schema cleanly:
```powershell
# If using Docker:
docker compose -f infra/docker-compose.yml down -v
docker compose -f infra/docker-compose.yml --profile core up -d postgres

# If using local PostgreSQL:
# Drop and recreate the aegis database via psql:
# psql -U postgres -c "DROP DATABASE IF EXISTS aegis;"
# psql -U postgres -c "CREATE DATABASE aegis OWNER aegis;"
```

---

## 5. Run the Backend (`aegis-app`)

The backend is an executable Spring Boot monolithic application combining `aegis-core`, `aegis-storage`, `aegis-decision-api`, and `aegis-identity`.

```powershell
cd backend
.\gradlew.bat :aegis-app:bootRun
```

- **Active Profile:** `tier0` (default in `backend/.env` for local in-memory/simulated execution).
- **Listening Port:** `8080`.
- **Health Verification:** Confirm startup in terminal logs:
  ```text
  Started AegisApplication in X.XX seconds
  ```
  Or check the health endpoint:
  ```powershell
  curl.exe http://localhost:8080/actuator/health
  # Expected response: {"status":"UP"}
  ```

---

## 6. Run the Operator Console (`frontend`)

The operator and risk analyst console is a React 18 single-page application built with Vite:

```powershell
cd frontend
npm run dev
```

- **URL:** [http://localhost:5173](http://localhost:5173)
- **Vite Proxy:** All API requests to `/v1/*` and simulated mailbox requests to `/dev/*` are automatically forwarded to `http://127.0.0.1:8080`.

---

## 7. Run the Marketing Website (`website`)

The public marketing website is an Astro static site:

```powershell
cd website
npm run dev
```

- **URL:** [http://localhost:4321](http://localhost:4321)
- **Zero JS Invariant:** The site compiles to pure static HTML and CSS with zero client-side scripts.

---

## 8. Quick Start (Run Everything)

To start the full stack for local testing, open three separate PowerShell windows:

```powershell
# Window 1: Start PostgreSQL and Backend Engine
cd c:\dev\aegis
docker compose -f infra/docker-compose.yml --profile core up -d postgres
cd backend
.\gradlew.bat :aegis-app:bootRun

# Window 2: Start Operator Console
cd c:\dev\aegis\frontend
npm run dev

# Window 3: Start Marketing Website
cd c:\dev\aegis\website
npm run dev
```

---

## 9. Try It Out (Verification Walkthrough)

### 9.1 Authentication & Dev Mailbox Flow
1. Open the console at [http://localhost:5173/signup](http://localhost:5173/signup).
2. Enter your full name, organization, email (`admin@example.com`), password, and a test phone number (`+919876543210`).
3. Open the simulated dev mailbox in another tab: [http://localhost:5173/dev/mailbox](http://localhost:5173/dev/mailbox).
4. Copy the 6-digit OTP dispatched for purpose `SIGNUP_MOBILE_VERIFY`.
5. Return to the signup tab, enter the OTP, and proceed to TOTP configuration.
6. Scan or copy the TOTP base32 key into your authenticator app (or use Google Authenticator), enter the 6-digit TOTP code, save the recovery codes, and sign in.

### 9.2 Real-Time Decision Feed & SSE Streaming
1. Navigate to the Live Decisions page: [http://localhost:5173/decisions](http://localhost:5173/decisions). Notice the green `Live Stream` badge connected to `GET /v1/events/stream`.
2. In a terminal, send a payment decision request using the real `PaymentEvent` contract:

```powershell
curl.exe -i -X POST http://127.0.0.1:8080/v1/decisions `
  -H "Content-Type: application/json" `
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" `
  -d '{
    "event_id": "evt_test_001",
    "event_time": "2026-10-30T18:00:00Z",
    "sequence_number": 1,
    "tenant_id": "00000000-0000-0000-0000-000000000001",
    "account_id_hash": "acc_hash_12345",
    "card_token": "tok_visa_4242",
    "amount_minor_units": 250000,
    "currency": "INR",
    "payment_method": "card",
    "merchant_id": "merch_acme_corp",
    "merchant_category_code": "5411",
    "merchant_name": "Acme Supermarket",
    "channel": "pos",
    "ip_address": "103.21.244.0"
  }'
```

3. Confirm the HTTP 200 response with `outcome`: `"APPROVE"`, `reason_codes`, and `execution_duration_micros`.
4. Observe the event immediately pop up in the console's live decision stream at [http://localhost:5173/decisions](http://localhost:5173/decisions).

### 9.3 Marketing Contact Form & Dev Mailbox Ingestion
1. Visit the contact page on the marketing website: [http://localhost:4321/contact](http://localhost:4321/contact).
2. Fill out the form (Name: `Ada Lovelace`, Email: `ada@analytical.engine`, Topic: `Pricing`, Message: `Evaluating Aegis decision latency.`).
3. Click **Send Message**.
4. You will be redirected to [http://localhost:4321/contact/thanks](http://localhost:4321/contact/thanks).
5. Open [http://localhost:5173/dev/mailbox](http://localhost:5173/dev/mailbox) (or run `curl.exe http://127.0.0.1:8080/dev/mailbox`).
6. Confirm the inquiry appears with `CONTACT_Pricing` and Ada's email details.

---

## 10. Running Automated Tests

### Backend Test Suite
```powershell
cd backend
# Run all module tests (sets AEGIS_ALLOW_NO_DOCKER=true if Docker is absent)
.\gradlew.bat test -DAEGIS_ALLOW_NO_DOCKER=true
```

### Frontend Test Suite
```powershell
cd frontend
# Run unit and integration tests (Vitest)
npm test

# Type-check TypeScript
npm run typecheck

# Lint with ESLint
npm run lint

# Run Playwright E2E browser tests (ensure backend is running)
npm run e2e
```

### Website Test Suite & Performance Gates
```powershell
cd website
# Run unit tests
npm test

# Audit content for banned buzzwords ("guarantee", "seamless", "bank-grade", etc.)
npm run check:content

# Enforce payload budget (< 500 KB total HTML transfer, 0.0 KB client JS)
npm run check:budget

# Run Playwright E2E & Axe accessibility audits
npm run e2e
```

---

## 11. Production Build Commands

```powershell
# 1. Build Backend Executable Fat JAR
cd backend
.\gradlew.bat :aegis-app:bootJar
# Artifact generated at: backend/aegis-app/build/libs/aegis-app-0.1.0-SNAPSHOT.jar

# 2. Build Frontend Console Bundle
cd ..\frontend
npm run build
# Static bundle emitted to: frontend/dist/ (Dev mailbox page is tree-shaken and removed)

# 3. Build Marketing Website
cd ..\website
npm run build
# Static HTML emitted to: website/dist/ (Includes sitemap-index.xml and robots.txt)
```

> [!IMPORTANT]
> **Production Deployment Changes:**
> - Set `SPRING_PROFILES_ACTIVE=prod` (or `tier2`).
> - The `/dev/mailbox` endpoint returns **HTTP 404** (route unregistered).
> - Real `RESEND_API_KEY`, `AEGIS_TOTP_ENC_KEY`, and `AEGIS_JWT_PRIVATE_KEY` secrets are required at startup; the backend refuses to boot if any are missing.

---

## 12. Troubleshooting Real Issues

| Symptom / Error | Root Cause | Solution |
| :--- | :--- | :--- |
| `Port 8080 already in use` | A lingering `java` process is still bound to port 8080. | Find and kill the process: `Get-Process java \| Stop-Process -Force` |
| `Port 5432 already in use` | Local Windows PostgreSQL service or another Docker container is running. | Stop the conflicting service: `Stop-Service postgresql*` or `docker stop $(docker ps -q --filter ancestor=postgres)` |
| `Connection to 127.0.0.1:5432 refused` | PostgreSQL server is not started. | Run `docker compose -f infra/docker-compose.yml --profile core up -d postgres` or start local PostgreSQL. |
| `FATAL: RESEND_API_KEY environment variable is mandatory under prod/tier2` | Backend booted under `prod` profile without configuring Resend credentials. | In development, use `SPRING_PROFILES_ACTIVE=tier0`. In production, set `RESEND_API_KEY`. |
| `FATAL: JWT signing keys must be configured in tier1/tier2 profiles` | RS256 private/public key variables were not supplied under a hardened profile. | Set `AEGIS_JWT_PRIVATE_KEY` and `AEGIS_JWT_PUBLIC_KEY`, or use `tier0` (which auto-generates dev keys). |
| `403 Forbidden` on `/v1/auth/refresh` | Missing CSRF protection header. | Refresh requests must include header `X-CSRF-Token: 1`. |
| `Account temporarily locked. Please try again in 30 minutes.` | 5 consecutive failed login attempts triggered security lockout. | Wait 30 minutes, or reset the dev rate limiter by sending `POST http://127.0.0.1:8080/dev/mailbox/reset`. |
| Contact form redirecting to `/contact/error?reason=rate_limit` | Exceeded 5 contact form submissions within 1 hour from the same IP. | Reset the rate limiter with: `curl.exe -X POST http://127.0.0.1:8080/dev/mailbox/reset`. |

---

## 13. Stopping and Cleaning Up

```powershell
# 1. Stop background Docker services and delete containers
docker compose -f infra/docker-compose.yml down

# 2. Stop Docker services AND wipe persistent database volumes
docker compose -f infra/docker-compose.yml down -v

# 3. Kill running Java backend processes
Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match "aegis" -or $_.CommandLine -match "aegis" } | Stop-Process -Force

# 4. Clean scratch temporary files and logs
Remove-Item -Path "scratch\*.log", "scratch\*.err", "scratch\*.pid" -Force -ErrorAction SilentlyContinue
```

