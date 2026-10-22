# Project Aegis - Operator & Analyst Console (Frontend)

**Status:** Placeholder for Milestone M7 (Console UI, Virtualized Tables & SSE Live Telemetry)  
**Architecture Reference:** [Document 18: Frontend Architecture](file:///c:/dev/aegis/docs/18-frontend-architecture.md) and [Document 10: Design System & UX](file:///c:/dev/aegis/docs/10-design-system-and-ux.md)  

---

## Overview

The Project Aegis console is a single-page React application built with TypeScript, Vite, and TailwindCSS/Vanilla CSS tokens. It connects to the backend REST APIs and Server-Sent Events (SSE) telemetry feed (`GET /v1/events/stream`) to provide operators, risk managers, and auditors with:
- Live transaction telemetry feed (`SCR-04`)
- Case queue triage and manual disposition (`SCR-05`, `SCR-06`)
- Ruleset authoring with Monaco CEL editor and AST complexity validation (`SCR-07`, `SCR-08`, `SCR-09`)
- Deterministic replay visualization and comparison matrix (`SCR-10`, `SCR-11`)
- Dataset import and backtest report export (`SCR-12`, `SCR-13`)
- Cryptographic audit trail inspection and verification (`SCR-14`)

Development will commence under Milestone M7.
