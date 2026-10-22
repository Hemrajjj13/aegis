# Project Aegis — Design System & Visual Specification (DESIGN.md)

**Document:** DESIGN.md (Repository Root Design Contract)  
**Status:** Authoritative Design System for AI Coding Agents and Frontend Developers  
**Design Reference:** Adapted from getdesign.md (Linear Precision + Stripe Trust & Clarity)  
**Last Updated:** October 2026  

---

## 1. Design Philosophy & Principles

Project Aegis is a real-time financial transaction decision engine. The console is built for fraud risk analysts, compliance officers, and rule authors who process thousands of high-stakes payments daily.

### Core Principles
1. **Linear-Style Precision:** High data density, sub-pixel alignment, keyboard-first operations (`Cmd+K`, `j`/`k`), dark-mode default, low cognitive friction.
2. **Stripe-Style Trust & Documentation:** Clear hierarchy, unambiguous status indicators, transparent explainability, and explicit auditability.
3. **Never Rely on Color Alone (Mandatory Accessibility):** Every outcome, risk score, and alert combines color, text, and geometric iconography (`[✓]` Approve, `[⚑]` Review, `[✕]` Decline).
4. **Instant Interaction:** Client transitions are sub-100ms. Tables support virtualized scrolling for 100k+ rows at 60 FPS without layout shifts.

---

## 2. Design Tokens

### 2.1 Color System

The console is **dark by default** with a calibrated **light companion** for bright office environments.

```css
:root {
  /* Dark Theme (Default) */
  --canvas: #0B0E14;          /* Page background */
  --surface: #121722;         /* Cards, tables, panels (contrast 1.15:1 vs canvas) */
  --surface-raised: #1A2130;  /* Popovers, modals, hover states (contrast 1.35:1 vs canvas) */
  --border: #263044;          /* Dividers, card outlines (contrast 2.13:1 vs surface) */
  --text: #E6EAF2;            /* Primary typography (contrast 13.84:1 - AAA) */
  --text-muted: #93A0B8;      /* Secondary typography, metadata (contrast 6.84:1 - AA) */
  --accent: #6C7CFF;          /* Primary actions, active tabs, focus ring (contrast 6.20:1 - AA) */
  --accent-solid: #5061FF;    /* Solid button background with white text (contrast 4.67:1 - AA) */
  --accent-hover: #5A69E8;
  
  /* Semantic Status Tokens (Recomputed via /tools/contrast.py) */
  --approve: #2FBF71;         /* Approved transaction (contrast 8.54:1 - AAA) */
  --approve-bg: rgba(47, 191, 113, 0.12);
  --review: #F5A524;          /* Flagged for manual review (contrast 8.12:1 - AAA) */
  --review-bg: rgba(245, 165, 36, 0.12);
  --decline: #F0556A;         /* Declined / Fraudulent transaction (contrast 4.63:1 - AA) */
  --decline-text: #FF6B81;    /* High contrast text for small pills (contrast 6.13:1 - AA) */
  --decline-bg: rgba(240, 85, 106, 0.12);
  --info: #4DA3FF;            /* Informational callouts (contrast 7.91:1 - AAA) */
  --info-bg: rgba(77, 163, 255, 0.12);
}

[data-theme="light"] {
  --canvas: #F7F8FA;
  --surface: #FFFFFF;
  --surface-raised: #F0F2F6;
  --border: #E2E6EE;
  --text: #0F1626;            /* Primary typography (contrast 16.53:1 - AAA) */
  --text-muted: #5B677F;      /* Secondary typography (contrast 5.41:1 - AA) */
  --accent: #3849D6;          /* Primary actions on surface (contrast 6.94:1 - AA) */
  --accent-solid: #3849D6;    /* Solid button with white text (contrast 6.79:1 - AA) */
  --accent-hover: #2E3DB8;
  
  /* Adjusted to pass WCAG 2.1 AA for normal text (>= 4.5:1) */
  --approve: #137841;         /* Adjusted from #178A4C (contrast 5.54:1 - AA) */
  --approve-bg: rgba(19, 120, 65, 0.10);
  --review: #945900;          /* Adjusted from #B7740A (contrast 5.69:1 - AA) */
  --review-bg: rgba(148, 89, 0, 0.10);
  --decline: #CC2F46;         /* Contrast 5.51:1 - AA */
  --decline-text: #CC2F46;
  --decline-bg: rgba(204, 47, 70, 0.10);
  --info: #1F6FD1;            /* Contrast 5.12:1 - AA */
  --info-bg: rgba(31, 111, 209, 0.10);
}
```

