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

## Repository layout and common commands

Per [ADR-0010: Repository Layout and Workspace Separation](file:///c:/dev/aegis/docs/adr/0010-repository-layout.md), the repository is organized into decoupled functional subsystems:

```text
aegis/
├── README.md               # Primary project introduction and quickstart
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

#### 1. Backend Build and Tests
All Gradle commands execute from the `backend/` directory:
```bash
# Build and run full automated test suite
cd backend && ./gradlew build

# Run unit tests only
cd backend && ./gradlew test

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

#### 3. Frontend Development
```bash
cd frontend
npm install
npm run dev
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
