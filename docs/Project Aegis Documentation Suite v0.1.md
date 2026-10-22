# Project Aegis: Real-Time Transaction Decision Engine

**Status:** Historical Founding Document Suite (v0.1)  
**Last updated:** October 2026  

*Documentation suite v0.1 for Hemraj Suryawanshi, October 2026. 'Aegis' is a placeholder name; check trademarks before using it.*

## 0. Read this first

Three framing points that shape everything below.

1. **This is written as if a 30+ person team builds it.** In practice you are one person with coding agents. That works if each squad below becomes a workstream and each document becomes the contract an agent builds from. Docs first is the right call: an agent is only as good as the spec it reads.
2. **'Production grade' is proven with evidence, not a feature list.** Load tests, failure drills, security review, audit exports. Each section says how it gets verified.
3. **Synthetic data proves the engineering, not the detection quality.** A replay tool that scores well on data you generated yourself shows the machinery works. Credibility on real fraud comes only from design partners and real labeled data. The plan is built around that.

All latency, throughput and cost numbers are hypotheses to validate with load tests. I did not pull market-size figures; section 2 lists where to get them.

## 1. Product thesis

**Aegis is the decision layer between a payment and its outcome.** It decides approve, decline or review in milliseconds, explains every decision, and lets a risk team test a rule change against history before it ships.

**The wedge:** rules as versioned, testable, replayable code. Think 'CI for fraud rules'. Most risk teams are more afraid of changing a rule than of fraud, because they cannot see the blast radius. Aegis removes that fear.

|  |  |
| --- | --- |
| Buyer | Head of Risk or Fraud, Head of Payments, CTO at a fintech |
| Daily users | Fraud analysts, rule authors, risk managers, auditors |
| Integrators | Backend engineers calling the Decision API |
| Not in scope (stage 1) | Moving money, KYC, chargeback handling, black-box ML |

## 2. Market research

### 2.1 Where to get real numbers

Pull these before any investor or customer conversation: RBI annual report and fraud-reporting data, NPCI UPI volume statistics, I4C and NCRB cybercrime data, Nilson Report for card fraud, PCI SSC guidance, public filings and investor decks of listed players (Riskified, Sift-type vendors), analyst reports (Gartner, Forrester, Everest, mostly paywalled), competitor pricing pages, G2 and Capterra reviews, and **20 to 30 customer discovery interviews**. The interviews matter more than every other source combined.

### 2.2 Segments and starting ICP

| Segment | Core pain | Fit |
| --- | --- | --- |
| Payment aggregators and gateways | Merchant fraud, chargebacks, high volume | High, but long sales cycles |
| Fintech lenders, BNPL, wallets | Transaction fraud, mule accounts | **High** |
| Neobanks and small banks | Rules buried in core banking | Medium, procurement is slow |
| Marketplaces and e-commerce | Checkout fraud, promo and return abuse | Medium, strong incumbents |
| Gig, gaming, crypto platforms | Promo abuse, account farming | Medium, compliance varies |

**Starting ICP:** mid-size Indian fintechs and payment companies, roughly 1 to 10 million transactions a month, a risk team of 1 to 5 people, rules living in code or spreadsheets. Too small for enterprise suites to bother with, big enough to feel real losses.

### 2.3 Competitive landscape

Verify current ownership and offerings before putting any of this in a pitch; this space consolidates fast.

| Category | Examples | How they work | Gap Aegis targets |
| --- | --- | --- | --- |
| Enterprise suites | FICO Falcon, Feedzai, Featurespace, NICE Actimize, SAS | Heavy platforms, long implementations | Cost, time to value |
| Processor-native | Stripe Radar, Adyen risk tools | Tied to one payment rail | Useless if you use several rails |
| Modern risk-ops | Sardine, Unit21, Sift, SEON, Oscilar | Consortium data, ML, case tools | Opaque pricing, rule testing depth varies |
| Merchant-side e-commerce | Riskified, Forter, Signifyd | Chargeback guarantee model | E-commerce only |
| India specialists | Clari5, Bureau, Tookitaki and others | Compliance and AML focus | Developer experience, rule agility |
| DIY and open source | Drools, Easy Rules, Flink plus custom code | Build everything yourself | No UI, no audit, no replay |