### 2.2 Typography System

```css
/* Font Families */
--font-sans: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
--font-mono: 'JetBrains Mono', Menlo, Consolas, monospace;

/* Monospaced Figures: Mandatory for money, latency, transaction IDs */
.tabular-nums {
  font-family: var(--font-mono);
  font-feature-settings: "tnum" 1, "zero" 1;
}
```

| Token | Size | Weight | Line Height | Tracking | Usage |
|---|---|---|---|---|---|
| `font-display` | 24px | 600 | 32px | -0.02em | Hero headers, Page titles |
| `font-h1` | 18px | 600 | 24px | -0.015em | Panel headings, modal titles |
| `font-h2` | 14px | 600 | 20px | -0.01em | Card headers, table section titles |
| `font-body` | 13px | 400 | 18px | normal | Standard body text, descriptions |
| `font-body-medium` | 13px | 500 | 18px | normal | Active tab text, bold labels |
| `font-caption` | 11px | 500 | 14px | +0.01em | Table column headers, badges, timestamps |
| `font-mono-code` | 12px | 400 | 16px | normal | CEL editor code, raw event JSON |
| `font-mono-num` | 12px | 500 | 16px | normal (tnum) | Amounts (₹), IDs, scores, latencies |

### 2.3 Layout Grid, Spacing & Radii

- **Grid Base:** 4px baseline. Spacing tokens: `4px`, `8px`, `12px`, `16px`, `24px`, `32px`, `48px`.
- **Border Radii:**
  - `radius-sm`: `4px` (tags, badge pills, checkboxes)
  - `radius-md`: `6px` (buttons, input fields, selects)
  - `radius-lg`: `10px` (cards, modal panels, slide-over sheets)
- **Focus Rings:**
  - `outline: 2px solid var(--accent); outline-offset: 2px;`

---

## 3. Density Modes

Fraud risk analysts process hundreds of rows per hour. The console provides two density modes:

- **Compact Mode (Default for Tables & Queues):**
  - Table row height: `28px`.
  - Cell padding: `4px 8px`.
  - Font: `12px` tabular mono / UI.
  - Accommodates ~35 transactions on screen simultaneously.
- **Comfortable Mode (Default for Audit & Settings):**
  - Table row height: `44px`.
  - Cell padding: `12px 16px`.
  - Font: `13px` UI.

---

## 4. Component Catalog

1. **Decision Badge Pill (`<DecisionBadge outcome="APPROVE|REVIEW|DECLINE" />`):**
   - Renders with rounded corners (`4px`), 1px border, filled icon, and bold uppercase text:
     - `APPROVE`: `[✓] APPROVE` (`var(--approve)` border/text, `var(--approve-bg)` background).
     - `REVIEW`: `[⚑] REVIEW` (`var(--review)` border/text, `var(--review-bg)` background).
     - `DECLINE`: `[✕] DECLINE` (`var(--decline)` border, `var(--decline-text)` text, `var(--decline-bg)` background).
   - Mandatory ARIA attribute: `aria-label="Outcome: <Outcome>"`.
2. **Virtualised Transaction Table (`<TransactionTable />`):**
   - `@tanstack/react-virtual` integration for 100k+ row datasets.
   - Fixed header, keyboard `j`/`k` selection highlight, click-to-open drawer.
3. **Monaco CEL Rule Editor (`<CelEditor />`):**
   - Custom CEL tokenizer, syntax error markers, autocompletion for `event.*`, `features.*`, and `lists.*`.
4. **Metric Stat Card (`<StatCard />`):**
   - Headline metric + sparkline trend chart + error budget indicator.
5. **Global Command Palette (`<CommandPalette />`):**
   - Triggered via `Cmd+K` / `Ctrl+K`. Instant keyboard search across entities, rules, and actions.

---

## 5. Keyboard Navigation Standards

