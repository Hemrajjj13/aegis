# Design System and UX Architecture

**Document:** 10  
**Status:** Approved (docs v1.0, assumption-based)
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

> **Assumption note:** Visual tokens, density configurations, and interaction workflows in this document are tailored for fraud risk analysts, compliance officers, and rule authors. Brand elements and color adjustments marked `[interview-driven]` will be validated during design partner prototype walkthroughs.

---

## 1. Design Philosophy: Linear Precision Meets Stripe Clarity

Aegis is an enterprise risk platform where operational efficiency directly impacts fraud losses. Fraud analysts process hundreds of suspicious payments daily, while risk managers make high-stakes rule promotion decisions.

The design system blends two established enterprise archetypes:
1. **Linear Precision (Console Core):** High information density, keyboard-first navigation (`Cmd+K`, `j`/`k`), dark-mode default, low-latency client transitions, and sub-pixel alignment.
2. **Stripe Trust and Clarity (Rule Authoring & Documentation):** Explicit typography, unambiguous status visualization, clear diff rendering, and audit transparency.

### Core UX Tenets
- **Data Density Without Clutter:** Fraud analysts live in tables. Every pixel must convey actionable context. Generous whitespace is replaced with disciplined compact typography and tabular alignment.
- **Keyboard-First Workflow:** An analyst should be able to review, flag, approve, or decline 50 transactions without lifting their hands from the keyboard.
- **Never Rely on Color Alone:** Accessible by design. Decisions always combine a distinct color token, an explicit text label, and a recognizable geometric icon (`[✓]`, `[⚑]`, `[✕]`).
- **Explainability Over Magic:** The interface never displays a raw score without decomposing the exact rules that fired, their numerical weights, and contributing features.

---

## 2. Design Tokens

### 2.1 Color Palette

Theme is **dark by default** (minimizing eye strain in 24/7 security operations centers), accompanied by a calibrated **light companion**.

| Token Name | Dark Value | Light Value | Semantic Role | Contrast Ratio (vs Background) | WCAG Status |
|---|---|---|---|---|---|
| `canvas` | `#0B0E14` | `#F7F8FA` | Base application background | — | Baseline |
| `surface` | `#121722` | `#FFFFFF` | Cards, panels, table rows | 1.15:1 (dark), 1.05:1 (light) | Surface |
| `surface-raised`| `#1A2130` | `#F0F2F6` | Popovers, modal dialogs, hover | 1.35:1 (dark), 1.09:1 (light) | Elevated |
| `border` | `#263044` | `#E2E6EE` | Card dividers, table borders | 2.13:1 (vs surface) | Visible structure |
| `text` | `#E6EAF2` | `#0F1626` | Primary headings, body copy | 13.84:1 (dark), 16.53:1 (light) | **AAA** (Pass) |
| `text-muted` | `#93A0B8` | `#5B677F` | Secondary labels, timestamps | 6.84:1 (dark), 5.41:1 (light) | **AA** (Pass) |
| `accent` | `#6C7CFF` | `#3849D6` | Primary buttons, active tabs, focus ring | 6.20:1 (dark on surface), 6.94:1 (light on surface) | **AA** (Pass) |
| `accent-solid` | `#5061FF` | `#3849D6` | Solid button background (white text) | 4.67:1 (white on dark), 4.52:1 (white on light) | **AA** (Pass) |
| `approve` | `#2FBF71` | `#137841` | Approved outcome, passing tests | 8.54:1 (dark), 5.54:1 (light) | **AA / AAA** (Pass)|
| `review` | `#F5A524` | `#945900` | Flagged for manual review, warnings | 8.12:1 (dark), 5.69:1 (light) | **AA / AAA** (Pass)|
| `decline` | `#F0556A` | `#CC2F46` | Declined outcome, critical blocks | 4.63:1 (dark), 5.51:1 (light) | **AA** (Pass) |
| `decline-text` | `#FF6B81` | `#CC2F46` | Small badge pill text on tinted bg | 6.13:1 (dark on surface), 5.51:1 (light) | **AA** (Pass) |
| `info` | `#4DA3FF` | `#1F6FD1` | Informational callouts, active filters | 7.91:1 (dark), 5.12:1 (light) | **AA / AAA** (Pass)|

