# Aegis — Real-Time Transaction Decision Engine

> **"Aegis" is a placeholder name. Verify it is unregistered in your target markets before using it externally.**

Aegis is a production-grade, multi-tenant transaction decision engine.
It returns `approve / decline / review` for every payment in milliseconds,
records a complete, auditable explanation of every decision, and provides a
**replay tool** that compares two rule versions on the same historical dataset
before either version ships to production.

Target first customers: mid-size Indian fintechs and payment companies
(~1–10 M transactions / month, risk team of 1–5 people).

---

## Developer Documentation & Quick Links

- **[STATUS.md](STATUS.md):** Complete project audit, feature implementation table, milestone reality, and launch readiness checklist.
- **[USAGE.md](USAGE.md):** Operational guide, roles, authentication, complete transaction schema, sample data, and FAQ.
- **[Run.md](Run.md):** Complete, copy-pasteable guide to run the backend, console, website, and database on Windows using PowerShell.
- **[ENV.md](ENV.md):** Authoritative single-source-of-truth reference for all environment variables, secrets generation, and production checklist.
- **[HOSTING.md](HOSTING.md):** Production deployment guide for hosting the entire stack (website, console, backend, database) with free-tier architecture, domain routing, and CI/CD.

---

## Repository layout and common commands

Per [ADR-0010: Repository Layout and Workspace Separation](file:///c:/dev/aegis/docs/adr/0010-repository-layout.md), the repository is organized into decoupled functional subsystems:

```text
aegis/
├── README.md               # Primary project introduction and quickstart
├── STATUS.md               # Code audit, feature completeness, and milestone reality
├── USAGE.md                # Usage guide, API contracts, sample data, and FAQ
├── Run.md                  # Complete local execution and run guide
├── ENV.md                  # Single source of truth for environment variables & secrets
├── HOSTING.md              # Free-tier production deployment and hosting guide
├── AGENTS.md               # Prime directives and constraints for coding agents
├── DESIGN.md               # Design principles and architectural guidelines
├── .gitignore              # Repository-wide ignore rules
├── .gitattributes          # Line-ending and attribute definitions
├── .editorconfig           # Editor indentation and encoding standard
├── backend/                # Gradle multi-module root (Java 21 / Spring Boot 3.3)
│   ├── settings.gradle.kts # Subproject declarations
│   ├── build.gradle.kts    # Root plugin and dependency configurations
│   ├── gradle.properties   # JVM and Gradle daemon parameters
│   ├── gradlew / gradlew.bat / gradle/ # Gradle wrapper binaries and scripts
│   ├── aegis-core          # Pure domain models and deterministic EventClock SPI
│   ├── aegis-storage       # PostgreSQL persistence, Flyway migrations, and RLS
│   ├── aegis-decision-api  # Synchronous POST /v1/decisions and SSE stream feed
│   ├── aegis-app           # Executable Spring Boot application runner
│   └── (module shells)     # velocity, rules, replay, identity, audit, dataset-importer
├── frontend/               # React operator and analyst console SPA (TypeScript / Vite)
│   └── README.md           # Console architecture and setup guide
├── website/                # Public marketing site (Astro, static)
├── api/
│   └── openapi.yaml        # Authoritative OpenAPI 3.1 specification
├── infra/
│   ├── docker-compose.yml  # Tier 0 multi-container orchestration
│   ├── docker/             # Dockerfiles (Dockerfile.backend)
│   └── tier0/              # Local initialization scripts (init-scripts/)
├── docs/                   # Architectural specifications and governance registers
│   └── archive/            # Historical founding and superseded document suites
└── tools/                  # Verification and linting scripts (Node.js)
```

### Common Commands

#### 1. Local Development Without Docker (`scripts/dev-up.ps1` and `scripts/dev-down.ps1`)
For rapid development on Windows without requiring Docker Desktop, Aegis supports an installed or portable PostgreSQL 16 instance:
```powershell
# Set environment variables (or rely on local defaults):
# $env:AEGIS_PG_URL="jdbc:postgresql://127.0.0.1:5432/aegis"
# $env:AEGIS_PG_USER="aegis"
# $env:AEGIS_PG_PASSWORD="aegis"

# Start local PostgreSQL, run Flyway migrations, seed dev tenant, and start app under tier0
.\scripts\dev-up.ps1

# Execute live vertical slice (POST transaction, duplicate test, SSE stream, row counts)
.\scripts\demo-slice.ps1

# Stop local application and PostgreSQL cleanly
.\scripts\dev-down.ps1
```

> **Database Configuration and Testcontainers Policy:**
> - When Docker is available, backend integration tests automatically spin up containerized PostgreSQL 16 instances via **Testcontainers**.
> - When Docker is not available, tests connect to a local PostgreSQL 16 instance via `AEGIS_PG_URL` (default: `jdbc:postgresql://127.0.0.1:5432/aegis`), `AEGIS_PG_USER`, and `AEGIS_PG_PASSWORD`.
> - If neither Docker nor a reachable PostgreSQL instance exists, database integration tests **fail immediately with explicit setup instructions**; tests never skip silently.
> - Zonky embedded-Postgres has been fully removed. All local testing executes against real PostgreSQL 16.

#### 2. Backend Build and Tests
All Gradle commands execute from the `backend/` directory:
```bash
# Build and run full automated test suite (set AEGIS_ALLOW_NO_DOCKER=true if Docker is absent)
cd backend && ./gradlew test -DAEGIS_ALLOW_NO_DOCKER=true

# Start backend application locally with Tier 0 profile
cd backend && ./gradlew bootRun --args='--spring.profiles.active=tier0'
```

#### 2. Tier 0 Infrastructure Orchestration
Start or stop the containerized backing services (PostgreSQL 16, Redis 7, Kafka KRaft):
```bash
# Start backing infrastructure with core profile
docker compose -f infra/docker-compose.yml --profile core up -d

# Check container status and health
docker compose -f infra/docker-compose.yml ps

# Stop backing infrastructure
docker compose -f infra/docker-compose.yml down
```

#### 3. Frontend Console (`frontend/`)
The frontend operator console is a single-page React 18 application built with TypeScript strict mode, Vite, Tailwind CSS driven by `DESIGN.md` CSS variable tokens, and `@tanstack/react-virtual`.

> **Memory Budget Warning:** On low-memory development machines (~3.89 GB RAM), **Gradle tests (`./gradlew test`), the backend (`scripts/dev-up.ps1`), and Playwright E2E tests must NOT run simultaneously.** Run one at a time to prevent Out-Of-Memory (OOM) failures or process thrashing.

```bash
cd frontend
npm install

# Type-safe API generation & verification
npm run gen:api        # Regenerates src/api/schema.d.ts from api/openapi.yaml
npm run gen:api:check  # Verifies schema types are up to date

# Code quality & unit tests
npm run lint           # ESLint with zero warnings
npm run typecheck      # Strict TypeScript check (tsc --noEmit)
npm test               # Vitest suite (DecisionBadge, ring buffer, decisionReducer)

# End-to-end tests (requires backend running via scripts/dev-up.ps1)
npm run e2e            # Playwright Chromium & Axe WCAG 2.1 AA accessibility tests

# Production build
npm run build          # Compiles optimized production bundle into dist/
```

#### 4. Architecture and Documentation Verification
Audit documentation integrity, OpenAPI 3.1 schemas, and PostgreSQL DDL:
```bash
node tools/check_docs.js
node tools/check-openapi.mjs
node tools/check-sql.mjs
```

---

## How the docs are ordered

Documentation is written in four waves aligned with the build schedule.
Read `docs/00-index.md` for the full map.

| Wave | Window | Purpose |
|------|--------|---------|
| 1 — Discovery | Weeks 1–3 | Define "done", validate market, set scope |
| 2 — Design | Weeks 3–6 | Architecture, API contracts, data model, security |
| 3 — Operate | Weeks 6–9 | SLOs, runbooks, DR, compliance mapping |
| 4 — Market | Weeks 8–12 | GTM, pricing, pilot agreements |

**Start here:** `docs/01-production-readiness-discovery.md`.
Every other document depends on its answers.

---

## Status — October 2026

All 22 core architecture, API, security, compliance, frontend, backend, test, and GTM specifications are complete. See [docs/00-index.md](file:///c:/dev/aegis/docs/00-index.md) for the complete documentation index and verification status.

Current Phase: **Draft-complete (docs v1.0, machine-checked, not yet reviewed)**  
Next Phase: Founder & design partner review (Gate G1).

---

## Verification Tools

Documentation integrity and WCAG color contrast verification scripts are available in `/tools` and run with Node.js:

### Documentation Linter (`tools/check_docs.js`)
Validates that all documentation links resolve, status lines match the specification standard (`Draft-complete (docs v1.0, machine-checked, not yet reviewed)`), canonical terms and the 7 application roles match the glossary, no ungrounded compliance claims exist, and all assumption rows are properly gated:
```bash
node tools/check_docs.js
```
*(Also available as `python tools/check_docs.py` if Python 3 is installed).*

### Color Contrast Calculator (`tools/contrast.js`)
Computes WCAG 2.1 relative luminance and contrast ratios between text and background hex colors, verifying AA / AAA thresholds:
```bash
node tools/contrast.js "#FFFFFF" "#3849D6"
```
*(Also available as `python tools/contrast.py "#FFFFFF" "#3849D6"` if Python 3 is installed).*

---

## Key engineering constraints (summarised)

Two problems that every other architectural decision flows through:

1. **Velocity-limit correctness under concurrency** — two simultaneous
   transactions for the same account must not both bypass a limit.
   Approach: Kafka ordering by `tenant:account`, atomic Redis Lua
   check-and-increment, Postgres as source of truth, reconciliation job,
   CI concurrency test asserting exactly L of N requests pass.

2. **Exactly-once decisions despite at-least-once delivery** — a replayed
   or duplicated event must never produce a second decision row.
   Approach: API idempotency key + payload hash, unique `(tenant_id, event_id)`
   constraint committed in the same transaction as the decision, transactional
   outbox, replay writes isolated to replay tables keyed by `run_id`.

---

## Contributing

This repository is in the docs-first phase (Stage 0).
No pull request touching application code will be merged until `docs/03-prd.md`
has been reviewed by at least one design partner and the production-readiness
gate in `docs/01-production-readiness-discovery.md` §12 is passing.

Human-only review required for: authentication flows, cryptographic primitives,
the Redis velocity Lua script, and all database migrations.
