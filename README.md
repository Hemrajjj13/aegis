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

## Repository layout

```
/                       ← you are here
├── README.md           ← this file
├── docs/
│   ├── 00-index.md                         ← documentation map and status
│   ├── 01-production-readiness-discovery.md
│   ├── 02-customer-market-discovery-plan.md
│   ├── 03-prd.md
│   ├── 04-sow-templates/
│   │   ├── internal-build-sow.md
│   │   └── customer-pilot-sow.md
│   ├── 05-architecture/
│   │   └── overview.md
│   ├── adr/
│   │   ├── 0000-template.md
│   │   ├── 0001-modular-monolith-first.md
│   │   ├── 0002-durable-decision-write-before-response.md
│   │   └── 0003-event-time-for-deterministic-replay.md
│   └── glossary.md
└── (application code not yet present — docs-first phase)
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