*Contrast Ratio & WCAG 2.1 Verification Note (recomputed via `/tools/contrast.py`):*
- Light Approve adjusted from `#178A4C` (4.43:1, failed AA) to `#137841` (5.54:1 vs white `#FFFFFF`, passes AA).
- Light Review adjusted from `#B7740A` (4.15:1, failed AA) to `#945900` (5.69:1 vs white `#FFFFFF`, passes AA).
- Dark Accent `#6C7CFF`: text `#0B0E14` on `#6C7CFF` achieves 5.49:1 (passes AA). For buttons requiring white text `#FFFFFF`, adjusted token `--accent-solid` is `#5061FF` (4.67:1 vs white, passes AA) or `#3849D6` in light theme (4.52:1 vs white, passes AA).
- Decline on dark surface (`#121722`): `#F0556A` yields 4.63:1 (passes AA normal text); small badge typography uses `#FF6B81` at 6.13:1 contrast against surface. All text tokens pass WCAG 2.1 AA for normal text (≥ 4.5:1).

### 2.2 Typography System

- **UI & Display Font:** **Inter** (`Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`).
- **Monospaced Font:** **JetBrains Mono** (`"JetBrains Mono", Menlo, Consolas, monospace`).
  - Mandatory for: Transaction IDs, Card Tokens, Currency Amounts, Latency Metrics, IP addresses, and CEL DSL code blocks.
  - Enabled with `font-feature-settings: "tnum"` (tabular figures) so numbers align vertically across table rows.

| Style Role | Font Family | Size | Weight | Line Height | Letter Spacing |
|---|---|---|---|---|---|
| `display` | Inter | 24px | 600 (SemiBold) | 32px | -0.02em |
| `heading-1` | Inter | 18px | 600 (SemiBold) | 24px | -0.015em |
| `heading-2` | Inter | 14px | 600 (SemiBold) | 20px | -0.01em |
| `body` | Inter | 13px | 400 (Regular) | 18px | normal |
| `body-medium` | Inter | 13px | 500 (Medium) | 18px | normal |
| `caption` | Inter | 11px | 500 (Medium) | 14px | +0.01em |
| `mono-code` | JetBrains Mono | 12px | 400 (Regular) | 16px | normal |
| `mono-tabular`| JetBrains Mono | 12px | 500 (Medium) | 16px | normal (tnum) |

### 2.3 Grid, Spacing, and Elevation
- **4px Base Grid:** All margins, paddings, and component dimensions are multiples of 4px (`4px`, `8px`, `12px`, `16px`, `24px`, `32px`).
- **Border Radii:**
  - `radius-sm`: `4px` (tags, badge pills, checkboxes)
  - `radius-md`: `6px` (buttons, input fields, dropdown triggers)
  - `radius-lg`: `10px` (cards, modal panels, slide-over sheets)
- **Shadows & Elevation:**
  - `elevation-0`: Flat surface with 1px border (`border: 1px solid #263044`).
  - `elevation-1`: Hover states (`box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25)`).
  - `elevation-2`: Popovers & Modals (`box-shadow: 0 12px 32px rgba(0, 0, 0, 0.45)`).

---

## 3. Density Modes

Because fraud analysts examine large volumes of transaction tabular data while administrators configure system settings, Aegis supports two user-selectable density modes:

```
Comfortable Mode (Row Height: 44px)
┌────────────────────────────────────────────────────────────────────────┐
│ [✓] APPROVE  │ evt_89412 │ ₹12,450.00 │ card_tok_9912 │ 12 ms │ 14:02:11│
└────────────────────────────────────────────────────────────────────────┘

Compact Mode (Row Height: 28px) — Default for Risk Analysts
┌────────────────────────────────────────────────────────────────────────┐
│[✓] APPROVE │ evt_89412 │ ₹12,450.00 │ card_tok_9912 │ 12ms │ 14:02:11  │
└────────────────────────────────────────────────────────────────────────┘
```

