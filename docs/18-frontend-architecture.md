# Frontend Architecture Specification

**Document:** 18  
**Status:** Approved  
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

---

## 1. Purpose and System Context

This specification defines the frontend architecture, component hierarchy, client-side routing, data synchronisation, state management, and accessibility standards for the **Project Aegis Console** (`apps/console`).

The console is a single-page application (SPA) built with **React 18+**, **TypeScript (strict mode)**, and **Vite**, designed to serve five primary enterprise user personas across risk, compliance, engineering, and administrative roles. It provides low-latency operational oversight, interactive Common Expression Language (CEL) rule authoring with syntax validation and AST checking, deterministic historical backtesting, real-time transaction streaming, and cryptographically verified audit inspection.

### 1.1 Architectural Precedents and Principles
- **Decision Alignment:** Conforms to Decision [DR-DES-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) (Compact tabular density for analysts; Comfortable density for audit/settings), [DR-DES-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) (Audio alerts opt-in), [DR-DES-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) (Header branding with scoped tenant isolation), and [DR-DES-04](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) (Deep URL routing for multi-monitor operation).
- **Design System Integration:** Implements visual tokens, WCAG 2.1 AA/AAA color pairings, typography, and density rules specified in [Document 10 §2–§4](file:///c:/dev/aegis/docs/10-design-system-and-ux.md).
- **Zero Raw Wall-Clock Dependencies in Core State:** Display components format timestamps strictly according to the user's localized timezone (defaulting to Asia/Kolkata / IST, `+05:30`), while internal calculations utilize ISO-8601 UTC strings.
- **Strict Claims Discipline:** In accordance with Decision D9, all performance claims (e.g., table virtualisation rendering speeds, SSE reconnect backoffs) are specified as engineering budgets and measurable targets rather than historical absolutes.

---

## 2. Directory Structure and Project Organization

The console frontend resides in `apps/console` within the repository structure outlined in [Document 17 §2.1](file:///c:/dev/aegis/docs/17-backend-design.md). It enforces a feature-based modular layout with strict unidirectional dependency flow:

```
apps/console/
├── index.html                     # HTML5 shell with root div, dark background CSS, viewport meta
├── vite.config.ts                 # Vite config, dev proxy, bundle analyzer, test configuration
├── tsconfig.json                  # Strict TypeScript configuration (noImplicitAny, strictNullChecks)
├── package.json                   # Dependencies (React 18+, TanStack Query/Virtual, Monaco, OpenAPI client)
├── src/
│   ├── main.tsx                   # Application entry point, React 18 createRoot, QueryClientProvider
│   ├── App.tsx                    # Root provider hierarchy, router mounting, global keyboard listener
│   ├── index.css                  # CSS token custom properties from Doc 10 §2 (canvas, surface, text)
│   ├── api/                       # Auto-generated OpenAPI SDK and HTTP transport layer
│   │   ├── generated/             # Output from openapi-typescript-codegen via Doc 06 §3
│   │   ├── client.ts              # Custom Axios/Fetch wrapper injecting Bearer JWT and Tenant ID
│   │   └── queryKeys.ts           # Hierarchical query key factory for TanStack Query
│   ├── assets/                    # Static SVG icons, tenant logos, fallback graphics
│   ├── components/                # Reusable design system primitives (Doc 10 §4)
│   │   ├── ui/                    # Button, Badge, Input, Select, Modal, Drawer, Tooltip, FocusRing
│   │   ├── table/                 # VirtualizedTable, TableHeader, ColumnHeaderSort, Pagination
│   │   ├── editor/                # MonacoRuleEditor, CelSquiggleProvider, AstVisualizer
│   │   └── feedback/              # Toast, ErrorBoundary, SkeletonLoader, EmptyState
│   ├── contexts/                  # React Contexts for cross-cutting concerns
│   │   ├── AuthContext.tsx        # User session, JWT tokens, permissions, active tenant state
│   │   ├── ThemeContext.tsx       # Dark/light mode switcher, CSS variable injector
│   │   ├── DensityContext.tsx     # Compact (28px) vs. Comfortable (44px) row height state
│   │   └── SseStreamContext.tsx   # SSE live feed connection manager, buffer queue, pause state
│   ├── hooks/                     # Custom shared React hooks
│   │   ├── useKeyboardShortcut.ts # Declarative key listener (j, k, a, d, Cmd+K, /)
│   │   ├── useRoleGuard.ts        # RBAC role assertion hook
│   │   └── useDebounce.ts         # Query string and search debouncing
│   ├── layouts/                   # Shell layouts
│   │   ├── AppLayout.tsx          # Authenticated layout: SideNav, Header, CommandBar, Breadcrumbs
│   │   ├── AuthLayout.tsx         # Unauthenticated shell for login, invite setup, OTP verify
│   │   └── StandaloneLayout.tsx   # Detached monitor view for live feed and NOC boards (DR-DES-04)
│   ├── routes/                    # Route tree configuration and lazy-loaded page modules
│   │   ├── index.tsx              # React Router v6 tree with role-based route guards
│   │   └── routesConfig.ts        # Centralized route paths, titles, and permission metadata
│   ├── features/                  # Domain-driven feature slices
│   │   ├── auth/                  # Login, Challenge, Onboarding, InviteAccept, ForgotPassword
│   │   ├── decisions/             # Live Decisions feed, Decision Detail Drawer, Rule Breakdown
│   │   ├── cases/                 # Manual Case Review queue, Disposition actions (Approve/Decline)
│   │   ├── rules/                 # Rule List, Rule Builder, Simulator drawer, Maker-Checker diff
│   │   ├── replay/                # Replay run trigger, Candidate vs Baseline diff report
│   │   ├── datasets/              # Dataset Import wizard (CSV/JSONL), Backtest report visualizer
│   │   ├── audit/                 # Merkle audit ledger browser, hash chain verification tool
│   │   └── settings/              # Tenant members, RBAC roles, API keys, Webhooks, Density toggle
│   └── utils/                     # Formatting, validation, and cryptographic helpers
│       ├── currency.ts            # INR/USD currency formatter with tabular digits (`font-feature: tnum`)
│       ├── datetime.ts            # IST timestamp formatter with millisecond precision
│       └── hash.ts                # Client-side SHA-256 for audit hash verification
```

---

## 3. Client-Side Routing and Role-Based Access Control (RBAC)

The console application uses `react-router-dom` (v6) with code-splitting via `React.lazy` and `Suspense`. Every route defines an explicit role access policy mapping to the 5 enterprise roles defined in [Document 08 §4.4](file:///c:/dev/aegis/docs/08-threat-model.md) and [Document 19 §4](file:///c:/dev/aegis/docs/19-identity-and-auth-spec.md).

### 3.1 Role Hierarchy and Permissions Matrix

| Role (`user.role`) | Live Feed (`/decisions`) | Case Queue (`/cases`) | Rule Editor (`/rules`) | Replay / Datasets (`/replay`) | Audit Ledger (`/audit`) | Tenant Admin (`/settings`) |
|---|---|---|---|---|---|---|
| `admin` | View | View | View | View / Run | Full View | Full Admin (Users, Keys) |
| `risk_manager` | View | View / Decide | Create / Approve | View / Run | View | View |
| `risk_analyst` | View | View / Decide | View / Simulate | View / Run | View | None |
| `auditor` | View | View (Read-Only) | View (Read-Only) | View (Read-Only) | Full View + Verify | None |
| `developer` | View | View | View / Simulate | View / Run | View | API Keys / Webhooks |

### 3.2 Route Tree and Guard Implementation

All protected routes are wrapped in a `<RoleGuard allowedRoles={[...]} />` higher-order component. If a user lacks the requisite role claim in their active JWT session, the guard intercepts navigation, renders a non-blocking `403 Forbidden` banner, and displays an action to request privilege elevation or return to the role's default landing page:

```typescript
// Architectural representation of Route Guard Logic (src/routes/RoleGuard.tsx)
// Enforces Section 3.1 RBAC mapping without inventing unapproved routes.
interface RoleGuardProps {
  allowedRoles: Array<'admin' | 'risk_manager' | 'risk_analyst' | 'auditor' | 'developer'>;
  children: JSX.Element;
}

export function RoleGuard({ allowedRoles, children }: RoleGuardProps) {
  const { user, isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <RouteSkeletonLoader />;
  }
  if (!isAuthenticated || !user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (!allowedRoles.includes(user.role)) {
    return <UnauthorizedState requiredRoles={allowedRoles} userRole={user.role} />;
  }
  return children;
}
```

### 3.3 Default Role Dashboard Redirects
Upon successful session establishment (via login or invite completion in [Document 19 §3](file:///c:/dev/aegis/docs/19-identity-and-auth-spec.md)), the user is navigated based on operational focus:
- `risk_analyst` $\rightarrow$ `/cases` (Case Review Queue, high-throughput triage)
- `risk_manager` $\rightarrow$ `/rules` (Rule management, pending maker-checker promotions)
- `auditor` $\rightarrow$ `/audit` (Cryptographic audit ledger and export)
- `developer` $\rightarrow$ `/decisions` (Live telemetry and API latency monitoring)
- `admin` $\rightarrow$ `/settings/members` (Tenant provisioning and user management)

---

## 4. State Management Architecture

State is divided into three distinct operational tiers to prevent state fragmentation, unnecessary component re-renders, and memory accumulation during high-rate event streaming:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        State Management Layers                         │
├────────────────────────────────────────────────────────────────────────┤
│ 1. Server Cache & Remote Data: TanStack Query v5                       │
│    - API resources (Rules, Cases, Replay Runs, Audit Seals, API Keys)  │
│    - Stale-while-revalidate, optimistic updates, garbage collection    │
├────────────────────────────────────────────────────────────────────────┤
│ 2. Ephemeral Real-Time Streaming State: React Context + Ring Buffer    │
│    - Server-Sent Events (SSE) live decisions feed                      │
│    - Fixed-size memory-bounded buffer (max 2,000 items in memory)      │
│    - Pause / Resume streaming accumulator                              │
├────────────────────────────────────────────────────────────────────────┤
│ 3. URL Query Parameter State: React Router (useSearchParams)           │
│    - Active filters (status, min_score, date range, search query)      │
│    - Active drawer / modal selections (?selectedEvent=evt_1234)        │
│    - Enables shareable, deep-linkable URLs for SOC collaboration       │
└────────────────────────────────────────────────────────────────────────┘
```

### 4.1 Server State with TanStack Query
- All REST interactions use custom hooks wrapping TanStack Query (e.g., `useCases()`, `useRule(id)`, `useReplayRun(id)`).
- **Cache Invalidation:** Mutating actions (e.g., submitting a case disposition or approving a rule) invalidate the corresponding query key prefix immediately:
  - `cases.disposition` $\rightarrow$ invalidates `['cases', 'list']` and `['cases', 'detail', id]`.
  - `rules.promote` $\rightarrow$ invalidates `['rules', 'active']` and `['rules', 'detail', id]`.
- **Garbage Collection Budget:** `gcTime` is set to 5 minutes for general metadata and 1 minute for transaction queries to maintain browser memory usage within limits.

### 4.2 URL State as Single Source of Truth
Filter and selection state is never duplicated in local `useState`. When a fraud analyst changes filters or selects a row:
1. The URL updates: `/cases?status=REVIEW&minScore=50&selected=evt_99812`.
2. The VirtualizedTable and TransactionDetailDrawer derive their display directly from URL params.
3. Analysts can copy the URL to Slack or an internal ticket; any recipient with the appropriate role will view the identical state.

---

## 5. API Client and Code Generation

To guarantee absolute contract alignment with the backend and eliminate runtime schema divergence:

1. **Generation Pipeline:** The frontend API SDK is generated directly from the OpenAPI 3.1 specification defined in [Document 06 §3](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md) using `openapi-typescript-codegen` into `src/api/generated/`.
2. **Deterministic Regeneration:** Generation is invoked via `./gradlew :apps:console:generateApiClient` (or `npm run codegen`). Hand-crafting API interfaces in `src/` is strictly prohibited.
3. **Transport Layer Interceptor:** A centralized client instance (`src/api/client.ts`) attaches:
   - `Authorization: Bearer <jwt_access_token>` retrieved from `AuthContext`.
   - `X-Tenant-Id: <tenant_id>` from the user's active organization.
   - `X-Request-Id: <uuidv4>` for end-to-end distributed trace correlation.
4. **RFC 7807 Error Handling:** The client intercepts HTTP 4xx/5xx responses and parses the standard `application/problem+json` envelope defined in [Document 17 §4](file:///c:/dev/aegis/docs/17-backend-design.md), converting them into typed `AegisApiError` exceptions with localized error code mappings.

---

## 6. Real-Time Live Feed Architecture (Server-Sent Events)

The Live Decisions stream (`/decisions`) visualizes high-throughput payment decisions as they occur. 

### 6.1 Protocol Selection: SSE vs. WebSockets
Per Decision [DR-OPS-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) and [Document 06 §3.5](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md), the live feed uses **Server-Sent Events (SSE)** over HTTP/2 rather than WebSockets. Rationale:
- The telemetry feed is strictly unidirectional (server-to-client).
- Standard HTTP/2 multiplexing avoids WebSocket connection state overhead.
- Built-in browser event reconnection and `Last-Event-ID` support.

### 6.2 Stream Lifecycle, Reconnection, and Buffer Management
- **Endpoint:** `GET /v1/events/stream`
- **Authentication:** Standard Bearer token passed in the initial connection handshake or short-lived stream ticket.
- **Client-Side Ring Buffer:** The feed maintains a fixed-capacity circular buffer of **2,000 items** in `SseStreamContext`. When new events arrive, the oldest events roll off, bounding client heap memory to $\le 15$ MB.
- **Pause & Inspect Workflow:**
  - Pressing `Space` toggles the stream pause state.
  - While paused, the SSE connection remains active in the background, but incoming events accumulate in a secondary queue (up to 500 events).
  - An indicator displays `Stream Paused (+142 new events)`. Pressing `Space` resumes and prepends the accumulated items.
- **Reconnection with Exponential Jitter:**
  - If the SSE connection drops, the client reconnects using truncated exponential backoff: $t = \min(1000 \times 2^{\text{attempt}}, 30000) \pm \text{jitter}$.
  - The client includes `Last-Event-ID: <event_id>` in reconnection headers to seamlessly resume telemetry.

---

## 7. Performance and Virtualization Standards

Fraud analysis involves tables containing thousands of items. To guarantee sub-16ms frame times (60 FPS):

### 7.1 Table Virtualization (`@tanstack/react-virtual`)
- Both the Live Feed and Case Queue use `@tanstack/react-virtual`.
- Only visible rows plus a 5-row overscan window are rendered in the DOM (typically 30–40 DOM nodes regardless of 100,000 total items).
- Row heights are strictly driven by the active density mode from [Document 10 §3](file:///c:/dev/aegis/docs/10-design-system-and-ux.md):
  - **Compact Mode:** Exact `28px` fixed row height.
  - **Comfortable Mode:** Exact `44px` fixed row height.
- Dynamic measurement is avoided in high-throughput tables to eliminate layout thrashing.

### 7.2 Monaco Rule Editor Optimization
- The CEL Monaco editor is dynamically imported (`React.lazy`) to prevent bloated bundle sizes on non-rule screens.
- Web Worker execution: CEL AST linting and syntax validation execute within a dedicated Web Worker (`cel-worker.ts`), ensuring typing remains completely stutter-free.

---

## 8. Accessibility Architecture (WCAG 2.1 AA/AAA)

As documented in [Document 10 §6](file:///c:/dev/aegis/docs/10-design-system-and-ux.md), the Aegis console must be accessible to operators with visual, motor, or auditory impairments:

1. **Non-Color Reliance:** Outcomes pair high-contrast tokens with explicit geometric icons and text labels (`[✓] APPROVE`, `[⚑] REVIEW`, `[✕] DECLINE`).
2. **Keyboard Traps Elimination:** Modals and slide-over drawers implement `<FocusTrap>` (using `focus-trap-react`). Focus is trapped within the active modal and restored to the triggering element upon `Esc`.
3. **Screen Reader Live Regions:** The Live Feed container includes an `aria-live="polite"` region that announces high-risk `DECLINE` or `REVIEW` events without interrupting active screen-reader speech.
4. **Contrast Verification:** All text tokens meet or exceed the WCAG 2.1 AA threshold of 4.5:1 for normal text (as verified in [Document 10 §2.1](file:///c:/dev/aegis/docs/10-design-system-and-ux.md)).
5. **Reduced Motion:** If `prefers-reduced-motion: reduce` is detected via CSS media query, all layout transitions, row highlight animations, and chart transitions are immediately disabled.

---

## 9. Comprehensive Screen to API Endpoint Mapping

The following matrix maps every screen defined in [Document 10 §7](file:///c:/dev/aegis/docs/10-design-system-and-ux.md) and new screens required by Decision [D8](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) to their backend endpoints in [Document 06 §3](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md) and [Document 17 §3](file:///c:/dev/aegis/docs/17-backend-design.md):

| Screen ID | Screen Name | Route Path | Primary Components | Backend API Endpoints Called | HTTP Method |
|---|---|---|---|---|---|
| **SCR-01** | User Onboarding / MFA Setup | `/auth/onboarding` | `InviteSetupForm`, `TotpQrScanner`, `OtpInput` | `GET /v1/auth/invite/verify`<br>`POST /v1/auth/invite/accept`<br>`POST /v1/auth/totp/verify` | GET<br>POST<br>POST |
| **SCR-02** | Login & MFA Challenge | `/login`<br>`/auth/challenge` | `LoginForm`, `TotpChallengeModal`, `AlertBanner` | `POST /v1/auth/login`<br>`POST /v1/auth/totp/challenge`<br>`POST /v1/auth/refresh` | POST<br>POST<br>POST |
| **SCR-03** | Password Reset | `/auth/forgot-password`<br>`/auth/reset-password` | `ForgotPasswordForm`, `OtpCountdownInput` | `POST /v1/auth/password/forgot`<br>`POST /v1/auth/password/reset` | POST<br>POST |
| **SCR-04** | Live Decisions Telemetry | `/decisions` | `LiveDecisionTable`, `SseControls`, `MetricStatCard` | `GET /v1/events/stream` (SSE)<br>`GET /v1/decisions` (historical fallback) | GET (SSE)<br>GET |
| **SCR-05** | Case Review Queue | `/cases` | `VirtualCaseTable`, `FilterDrawer`, `BatchActions` | `GET /v1/cases`<br>`POST /v1/cases/export` | GET<br>POST |
| **SCR-06** | Case Detail Drawer | `/cases/:id` | `TransactionDetailDrawer`, `RuleExplainList`, `DispositionForm` | `GET /v1/cases/{id}`<br>`POST /v1/cases/{id}/disposition` | GET<br>POST |
| **SCR-07** | Ruleset Directory | `/rules` | `RulesetTable`, `CanaryStatusPill`, `RuleVersionDiff` | `GET /v1/rulesets`<br>`GET /v1/rulesets/{id}/versions` | GET<br>GET |
| **SCR-08** | Rule Editor & Simulator | `/rules/:id/edit`<br>`/rules/new` | `MonacoRuleEditor`, `SimulationResultDrawer`, `AstSummary` | `GET /v1/rulesets/{id}`<br>`POST /v1/rulesets/simulate`<br>`POST /v1/rulesets/{id}/rules`<br>`PUT /v1/rulesets/{id}/rules/{rule_id}` | GET<br>POST<br>POST<br>PUT |
| **SCR-09** | Maker-Checker Approval | `/rules/approvals` | `PendingApprovalCard`, `CelDiffViewer`, `AuditBanner` | `GET /v1/rulesets/pending-approvals`<br>`POST /v1/rulesets/{id}/approve`<br>`POST /v1/rulesets/{id}/reject` | GET<br>POST<br>POST |
| **SCR-10** | Replay Test Runner | `/replay` | `ReplayConfigForm`, `DatasetSelector`, `RunProgressBar` | `GET /v1/replay/runs`<br>`POST /v1/replay/runs`<br>`GET /v1/replay/runs/{id}/status` | GET<br>POST<br>GET |
| **SCR-11** | Replay Comparison Report | `/replay/:id` | `DecisionShiftMatrix`, `ConfusionComparisonTable`, `LatencyDiffChart` | `GET /v1/replay/runs/{id}/report`<br>`GET /v1/replay/runs/{id}/diff.csv` | GET<br>GET |
| **SCR-12** | Dataset Import (Decision D8) | `/datasets/import` | `FileUploadZone`, `SchemaMapperTable`, `ProgressTracker` | `POST /v1/datasets/import` (Multipart/stream)<br>`GET /v1/datasets/import/{id}/status` | POST<br>GET |
| **SCR-13** | Backtest Comparison Report | `/datasets/:id/report` | `BacktestExecutiveSummary`, `ScoreDistributionGraph`, `DownloadReportButton` | `GET /v1/datasets/{id}/backtest-report`<br>`POST /v1/datasets/{id}/export-pdf` | GET<br>POST |
| **SCR-14** | Audit Ledger & Sealing | `/audit` | `MerkleLedgerTable`, `BatchSealInspector`, `HashVerifier` | `GET /v1/audit/ledger`<br>`GET /v1/audit/batches/{batch_id}/seal`<br>`POST /v1/audit/verify` | GET<br>GET<br>POST |
| **SCR-15** | Tenant Settings & Keys | `/settings` | `ApiKeyList`, `UserTable`, `WebhookConfig`, `AuditExport` | `GET /v1/tenant/members`<br>`POST /v1/tenant/keys`<br>`DELETE /v1/tenant/keys/{id}` | GET<br>POST<br>DELETE |

---

## 9. Frontend Testing and Quality Assurance Strategy

In accordance with [Document 11 §2](file:///c:/dev/aegis/docs/11-test-and-performance-strategy.md), the frontend adheres to a four-tier automated testing strategy:

```
                  ┌────────────────────────┐
                  │ Playwright E2E Tests   │ ~15 critical operational flows
                  │ (Real Browser / Headed)│ (Maker-checker, Replay, Case review)
                  └───────────┬────────────┘
                              │
                  ┌───────────┴────────────┐
                  │ Component Tests        │ React Testing Library + Vitest
                  │ (User Event Simulation)│ (Virtual table, Monaco linter, Forms)
                  └───────────┬────────────┘
                              │
                  ┌───────────┴────────────┐
                  │ Unit Tests             │ Pure utility functions
                  │ (Fast Vitest Runners)  │ (Formatters, Hash verifier, Math)
                  └────────────────────────┘
```

1. **Unit Tests (Vitest):**
   - Currency and tabular number formatters.
   - ISO-8601 UTC to IST timestamp parsers.
   - Client-side SHA-256 Merkle chain verification logic.
2. **Component Integration Tests (React Testing Library + Vitest + MSW):**
   - Mock Service Worker (MSW) intercepts network calls using the exact schemas from [Document 06 §3](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md).
   - Simulates keyboard interactions (`j`, `k`, `Space`, `a`, `d`) on the VirtualizedTable.
   - Tests Monaco CEL editor syntax validation squiggles on malformed expressions.
3. **End-to-End Tests (Playwright):**
   - **Flow 1: Maker-Checker Enforcement:** Verifies that a rule submitted by User A cannot be approved by User A, requiring User B (Risk Manager) to approve.
   - **Flow 2: Free Backtest Ingestion:** Ingests a 10,000-row synthetic CSV via `/datasets/import`, triggers a replay, and asserts that the `/datasets/:id/report` screen renders decision shift metrics.
   - **Flow 3: Case Review Keyboard Navigation:** Tests reviewing 10 consecutive cases using `j`, `k`, and `a`/`d` keyboard shortcuts without mouse clicks.
4. **Accessibility Audits (Axe Core):**
   - Automated `@axe-core/playwright` assertions run on every screen route in CI, enforcing zero WCAG 2.1 AA violations.