| Key Binding | Target Scope | Action |
|---|---|---|
| `Cmd + K` / `Ctrl + K` | Global | Open command palette |
| `/` | Global | Focus search filter bar |
| `j` / `k` | Table / List | Move selection down / up |
| `Enter` / `Space` | Table Row | Open transaction details drawer |
| `Esc` | Modal / Drawer | Close active overlay |
| `a` | Case Review Drawer | Mark transaction **Approved** |
| `d` | Case Review Drawer | Mark transaction **Declined** |
| `r` | Case Review Drawer | Escalate to **Senior Review** |
| `c` | Rule Editor | Run simulation on current draft rule |
| `g then d` | Global | Jump to **Live Decisions** |
| `g then q` | Global | Jump to **Case Queue** |
| `g then r` | Global | Jump to **Rule Editor** |
| `g then b` | Global | Jump to **Replay Backtest** |

---

## 6. Accessibility & Compliance Rules (WCAG 2.1 AA/AAA)

1. **Color Independence:** Color is never the single differentiator of state. Decisions always include geometric icons (`[✓]`, `[⚑]`, `[✕]`) and uppercase labels.
2. **Contrast Standards:** All text elements meet or exceed WCAG 2.1 AA (4.5:1 for normal text, 3:1 for large text); critical primary text meets AAA (7:1).
3. **Keyboard Focusability:** All interactive components have a visible 2px focus ring. No trapped keyboard focus.
4. **Motion Preferences:** `@media (prefers-reduced-motion: reduce)` disables all animations, live feed transitions, and chart animations.

---

## 7. UX Flows & Wireframes