### 2.4 India-specific context (have counsel verify)

UPI scale makes real-time decisioning a daily need. Things to confirm with a lawyer and a compliance consultant: RBI expectations on fraud risk management and payment-data localisation, the DPDP Act 2023 for personal data, and PCI DSS scope. Design choice that follows regardless: **never store raw card numbers**. Accept tokens or tenant-keyed hashes, which keeps PCI scope small.

## 3. Alternatives, shared patterns, and how to stand apart

Almost every competitor follows one pattern: ingest events, enrich with features, apply rules plus an ML score, produce a decision, push alerts to a queue, give analysts a UI, produce reports. They converge because regulators and ops teams expect exactly this.

**Strategy: be conventional where buyers expect convention, different on exactly one axis.** Buyers need a case queue, dashboards and audit exports to look familiar. They do not get a familiar answer to 'how do I know this rule change is safe?'

| Dimension | Typical pattern | Aegis pattern |
| --- | --- | --- |
| Changing a rule | Edit in UI, publish, hope | Author, simulate on history, diff, approve (maker-checker), shadow, canary, live |
| Explainability | Score, sometimes reason codes | Every decision stores features, rules fired, contributions, ruleset hash |
| Testing | Staging with fake data | Deterministic replay on labeled data, usable as a CI gate |
| Deployment | SaaS only | SaaS plus bring-your-own-cloud for data residency |
| Pricing | Opaque, annual contracts | Published per-decision pricing |
| Integration | Weeks | Sandbox and first decision in a day (OpenAPI, SDKs) |
| Data | Consortium lock-in | Your data stays yours; shared signals are opt-in later |