- **Compact Mode (Default for Case Queue & Tables):**
  - Row height: `28px`.
  - Cell padding: `4px 8px`.
  - Font size: `12px` monospaced / `12px` UI.
  - Allows ~35 transactions visible simultaneously on a standard 1080p display without scrolling.
- **Comfortable Mode (Default for Audit Log & Settings):**
  - Row height: `44px`.
  - Cell padding: `12px 16px`.
  - Font size: `13px`.
  - Optimized for touch devices or executive metric overviews.

---

## 4. Component Catalog

1. **Decision Badge Pill:**
   - Visual: Rounded pill with 1px border, filled icon, and bold uppercase outcome.
   - Variations:
     - `Approve`: Green text `#2FBF71`, green background `rgba(47, 191, 113, 0.12)`, icon `[✓]`.
     - `Review`: Orange text `#F5A524`, orange background `rgba(245, 165, 36, 0.12)`, icon `[⚑]`.
     - `Decline`: Red text `#F0556A`, red background `rgba(240, 85, 106, 0.12)`, icon `[✕]`.
2. **Virtualised Data Table:**
   - Powered by `@tanstack/react-virtual`.
   - Smoothly scrolls 100,000+ transaction rows at 60 FPS.
   - Sticky header, column resizing, click-to-sort, and multi-column filter drawers.
3. **CEL Rule Editor:**
   - Powered by Monaco Editor with custom CEL language syntax highlighting, auto-completion of `event.*` and `features.*` variables, inline syntax error squiggles, and AST validation.
4. **Metric Stat Card:**
   - Displays real-time KPIs (e.g., "p99 Latency: 24 ms", "Approval Rate: 94.2%").
   - Includes sparkline trend micro-chart (last 60 minutes) and error-budget burn indicator.
5. **Global Command Palette (`Cmd+K` / `Ctrl+K`):**
   - Instant search across transactions, rules, documentation, and tenant settings.
   - Action launcher for common tasks (`Create Rule`, `Export Audit Log`, `Toggle Dark/Light`).

---

## 5. Keyboard Navigation and Shortcuts

The console is fully operable via keyboard shortcuts:

| Shortcut | Scope | Action |
|---|---|---|
| `Cmd + K` / `Ctrl + K` | Global | Open command palette |
| `/` | Global | Focus table search filter |
| `j` / `k` | Table / Case Queue | Move selection to next / previous transaction row |
| `Enter` or `Space` | Table / Case Queue | Open selected transaction detail drawer |
| `Esc` | Global | Close drawer, modal, or popover |
| `a` | Case Review Drawer | Mark transaction as **Approved** (confirmed legitimate) |
| `d` | Case Review Drawer | Mark transaction as **Declined / Fraud** |
| `r` | Case Review Drawer | Escalate to **Senior Review** |
| `c` | Rule Editor | Trigger **Simulate / Compile** on current draft rule |
| `g then d` | Global | Navigate to **Live Decisions** |
| `g then q` | Global | Navigate to **Case Review Queue** |
| `g then r` | Global | Navigate to **Rule Editor** |
| `g then b` | Global | Navigate to **Replay Backtest** |

---

## 6. Accessibility & Compliance Standards (WCAG 2.1 AA/AAA)

1. **Non-Color Dependent Status:** Color is never used as the sole indicator of state. Every decision, alert, and rule status pairs color with text and distinctive icons.
2. **Keyboard Focus Ring:** Every interactive element features a prominent 2px focus ring (`outline: 2px solid #6C7CFF; outline-offset: 2px;`) that is clearly visible in both light and dark modes.
3. **Screen Reader Optimization:**
   - ARIA live regions (`aria-live="polite"`) announce new incoming transactions on the live decision feed without stealing focus.
   - Outcome badges include `role="status"` and `aria-label="Outcome: Approve"`.
4. **Reduced Motion:** Respects user operating system preference (`@media (prefers-reduced-motion: reduce)`): disables all CSS transitions, live feed animations, and chart transitions.

---

## 7. UX Flows and Text Wireframes

### 7.1 Flow 1: Invite and User Onboarding
```
Step 1: Admin sends invite (Email + Role)
Step 2: User receives email with single-use cryptographic token link (24h expiry)
Step 3: User lands on onboarding screen: enters name, sets password, scans TOTP MFA QR code
Step 4: User confirms MFA token -> redirected to role-tailored dashboard
```