### 7.1 Flow 1: Invite and User Onboarding
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Complete Account Setup (Acme Fintech)                                       │
│                                                                             │
│ Full Name:          [ Vikram Sharma                         ]               │
│ Work Email:         vikram.sharma@acme.example (Read-only)                  │
│ Password:           [ ••••••••••••••••••••••                ]               │
│ Confirm Password:   [ ••••••••••••••••••••••                ]               │
│                                                                             │
│ Set Up Multi-Factor Authentication (Mandatory)                              │
│ [  ████████████  ]   Scan with Google Authenticator or 1Password             │
│ [  ██  ██  ████  ]   Manual code: K5TG-88XA-MN90-PQ42                       │
│ [  ████████████  ]                                                          │
│                                                                             │
│ Enter 6-digit TOTP code: [ 8 ][ 4 ][ 1 ][ 9 ][ 0 ][ 2 ]                     │
│ [ Complete Registration ]                                                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 Flow 2: Login and Password Reset with OTP
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Sign In to Aegis Console                                                    │
│                                                                             │
│ Email:     [ ananya.patel@acme.example             ]                        │
│ Password:  [ •••••••••••••••••                     ]                        │
│ 2FA Code:  [ 4 ][ 9 ][ 2 ][ 1 ][ 1 ][ 8 ]                                   │
│                                                                             │
│ [ Sign In ]                                        [ Reset Password (OTP) ] │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.3 Flow 3: Live Decisions Real-Time Feed
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ LIVE DECISIONS  ● 2,140 req/s  p50: 18ms  p99: 42ms [ILLUSTRATIVE MOCK]     │
├─────────────────────────────────────────────────────────────────────────────┤
│ TIME (IST) │ STATUS     │ EVENT ID    │ ENTITY HASH │ AMOUNT (INR) │ LATENCY│
├────────────┼────────────┼─────────────┼─────────────┼──────────────┼────────┤
│ 14:05:22.1 │ [✓] APPROVE│ evt_098412  │ a981..bc02  │ ₹    1,250.00│   14 ms│
│ 14:05:22.0 │ [⚑] REVIEW │ evt_098411  │ f112..99a1  │ ₹   75,000.00│   28 ms│
│ 14:05:21.9 │ [✕] DECLINE│ evt_098410  │ c443..12df  │ ₹  120,000.00│   19 ms│
│ 14:05:21.8 │ [✓] APPROVE│ evt_098409  │ d099..88ef  │ ₹      450.00│   11 ms│
├────────────┴────────────┴─────────────┴─────────────┴──────────────┴────────┤
│ 35 transactions shown [ILLUSTRATIVE MOCK DATA] | Press 'j'/'k' to select    │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.4 Flow 4: Case Review Queue and Detail Drawer
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ TRANSACTION DETAIL: evt_098411 [ILLUSTRATIVE MOCK DATA]          [ Close X ]│
├─────────────────────────────────────────────────────────────────────────────┤
│ Outcome:      [⚑] REVIEW (Score: 68.0 / 100.0) [MOCK] | Latency: 28 ms      │
│ Account Hash: f112..99a1  | Amount: ₹75,000.00 | UPI (MCC: 5732)            │
├─────────────────────────────────────────────────────────────────────────────┤
│ EXPLAINABILITY — RULES FIRED (3) [ILLUSTRATIVE MOCK DATA]                   │
│ 1. RUL-AMT-002: Amount Spike > 5x 30d Avg          +28.0 pts  [View Rule]   │
│ 2. RUL-TIME-003: Late-Night High-Value Transfer     +20.0 pts  [View Rule]   │
│ 3. RUL-DEV-006: Rapid Device Switching              +20.0 pts  [View Rule]   │
│ Total Score: 28.0 + 20.0 + 20.0 = 68.0 (Review >= 40.0, Decline >= 75.0)   │
├─────────────────────────────────────────────────────────────────────────────┤
│ ANALYST ACTION (Shortcuts: 'a' Approve | 'd' Decline | 'r' Escalate)        │
│ Notes: [ Verified via customer callback. Genuine purchase.                ] │
│ [ (a) Approve ]            [ (d) Decline as Fraud ]         [ (r) Escalate ]│
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.5 Flow 5: Rule Authoring to Maker-Checker Approval
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ RULE EDITOR: rs_card_ecommerce_v1 > RUL-GEO-004                   [ Draft ] │
│ Name: [ High-Risk MCC with Foreign IP ]   Weight: [ +40.0 ]                 │
├─────────────────────────────────────────────────────────────────────────────┤
│ CEL EXPRESSION:                                                             │
│ 1  event.mcc in ['7995', '6211', '6051'] &&                                 │
│ 2  event.device.ip_country != 'IN' &&                                       │
│ 3  event.amount_inr >= 5000.0                                               │
├─────────────────────────────────────────────────────────────────────────────┤
│ SIMULATION RESULTS (10k txns) [ILLUSTRATIVE MOCK DATA]:                     │
│ Trigger Rate: 1.42% | FPR: 0.12% | Lat: 0.04 ms                             │
├─────────────────────────────────────────────────────────────────────────────┤
│ [ Run Simulation (c) ]     [ Save Draft ]     [ Submit for Approval ]       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.6 Flow 6: Replay Comparison (Version A vs. Version B)
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ REPLAY COMPARISON: Run #rep_88921                          [ COMPLETED ✓ ]  │
│ Dataset: Sept 2026 Ground Truth (1M txns) | Speed: 18,500 txns/s [MOCK]     │
├─────────────────────────────────────────────────────────────────────────────┤
│ BASELINE: v1.2 (Hash: e8a1..bc90)  vs.  CANDIDATE: v1.3 (Hash: 44f2..11a8)  │
├─────────────────────────────────────────────────────────────────────────────┤
│ DECISION SHIFTS [ILLUSTRATIVE MOCK DATA]:                                   │
│ Approve -> Decline: +1,420 (Newly caught fraud)                             │
│ Decline -> Approve:   -310 (Reduced false positives)                        │
│ Unchanged:         998,270 (99.83% agreement)                               │
│ Divergence:          0.02% (Expected concurrency bound < 0.1%)               │
├─────────────────────────────────────────────────────────────────────────────┤
│ METRICS [ILLUSTRATIVE MOCK DATA]:                                           │
│ Metric               │ Version A (Live)     │ Version B (Candidate)│ Delta  │
│ ─────────────────────┼──────────────────────┼──────────────────────┼─────── │
│ Fraud Recall         │ 88.4%                │ 94.2%                │ +5.8%  │
│ False Positive Rate  │ 1.82%                │ 1.24%                │ -0.58% │
│ p99 Latency          │ 22.1 ms              │ 23.4 ms              │ +1.3 ms│
├─────────────────────────────────────────────────────────────────────────────┤
│ [ Download Diff CSV ]      [ Export Audit Proof ]      [ Promote Candidate ]│
└─────────────────────────────────────────────────────────────────────────────┘
```