**Why this can put you at the top:** it answers the pain buyers actually voice, it is demonstrable in a 30-minute demo, and it compounds (every customer's rule history makes replay more valuable). **The honest caveat:** no architecture guarantees the top position. It is earned by 3 to 5 referenceable customers and a track record of fewer false positives.

## 4. Staged roadmap: wedge to platform

The shape matches your Garage Plus example: one painful workflow, become the system of record, then build the network on top.

| Stage | Window | What ships | Exit criterion before moving on |
| --- | --- | --- | --- |
| 0. Foundations | Months 0 to 3 | All docs, discovery interviews, synthetic data generator, walking skeleton | 10+ interviews done, 3 design partners verbally committed |
| 1. Wedge | Months 3 to 9 | Decision API, rules engine, audit trail, replay tool, console | 3 partners running in shadow mode; a real rule change validated by replay |
| 2. Operations | Months 9 to 15 | Case management, analyst workflow, label feedback loop, allow/block lists, webhooks, SSO, ML champion vs challenger | Paying customers; analysts use it daily |
| 3. Adjacent products | Months 15 to 24 | Login and account-takeover risk, onboarding and KYC orchestration, AML monitoring, chargeback and dispute management | Customers asking for these unprompted |
| 4. Platform | 24 months plus | Marketplace for rule packs, connectors and third-party signal providers with revenue share; privacy-preserving shared signals; partner API | Enough customers to make a two-sided market worth it |

Rule: **do not start a stage until the previous exit criterion holds.** The marketplace in stage 4 is the Amazon-like step; it only works after the system of record exists in stage 1 and 2.

## 5. Team: 35 people in six squads

| Squad | Roles | Heads |
| --- | --- | --- |
| Decision Core | Backend engineers (4), data/risk scientist (1) | 5 |
| Streaming and Data | Backend/data engineers (3), data/risk scientists (2) | 5 |
| Rules and Replay | Backend engineers (2), risk scientist (1), QA (1) | 4 |
| Console | Frontend engineers (4), product designers (2), QA (1) | 7 |
| Platform | SRE/DevOps (3), security engineer (1) | 4 |
| Trust and Go-to-market | Compliance (1), QA/SDET (1), tech writer/DevRel (1), solutions engineers (2), sales (2), PMs (2), plus 3 leads (product, engineering, design) | 10 |

Mapping to your reality: each squad's charter doc is the brief you give an agent session. The roles you cannot delegate to an agent are customer interviews, sales, legal review and security sign-off.

## 6. The documentation suite (docs first)

### 6.1 Documents in four waves

| # | Document | Purpose | Gate to pass |
| --- | --- | --- | --- |
| **Wave 1: Discovery (weeks 1 to 3)** |  |  |  |
| 1 | Production-Readiness Discovery | Defines what 'ready' means: SLOs, data, security, compliance, ops, people | Leads sign off |
| 2 | Customer and Market Discovery Report | Interview synthesis, ICP, pricing hypotheses | 10+ interviews |
| 3 | PRD with success metrics | Scope, personas, non-goals | Reviewed by design partners |
| 4 | Statement of Work templates | One for the internal build, one for customer pilots | Legal review |
| **Wave 2: Design (weeks 3 to 6)** |  |  |  |
| 5 | Architecture overview (C4 levels 1 to 3) plus ADR log | Decisions and their reasons | Architecture review |
| 6 | OpenAPI 3.1 spec plus event schemas | Contract for API and Kafka messages | Schema registry compatibility checks |
| 7 | Data model and retention policy | Tables, partitions, PII handling | Privacy review |
| 8 | Threat model (STRIDE) plus controls matrix | Security design | Security sign-off |
| 9 | Rule DSL and explainability spec | How rules are written, versioned, explained | Risk team review |
| 10 | Design system (DESIGN.md) plus UX flows | See section 9 | Design review |
| 11 | Test and performance strategy | Load, chaos, concurrency tests | QA lead sign-off |
| **Wave 3: Operate (weeks 6 to 9)** |  |  |  |
| 12 | SLOs, runbooks, on-call, backup and DR plan | How it stays up | Game-day drill passed |
| 13 | Compliance mapping | PCI scope, DPDP, SOC 2 or ISO 27001 controls | Consultant review |
| 14 | Deployment and IaC guide | Repeatable environments | Clean-room deploy works |
| **Wave 4: Market (weeks 8 to 12)** |  |  |  |
| 15 | GTM plan, pricing, pilot agreement, DPA, security whitepaper | Sales kit | Founder review |
| 16 | Case study templates | See section 14 | First pilot data |

### 6.2 SOW skeleton

1. Objectives and success metrics
2. Scope in and scope out (explicit non-goals)
3. Deliverables, each with acceptance criteria
4. Milestones and timeline
5. Environments and data (what the customer provides, in what format)
6. Assumptions and dependencies
7. Security, privacy and data-handling terms
8. Roles and responsibilities (both sides)
9. Change control process
10. Fees, payment terms, exit and data-return terms

For pilots, add: shadow-mode only, no live decisions, success is defined as a measured comparison (for example, flagged fraud the customer's current system missed, and false positives avoided).

### 6.3 Production-readiness discovery questions

- **Business:** What does one wrongly declined payment cost? One missed fraud? Who owns that trade-off?
- **Data:** What fields exist per transaction? How are fraud labels produced and how late do they arrive (chargebacks can take weeks)?
- **Reliability:** Target availability and p99 latency? If Aegis is down, does the customer fail open or closed?
- **Security:** Where is data hosted? Who can see PII? How are keys managed?
- **Compliance:** Which regulators apply? What audit evidence will be requested and how often?
- **Operations:** Who is on call? What is the incident process? RPO and RTO?
- **People:** Which roles will use the console, and what can each one do?

## 7. Architecture

### 7.1 Principles

Start as a **modular monolith with separate deployables**, not 30 microservices. Six deployables is enough: decision-api, stream-processor, rules-service, replay-engine (batch worker), console-api, identity-service. Split further only when team size or scale forces it. Multi-tenancy is in every table, key and topic from day one.

### 7.2 Flow

```
Client -> Decision API (sync) ----\
                                    >-> Evaluation core -> Decision + reasons
Kafka ingest topic (async) -------/        |      |
                                           |      +-> Redis (velocity, features)
                                           +-> Postgres (rules, history)
Decision -> outbox -> Kafka decisions topic -> webhooks, case queue, analytics
Replay engine reads historical events -> same Evaluation core -> isolated results
```

The key design rule: **the sync API, the async stream and the replay tool all call the same evaluation core.** That is what makes replay trustworthy: it runs the real code.

### 7.3 Velocity limits under concurrency

The problem: two transactions for one card arrive together, both read count 4 against a limit of 5, both pass.

The fix is layered:

1. **Ordering by entity.** The Kafka key is `tenant_id:account_id`, so the async path evaluates one entity's events one at a time.
2. **Atomic check-and-increment.** On the sync path, a Redis Lua script does trim window, count, compare and add in one step. Redis runs scripts atomically, so two callers cannot both see 4. Use hash tags so one entity's keys live in one cluster slot.
3. **Postgres is the source of truth; Redis is derived.** A reconciliation job rebuilds counters from history and alerts on drift.
4. **State the failure bound honestly.** Redis replication is asynchronous, so a failover can lose the last few writes. Document the bounded error, or accept the latency cost of waiting for replica acknowledgment on high-stakes limits.
5. **Prove it.** A concurrency test fires N parallel requests at a limit of L and asserts exactly L pass. It runs in CI.

### 7.4 Duplicate messages and replay safety

| Layer | Mechanism |
| --- | --- |
| API | Client sends a transaction ID or idempotency key. Same key and same payload hash returns the stored decision. Same key, different payload returns an error. |
| Consumer | A unique constraint on `(tenant_id, event_id)` in a processed-events table, inserted in the **same database transaction** as the decision. Delivery is at-least-once; the effect is exactly-once. |
| Publishing | Transactional outbox, so a decision and its outgoing event cannot diverge. Kafka producers run with idempotence on. |
| Replay | Every replay has a `run_id` and writes only to replay tables. Replays can never touch live decisions. Re-running the same run is itself idempotent on `(run_id, transaction_id)`. |

### 7.5 Rules and scoring

- Rules are data, written in a restricted expression language (CEL is a good candidate because it is non-Turing-complete and safe to sandbox), stored as **immutable, hashed versions**.
- Each rule carries a weight and a reason code. Score is the clamped sum of fired rule weights; thresholds map score to approve, review or decline. Hard rules (block lists) override the score.
- Publishing needs **maker-checker**: the author cannot approve their own change. Rollout path: draft, simulate, approve, shadow, canary percentage, live.

### 7.6 Replay engine (the differentiator)

Input: a dataset plus ruleset A and ruleset B. Output: a comparison report.

- **Deterministic by construction.** Velocity features use **event time**, not wall-clock time, and events are ordered by `(event_time, sequence)`. Feature state is rebuilt per run in an isolated namespace.
- **Report contents:** transactions whose decision changed (approve to review and so on), newly flagged and newly released, precision, recall and false-positive rate on labeled data, and p50, p95, p99 evaluation latency per version.
- **Synthetic data generator** with labeled patterns: card testing, velocity bursts, impossible travel, mule rings, merchant-category anomalies. Publish the generator and methodology so results can be reproduced.
- **Limit to state in the docs:** synthetic labels measure the engine, not real-world fraud. Real validation uses partner data in shadow mode.

### 7.7 Audit trail

Every decision stores: input snapshot, features computed, rules fired with contributions, ruleset version hash, final score, outcome, latency, and whether the decision was degraded. The audit log for rule changes, logins, permission changes and data access is append-only and **hash-chained** per tenant, exported daily to object storage with object lock.

### 7.8 Data stores

| Store | Role |
| --- | --- |
| PostgreSQL | System of record. Monthly partitions for transactions and decisions. Row-level security by tenant. Read replicas for the console. |
| Redis | Velocity counters, features, idempotency cache, rate limits. Rebuildable. |
| Kafka | Ingest, decisions, dead-letter and replay topics, with a schema registry. |
| Object storage | Replay datasets, reports, audit exports, archives. |
| Analytics store | Postgres materialized views in stage 1; ClickHouse or similar in stage 2 if needed. |

### 7.9 Failure policy

The evaluation has a time budget (hypothesis: 80 ms). If an enrichment is slow, a circuit breaker skips it. If the whole budget is blown, the tenant's configured policy applies: **fail open** (approve and flag) or **fail closed** (decline). The decision is marked degraded and audited. Poison messages go to a dead-letter topic. Consumers apply backpressure rather than dropping events.

## 8. Authentication and authorization

### 8.1 Human flows, adapted from your onboarding diagram

Your diagram has a sound skeleton: owner-only signup, invites for everyone else, login by email or mobile, OTP-based password reset, role-specific dashboards. Here is the same flow mapped to Aegis, with the hardening I would add.

| Step | Your flow | Aegis version | Hardening |
| --- | --- | --- | --- |
| Signup | Owner only: name, email, mobile verified by OTP, password, confirm, eye icon | Organization Owner signs up and creates the tenant | Verify email as well as mobile. Hash passwords with argon2id. Check against breached-password lists. Show/hide toggle is an SVG icon with an accessible label. |
| After signup | Redirect to login | Same; first login lands on an onboarding checklist | Mandatory TOTP setup for Owner and Admin |
| Invite | Owner invites others; email with button and link (same action); unique hashed token; mobile shown read-only; set and confirm password | Owner or Admin invites Risk Manager, Rule Author, Analyst, Auditor, Developer | 256-bit random token, **store only its hash**, single use, expires in 72 hours, bound to invited email and role, revocable, resend invalidates the old link. In B2B show email read-only; make mobile optional. |
| Login | Email or mobile plus password | Same | Generic error messages. Exponential backoff and lockout. Session list and new-device email alert. |
| Forgot password | Mobile, OTP server, verify OTP, new password, confirm, back to login | Same, with email link as an alternative | 6-digit OTP stored hashed, 5-minute expiry, max 5 attempts, resend cooldown, rate limits per phone and per IP. Always answer 'if the account exists, a code was sent'. A reset revokes all sessions. SMS OTP is weak against SIM-swap, so privileged roles reset via email link plus TOTP. |
| After login | Redirect to role-specific dashboard | Same | Role comes from server-side claims, never the client. Deep links re-check permission. |

### 8.2 Roles (RBAC with maker-checker)

| Role | Can | Cannot |
| --- | --- | --- |
| Owner | Everything, including billing, admins, deleting the tenant | n/a |
| Admin | Users, API keys, settings | Approve rules |
| Risk Manager | Approve and publish rules, set thresholds and fail policy, run replays | Approve a change they authored |
| Rule Author | Draft rules, run simulations and replays | Publish |
| Analyst | Review cases, label transactions, view decisions | Change rules |
| Auditor | Read-only, including audit log and exports | Change anything |
| Developer | API keys, webhooks, sandbox | See PII views by default |

Permissions are enforced server-side and scoped by environment (sandbox or production) and business unit.

### 8.3 Machine authentication

Decision API: OAuth2 client credentials, or API keys with HMAC request signing and a timestamp window to stop replayed requests. Separate keys per environment, scopes, rotation with an overlap period, optional IP allowlists, mTLS for enterprise. Webhooks are signed. Secrets are shown once and stored hashed.

### 8.4 Sessions and identity

Short-lived access token (10 to 15 minutes) plus a rotating refresh token in an httpOnly, Secure, SameSite cookie, with CSRF protection and idle timeout. **Step-up authentication** (re-enter TOTP) before publishing rules or creating API keys. Enterprise SSO (OIDC, SAML) and SCIM provisioning arrive in stage 2.

**Build or buy:** I would not hand-roll identity. Hand-written auth is where production-grade projects get breached. In a Java stack, Keycloak or Spring Authorization Server, or a managed provider, handles tokens, MFA and SSO; you add your custom flows (mobile OTP, invite links) on top.

## 9. Frontend (React) and design system

### 9.1 Stack

- React with TypeScript in strict mode, Vite, React Router
- TanStack Query for server state, TanStack Table with row virtualization
- React Hook Form with Zod for forms and validation
- Tailwind CSS plus Radix primitives (the shadcn/ui pattern), driven by design tokens
- ECharts or visx for charts (they handle large datasets)
- Typed client generated from the OpenAPI spec
- SSE or WebSocket for the live decision stream, batched every 250 ms
- INR and en-IN number formatting from day one
- Tests: Vitest, Testing Library, Playwright, axe for accessibility, Storybook with visual regression
- Monorepo with pnpm and Turborepo: `apps/console`, `apps/marketing`, `packages/ui`, `packages/tokens`, `packages/api-client`. The marketing and docs site is separate (Astro or Next.js) for SEO; the console is a SPA behind auth.

### 9.2 Screens

| Screen | Job it does |
| --- | --- |
| Live decisions | Real-time stream, filters, outcome chips |
| Transaction detail | Why this decision: rules fired, contributions, features, ruleset version, timeline |
| Case queue | Assign, SLA timers, bulk actions, label outcome |
| Rule editor | Validated editor, diff against live, simulate on sample data |
| Replay compare | Ruleset A vs B: changed decisions, precision and recall, latency charts |
| Audit explorer | Search and export |
| Team, API keys, webhooks, sandbox | Access and integration |
| Role dashboards | Analyst: queue. Risk Manager: metrics and pending approvals. Auditor: audit. Developer: API health. |

### 9.3 Design system with getdesign.md

getdesign.md is a catalog of DESIGN.md files: write-ups of 550+ well-known sites' design systems (colors, type, spacing, components and the reasoning), meant to be handed to a coding agent so every page keeps one visual language. I read the site's landing page, not the individual files, so open your chosen one and review it before giving it to the agent.

**Recommendation:** choose one DESIGN.md as the console base and one for marketing, not five blended. From its catalog, the ones that fit this product: **Linear** (precise, dense, minimal) for the console; **Sentry** (dark, data-dense dashboards) as a reference for monitoring screens; **Stripe** (trust, documentation clarity) for marketing and docs; **IBM Carbon** if you want a rigorous enterprise-table system. My pick: Linear-style console plus Stripe-style marketing. Then write **your own DESIGN.md** recording the tokens you adapted, so the agent follows yours. Use these as structural references only; do not copy another company's logo, brand marks or exact palette.

**Color theme (dark default, light companion).** I have not measured contrast; run these through a checker.

| Token | Dark | Light | Use |
| --- | --- | --- | --- |
| canvas | #0B0E14 | #F7F8FA | Page background |
| surface | #121722 | #FFFFFF | Cards, panels |
| surface-raised | #1A2130 | #F0F2F6 | Popovers, hover |
| border | #263044 | #E2E6EE | Dividers |
| text | #E6EAF2 | #0F1626 | Body |
| text-muted | #93A0B8 | #5B677F | Secondary text |
| accent | #6C7CFF | #4B5BE0 | Primary actions, focus ring |
| approve | #2FBF71 | #178A4C | Approved |
| review | #F5A524 | #B7740A | Flagged for review |
| decline | #F0556A | #CC2F46 | Declined |
| info | #4DA3FF | #1F6FD1 | Neutral info |

**Rules:** outcomes never rely on color alone; each has an icon and a label (check, flag, cross). Inter for UI, JetBrains Mono for IDs, amounts and rule code, tabular numerals for money. 4px grid, radius 6 on controls and 10 on cards. Two density modes (comfortable, compact) because analysts live in tables. Keyboard-first: command palette, j/k to move through the queue, shortcuts for case actions. Motion is minimal and respects reduced-motion settings.

## 10. Performance

### 10.1 Targets (hypotheses to verify)

| Area | Target | Verified by |
| --- | --- | --- |
| Decision API latency | p50 25 ms, p99 100 ms server-side | k6 or Gatling load tests with realistic traffic mix |
| Availability | 99.95% (about 22 minutes of downtime a month) | SLO dashboards, game days |
| Throughput | 2,000 decisions/s per region at launch, design path to 10,000+ | Load and horizontal-scaling tests |
| Kafka consumer lag | Under 5 s at p99, steady state | Alerting |
| Replay speed | 20,000+ events/s per worker, scaling with workers | Benchmarks |
| Console | LCP under 2.5 s, INP under 200 ms, initial JS under 200 KB gzipped | Lighthouse CI, bundle budget in CI |
| Big tables | 100k rows scroll smoothly through virtualization | Playwright performance test |

### 10.2 Techniques

- **Backend:** compile rules once per version and cache; pipeline Redis calls; per-step latency budgets (for example auth 5 ms, features 20 ms, rules 10 ms); connection pools; warm-up before taking traffic; virtual threads on Java 21 for I/O-bound work.
- **Durability vs speed (write an ADR):** I recommend a durable write of the decision before responding, so the audit trail is never incomplete, and partitioning plus batching to keep it fast. The alternative, acknowledged Kafka write then async persist, is faster but widens the failure window.
- **Frontend:** route-level code splitting, virtualization, batched live updates, skeleton screens.
- **Testing:** smoke, load, stress, 24-hour soak, spike. Use open-model load generators so slow responses do not hide themselves. Chaos drills: kill Redis, a Kafka broker, fail over the database, and confirm the failure policy behaves as documented.

## 11. Production-readiness gate (all must be true before real traffic)

- **Reliability:** SLOs defined and measured; load, soak and chaos tests passed; backups restored successfully in a drill; RPO and RTO stated and met; fail-open or fail-closed policy tested.
- **Correctness:** concurrency test proves exactly-L under a limit of L; duplicate-delivery and replay tests pass; replay is bit-for-bit repeatable on the same dataset.
- **Security:** threat model reviewed; external penetration test with findings closed; secrets in a vault; dependency, container and secret scans in CI; least-privilege access; PII encrypted at rest and in transit.
- **Data and compliance:** retention and deletion policy implemented; no raw card numbers stored; audit export verified; DPA and privacy notice in place; counsel has reviewed regulatory position.
- **Observability:** metrics, logs, traces linked by decision ID; alerts tied to runbooks; status page.
- **Operations:** on-call rota, incident process, runbooks exercised in a game day; infrastructure reproducible from code.
- **Product:** onboarding works end to end for each role; docs and sandbox usable by an outside engineer without help; pricing and SOW templates approved.

## 12. Sales and go-to-market without ads

### 12.1 Principle

This is trust-based B2B with a small, findable buyer set (a few hundred companies in your starting segment, not millions). Ads are the wrong tool. Direct and earned channels beat them.

### 12.2 The motion

1. **Target list:** 200 companies, one named buyer each (Head of Risk, Fraud, or Payments).
2. **The offer: a free shadow-mode backtest.** 'Give us 90 days of anonymized transactions and labels. In a week we show what Aegis would have flagged compared with your current system.' No risk to them, and the demo uses their own data.
3. **Design partners:** 3 to 5, discounted or free in exchange for feedback and case-study rights.
4. **Pilot to paid** under the SOW from section 6.

### 12.3 Channels

| Channel | How | Expectation |
| --- | --- | --- |
| Founder-led outreach | Researched, personal messages to risk leaders, 10 to 20 a week | Low volume, high quality; the main early source |
| Warm introductions | Advisors, investors, former colleagues, your own network | Highest conversion |
| Open source and technical content | Open-source the synthetic data generator and a rule-backtesting library; write about concurrency, idempotency and replay; give talks | Attracts the engineers who influence the buying decision |
| Communities and events | Fintech meetups in Pune, Mumbai, Bengaluru; Global Fintech Fest; risk and fraud forums. Speak rather than sponsor | Slow, compounding |
| Partnerships | Payment gateways, bank fintech programs, system integrators, compliance and audit consultants who recommend tools | Referral fee model |
| Product-led | Free sandbox, API key in five minutes, excellent docs, public pricing calculator | Engineers try it before sales is involved |
| Cloud marketplaces | AWS Marketplace and similar, later stage | Eases procurement |

Have counsel confirm outreach rules (commercial-message and consent rules, DPDP) before scaling any channel.

### 12.4 Pricing hypotheses (test in interviews)

Platform fee plus per-decision usage, a free sandbox tier, a fixed-fee pilot, and a premium for bring-your-own-cloud. Publish the rates.

### 12.5 Process and metrics

Lead, discovery call, backtest, technical validation, security review, SOW, pilot, paid. Track backtest-to-pilot conversion, time to first decision, pilot-to-paid conversion, false-positive reduction, and net revenue retention. Early on this is you; at scale it is 2 sales plus 2 solutions engineers.

### 12.6 Objections you will hear

- *'We can build this.'* Maintaining audit, replay, maker-checker and reliability costs more than it looks.
- *'We can't share data.'* Shadow mode, anonymization, bring-your-own-cloud.
- *'Security?'* Published controls, SOC 2 roadmap, penetration test summary.
- *'No track record.'* Design partners, open methodology, reproducible benchmark.

## 13. Building it with Antigravity IDE

### 13.1 How to run a 35-person plan with agents

- **Repo layout:** `/docs` (every document above as versioned markdown), `/docs/adr`, `/apps`, `/services`, `/packages`, `/infra`, plus the agent rules file your IDE uses (check Antigravity's docs for its exact convention). Put DESIGN.md at the frontend root.
- **Workflow:** spec, then an agent task that cites the exact doc section, then tests first, then a pull request, then human review. Every acceptance criterion in a spec becomes a test.
- **Human-only zones:** authentication, cryptography, the Redis velocity script, and anything touching migrations get human review every time. Use property-based tests for velocity and idempotency.
- **CI gates:** unit tests, integration tests with Testcontainers (Postgres, Redis, Kafka), API and schema contract tests, a load smoke test, static analysis, dependency and secret scanning.
- **Build order:** one **vertical slice** first: a transaction goes in, a decision comes out, an audit row is written, and it shows up in the console. Widen from there.

### 13.2 Phases for stage 0 and 1

| Weeks | Focus |
| --- | --- |
| 1 to 3 | Wave 1 documents, discovery interviews |
| 3 to 6 | Monorepo, CI, tenant model, identity, Decision API contract and stub |
| 6 to 10 | Evaluation core, Redis velocity script, idempotency, audit trail |
| 10 to 14 | Rule DSL, versioning, maker-checker, replay engine |
| 14 to 18 | Console screens |
| 18 to 22 | Load, chaos, security testing, hardening |
| 22 to 26 | Shadow pilots with design partners |

That is the schedule for a funded team. **Solo with agents, plan for 9 to 12 months** and use the stage exit criteria, not the calendar, as your guide.

## 14. Case studies

You have no customers yet, so produce three honest kinds.

1. **Engineering case studies (publishable now):** 'Proving exactly-N under a velocity limit with concurrent requests', 'Surviving duplicate Kafka deliveries with idempotent decisions', 'Deterministic replay with event-time features'.
2. **Benchmark case study:** the synthetic dataset, published methodology, reproducible scripts, clearly labeled as synthetic.
3. **Pilot case studies (after design partners):** Context, Problem, Approach (shadow mode), Results, Quote, Caveats. Results to measure: decisions changed, false positives avoided, fraud caught that the old system missed, rule-change lead time before and after, analyst review time, p99 latency. Get written approval, anonymize when asked, define every metric, never overclaim.

## 15. Risks and Decision Register References (formerly Open Questions)

| Risk | Why it matters | Mitigation |
| --- | --- | --- |
| No real fraud data or labels | Cannot prove detection quality | Design partners, shadow pilots, public datasets for sanity checks |
| Liability for wrongful declines | Revenue and trust | Customer owns thresholds, fail-open option, contract limits, insurance |
| Regulation and data localisation | Can block deals | India-hosted regions, bring-your-own-cloud, counsel early |
| Security breach | Company-ending | Minimize PII, tokens and hashes, managed identity, penetration tests |
| Long enterprise sales cycles | Cash runway | Target mid-size, free backtest, usage pricing |
| Incumbents copy replay | Erodes the wedge | Go deepest on the rule lifecycle; open methodology builds trust |
| Scope creep toward 'platform' | Never ships stage 1 | Stage gates in section 4 |
| Concurrency bugs during failover | Silent limit bypass | Documented error bound, tests, reconciliation |

> **Note (Decision D3):** All questions below have been formally resolved or classified in [docs/00-decisions/decision-register.md](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) and [docs/00-decisions/assumptions-register.md](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md).

**Questions answered in Decision Register:**

1. Which segment and geography first: payment companies, lenders, or marketplaces?
2. Card, UPI, or both in the stage-1 event schema?
3. Self-funded or raising money? It changes how much of this you can do.
4. Build or buy identity?
5. Which three companies could you ask for a discovery interview this month?

**Suggested next document:** the Production-Readiness Discovery (document 1), since every other document depends on its answers.