#### Wireframe: User Onboarding / MFA Setup Screen
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ AEGIS                                                      [Dark Mode] [IN] │
│                                                                             │
│                        Welcome to Project Aegis                             │
│                  Complete your account setup for Acme Fintech               │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ Full Name:          [ Vikram Sharma                         ]         │  │
│  │ Email Address:      vikram.sharma@acme.example (Read-only)            │  │
│  │ Create Password:    [ ••••••••••••••••••••••                ]         │  │
│  │ Confirm Password:   [ ••••••••••••••••••••••                ]         │  │
│  │                                                                       │  │
│  │ Set Up Two-Factor Authentication (Mandatory)                          │  │
│  │ Scan the QR code using Google Authenticator, 1Password, or Authy:     │  │
│  │                                                                       │  │
│  │        [  ██████████████  ]    Or enter secret manually:              │  │
│  │        [  ██  ██  ██  ██  ]    K5TG-88XA-MN90-PQ42                    │  │
│  │        [  ██████████████  ]                                           │  │
│  │                                                                       │  │
│  │ Enter 6-digit verification code:                                      │  │
│  │ [ 8 ][ 4 ][ 1 ][ 9 ][ 0 ][ 2 ]                                        │  │
│  │                                                                       │  │
│  │ [ Submit & Complete Setup ]                                           │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 7.2 Flow 2: Login and Password Reset with OTP
```
Login:     Email + Password -> Prompt for 6-Digit TOTP MFA -> Authenticated
Reset:     Forgot Password -> Email Link + SMS OTP (15m expiry) -> Reset Password
```

#### Wireframe: Login with MFA Challenge
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ AEGIS                                                                       │
│                                                                             │
│                         Sign In to Aegis Console                            │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ Work Email:         [ ananya.patel@acme.example             ]         │  │
│  │ Password:           [ •••••••••••••••••                     ]         │  │
│  │                                                                       │  │
│  │ Two-Factor Authentication Code:                                       │  │
│  │ [ 4 ][ 9 ][ 2 ][ 1 ][ 1 ][ 8 ]   (Expires in 22s)                     │  │
│  │                                                                       │  │
│  │ [ Sign In ]                                  [ Forgot Password? ]     │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  Protected by Multi-Factor Authentication (TOTP).                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 7.3 Flow 3: Live Decisions Real-Time Feed
```
Real-time transaction stream updating via WebSockets / SSE. Displays decisions
as they execute. Keyboard shortcut 'Space' pauses stream; 'j'/'k' selects rows.
```

#### Wireframe: Live Decisions Screen
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ AEGIS  [Acme Fintech ▼]   Decisions  Case Queue  Rules  Replay  Audit  [Cmd+K]│
├─────────────────────────────────────────────────────────────────────────────┤
│ LIVE TRANSACTIONS  ● 2,140 req/s  p50: 18ms  p99: 42ms [ILLUSTRATIVE MOCK]   │
│ Filter: [ Status: All ▼ ] [ Min Score: 0 ] [ Amount > ₹0 ] [ Search ID... ] │
├─────────────────────────────────────────────────────────────────────────────┤
│ TIME (IST) │ STATUS     │ EVENT ID    │ ENTITY HASH │ AMOUNT (INR) │ LATENCY│
├────────────┼────────────┼─────────────┼─────────────┼──────────────┼────────┤
│ 14:05:22.1 │ [✓] APPROVE│ evt_098412  │ a981..bc02  │ ₹    1,250.00│   14 ms│
│ 14:05:22.0 │ [⚑] REVIEW │ evt_098411  │ f112..99a1  │ ₹   75,000.00│   28 ms│
│ 14:05:21.9 │ [✕] DECLINE│ evt_098410  │ c443..12df  │ ₹  120,000.00│   19 ms│
│ 14:05:21.8 │ [✓] APPROVE│ evt_098409  │ d099..88ef  │ ₹      450.00│   11 ms│
│ 14:05:21.8 │ [✓] APPROVE│ evt_098408  │ b121..77c0  │ ₹    2,100.00│   15 ms│
│ 14:05:21.7 │ [⚑] REVIEW │ evt_098407  │ e982..33ba  │ ₹   45,000.00│   32 ms│
├─────────────────────────────────────────────────────────────────────────────┤
│ 35 transactions shown [ILLUSTRATIVE MOCK DATA] | Press 'j'/'k' to select    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 7.4 Flow 4: Case Review Queue and Detail Drawer
```
Analyst navigates queue of 'REVIEW' transactions, inspects features and rules
that fired, and submits final disposition ('a' Approve / 'd' Decline).
```

