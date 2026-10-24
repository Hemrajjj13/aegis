# Project Aegis — Operator & Analyst Console (`frontend`)

**Status:** TSK-008 Implemented (Minimal React Console with Live Decisions Table, SSE Streaming & WCAG 2.1 AA Compliance)  
**Architecture Reference:** [Document 18: Frontend Architecture](file:///c:/dev/aegis/docs/18-frontend-architecture.md), [Document 10: Design System & UX](file:///c:/dev/aegis/docs/10-design-system-and-ux.md), and [DESIGN.md](file:///c:/dev/aegis/DESIGN.md)  
**Target Stack:** React 18, TypeScript Strict, Vite, Tailwind CSS driven by `DESIGN.md` CSS variable design tokens.

---

## 1. System Overview & Core Capabilities

The console provides real-time fraud monitoring and operational decision telemetry for fraud risk analysts:

1. **Live Decisions Feed (`/decisions`, `SCR-04`):**
   - **Streaming Transport:** Real-time Server-Sent Events (SSE) via `@microsoft/fetch-event-source` connected to `GET /v1/events/stream`. Attaches `X-Tenant-Id` header (which native `EventSource` cannot do), automatically ignores initial `"connected"` handshake, and implements exponential backoff reconnection with visible status badges (`Live Stream`, `Connecting...`, `Reconnecting...`, `Disconnected`).
   - **Circular Ring Buffer:** Fixed 2,000-event circular memory buffer in `decisionReducer.ts`. Bounded memory usage prevents memory leaks during high-throughput evaluation.
   - **Deduplication:** Guarantees strict deduplication by `decision_id`.
   - **Pause & Inspect:** Toggling pause accumulates incoming events up to the buffer cap and displays a dynamic `+N new` badge. Unpausing flushes accumulated events to the table.
   - **Virtualized Rendering:** Rendered with `@tanstack/react-virtual` for 60 FPS scrolling across thousands of transaction rows with minimal DOM nodes.
   - **Keyboard Navigation:** Keyboard shortcuts `j` (select next row down) and `k` (select previous row up) navigate the virtualized table with auto-scrolling into view. `Space` toggles feed pause/resume.
2. **Accessible Decision Badges (`<DecisionBadge />`):**
   - Strict WCAG 2.1 AA/AAA compliance per `DESIGN.md` §1 & §4.
   - **Never relies on color alone:** Always combines color tokens, explicit bold uppercase labels (`APPROVE`, `REVIEW`, `DECLINE`), geometric iconography (`[✓]`, `[⚑]`, `[✕]`), and mandatory `aria-label="Outcome: <Outcome>"`.
3. **Theme Switcher:**
   - Dark theme default (`data-theme="dark"`) with companion Light theme (`data-theme="light"`).
   - Recomputed WCAG 2.1 AA compliant color tokens (`--approve`, `--review`, `--decline`, `--canvas`, `--surface`).
   - Invariant: **Only the theme name (`'dark'` or `'light'`) may be stored in `localStorage`**. Never store transaction, card, or decision data in client-side storage.
4. **Dev-Only Test Transaction Harness:**
   - Modal dialog sending valid `TransactionEvent` payloads to `POST /v1/decisions` via the generated OpenAPI client.
   - Readably displays RFC 7807 problem details (`status`, `title`, `detail`, `invalid_params`) when validation fails.
   - Strictly enforces the Zero PAN invariant: accepts network tokens (`tok_...`), while raw PANs are rejected by the backend Luhn validator.

---

## 2. Resource Budget & Memory Constraints

> [!CAUTION]
> **Strict Concurrency Rule (Low Memory Machine Budget):**
> Memory is constrained on this development machine (~3.89 GB RAM).
> **Never run Gradle test suites (`./gradlew test`), the backend server (`scripts/dev-up.ps1`), and Playwright test suites simultaneously.**
> Running them concurrently will cause Out-Of-Memory (OOM) failures or process thrashing. Always execute them sequentially:
> 1. Stop background servers before running Gradle builds (`scripts/dev-down.ps1`).
> 2. Start the backend with `scripts/dev-up.ps1 -Wait` before running E2E tests.
> 3. Stop the backend with `scripts/dev-down.ps1` once Playwright finishes.

---

## 3. API Contract Generation & Client

- **OpenAPI Source of Truth:** `api/openapi.yaml`
- **Generator:** `openapi-typescript` generates `src/api/schema.d.ts` without hand-written type interfaces.
- **Client Transport:** `openapi-fetch` typed client configured in `src/api/client.ts`.
- **Dev Tenant Header:** Injects `X-Tenant-Id: VITE_DEV_TENANT_ID` (default `00000000-0000-0000-0000-000000000001`). Marked dev-only; will be replaced by user session JWTs when auth lands in Milestone M8.

---

## 4. NPM Scripts

| Script | Command | Purpose |
|---|---|---|
| `npm run gen:api` | `openapi-typescript ../api/openapi.yaml -o src/api/schema.d.ts` | Regenerates type-safe API schemas from OpenAPI contract |
| `npm run gen:api:check` | `node scripts/check-api-schema.mjs` | CI verification asserting `src/api/schema.d.ts` matches `openapi.yaml` |
| `npm run lint` | `eslint . --ext ts,tsx --max-warnings 0` | Enforces zero ESLint warnings across TypeScript and React code |
| `npm run typecheck` | `tsc --noEmit` | Strict TypeScript compiler validation |
| `npm test` | `vitest run` | Runs unit tests (DecisionBadge, ringBuffer, decisionReducer) with jsdom |
| `npm run e2e` | `playwright test` | Runs Chromium E2E (transaction latency < 2s) and Axe a11y tests |
| `npm run build` | `tsc && vite build` | Produces production-ready bundle |
| `npm run dev` | `vite` | Starts local dev server with proxy to backend |