#### Wireframe: Transaction Detail Slide-Over Drawer
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ TRANSACTION DETAIL: evt_098411 [ILLUSTRATIVE MOCK DATA]           [ Close X]│
├─────────────────────────────────────────────────────────────────────────────┤
│ Outcome:      [⚑] REVIEW (Score: 68.0 / 100.0) [ILLUSTRATIVE MOCK DATA]     │
│ Event Time:   2026-10-21 14:05:22 IST  | Latency: 28 ms | Degraded: No      │
│ Account Hash: f112..99a1               | Amount: ₹75,000.00 (INR)           │
│ Payment Type: UPI (Merchant: Electronics Megastore, MCC: 5732)              │
├─────────────────────────────────────────────────────────────────────────────┤
│ EXPLAINABILITY — RULES FIRED (3) [ILLUSTRATIVE MOCK DATA]                   │
│ 1. RUL-AMT-002: Amount Spike > 5x 30d Avg          +28.0 pts  [View Rule]   │
│    Reason: Historical avg ₹8,200.00; current txn ₹75,000.00 (9.1x)          │
│ 2. RUL-TIME-003: Late-Night High-Value Transfer     +20.0 pts  [View Rule]   │
│    Reason: Transaction executed at 02:14 IST (> ₹50,000)                    │
│ 3. RUL-DEV-006: Rapid Device Switching              +20.0 pts  [View Rule]   │
│    Reason: 3 distinct device fingerprints observed in last 60 minutes       │
│                                                                             │
│ Score Calculation: 28.0 + 20.0 + 20.0 = 68.0 / 100.0 (Review >= 40, Dec >= 75)│
├─────────────────────────────────────────────────────────────────────────────┤
│ ANALYST ACTION (Keyboard: 'a' Approve | 'd' Decline | 'r' Escalate)        │
│ Notes: [ Customer verified via outbound callback OTP. Legitimate purchase. ]│
│                                                                             │
│ [ (a) Approve Transaction ]   [ (d) Decline as Fraud ]   [ (r) Escalate ]   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 7.5 Flow 5: Rule Authoring to Maker-Checker Approval
```
Step 1: Rule Author edits CEL expression in Monaco editor with live autocomplete.
Step 2: Author clicks "Simulate": runs draft against 10k synthetic events.
Step 3: Author submits for approval. Ruleset enters state 'SUBMITTED'.
Step 4: A different user (Risk Manager) reviews diff and approves or rejects.
        (Maker-checker prevents author from approving their own rule).
Step 5: Risk Manager can promote to Canary (1%, 5%, 10%) or Full Live.
```

#### Wireframe: Rule Editor with Simulation & Diff View
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ RULE EDITOR: rs_card_ecommerce_v1 > RUL-GEO-004                   [ Draft ] │
├─────────────────────────────────────────────────────────────────────────────┤
│ Name:        [ High-Risk MCC with Foreign IP                  ]             │
│ Rule Type:   (●) Soft Rule (Scoring)    ( ) Hard Rule (Override)            │
│ Weight:      [ +40.0 ]   Reason Code: [ HIGH_RISK_MCC_CROSS_BORDER_IP     ] │
├─────────────────────────────────────────────────────────────────────────────┤
│ CEL CONDITION (Google Common Expression Language):                          │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ 1  // Flag international IPs transacting at high-risk MCCs              │ │
│ │ 2  event.mcc in ['7995', '6211', '6051'] &&                             │ │
│ │ 3  event.device.ip_country != 'IN' &&                                   │ │
│ │ 4  event.amount_inr >= 5000.0                                           │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│ Syntax: Valid CEL AST | Complexity: 6 nodes (Max 250)                       │
├─────────────────────────────────────────────────────────────────────────────┤
│ SIMULATION RESULTS (10k txns) [ILLUSTRATIVE MOCK DATA]:                     │
│ - Rule Trigger Rate: 1.42% (142 txns fired)                                 │
│ - Impact on Approval Rate: -0.8% (Estimated False Positive Rate: 0.12%)     │
│ - Estimated p99 Eval Latency: 0.04 ms                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│ [ Run Simulation (c) ]     [ Save Draft ]     [ Submit for Approval ]       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 7.6 Flow 6: Replay Comparison (Version A vs. Version B)
```
Risk Manager selects historical dataset (e.g., "September 2026 E-commerce"),
chooses Baseline Ruleset (Live v1.2) and Candidate Ruleset (Draft v1.3),
and executes deterministic replay. Displays changed decisions and metrics.
```

#### Wireframe: Replay Comparison Report Screen
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ REPLAY COMPARISON: Run #rep_88921                          [ COMPLETED ✓ ]  │
│ Dataset: Sept 2026 Ground Truth (1M txns) | Speed: 18,500 txns/s [MOCK]     │
├─────────────────────────────────────────────────────────────────────────────┤
│ BASELINE: v1.2 (Hash: e8a1..bc90)  vs.  CANDIDATE: v1.3 (Hash: 44f2..11a8)  │
├─────────────────────────────────────────────────────────────────────────────┤
│ DECISION SHIFTS (Transitions) [ILLUSTRATIVE MOCK DATA]:                     │
│ Approve -> Decline:  +1,420 (Newly caught fraud)                            │
│ Decline -> Approve:  -310   (Reduced false alarms)                          │
│ Unchanged Decisions: 998,270 (99.83% agreement)                             │
│ Live Divergence Pct: 0.02% (Concurrency-order expected bound < 0.1%)        │
├─────────────────────────────────────────────────────────────────────────────┤
│ PERFORMANCE & CONFUSION MATRIX COMPARISON [ILLUSTRATIVE MOCK DATA]:         │
│ Metric               │ Version A (Live)     │ Version B (Candidate)│ Delta  │
│ ─────────────────────┼──────────────────────┼──────────────────────┼─────── │
│ Fraud Recall         │ 88.4%                │ 94.2%                │ +5.8%  │
│ False Positive Rate  │ 1.82%                │ 1.24%                │ -0.58% │
│ p50 Eval Latency     │ 8.4 ms               │ 8.9 ms               │ +0.5 ms│
│ p99 Eval Latency     │ 22.1 ms              │ 23.4 ms              │ +1.3 ms│
├─────────────────────────────────────────────────────────────────────────────┤
│ [ Download Full Diff CSV ]    [ Export Audit Report ]   [ Promote Candidate]│
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Decision Register References (formerly Open Questions)

All UX and design system questions have been resolved or classified per Decision **D3** in the [Decision Register](file:///c:/dev/aegis/docs/00-decisions/decision-register.md):

| # | Decision ID | Question / Topic | Status | Decision / Assumed Value |
|---|---|---|---|---|
| 1 | [DR-DES-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Default Density Preference:** Compact vs Comfortable mode for analyst queues. | Decided | Compact mode (28px row) default for Risk Analysts on Case Queue; Comfortable mode (44px) default on Audit and Settings. |
| 2 | [DR-DES-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Sound Effects for Live Feed Alerts:** Audio pings in browser SOC monitoring. | Decided | Audio pings disabled by default; toggleable in user settings with browser notification permission. |
| 3 | [DR-DES-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Custom Branding / White-Labeling:** Logo and theme color overrides in console. | Decided | Tenant logo and tenant name displayed in header; custom CSS color theming deferred to enterprise roadmap. |
| 4 | [DR-DES-04](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Multi-Monitor Layouts:** Detaching live feeds into separate windows. | Decided | Browser-native multi-window support via deep URL routing (`/live-feed`, `/queue`); popup detaching deferred. |

