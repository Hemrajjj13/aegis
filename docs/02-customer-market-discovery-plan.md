# Customer and Market Discovery Plan

**Document:** 02  
**Status:** Outline — to be completed after interviews  
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026

---

> **Note:** The complete, authoritative operational discovery plan, 200-company target list methodology, outreach templates, free-backtest one-pager, and interview logging templates are located in [docs/02-discovery-plan.md](file:///c:/dev/aegis/docs/02-discovery-plan.md).
> This document remains as a companion reference. Do not draw validation conclusions until interviews are logged.

---

## 1. Objectives

1. Confirm or invalidate the ICP hypothesis (mid-size Indian fintechs
   and payment companies, 1–10 M transactions/month, risk team 1–5 people).
2. Map the specific pains that Aegis's replay and rule-lifecycle features
   address, in the buyer's own language.
3. Test pricing hypotheses.
4. Identify 3–5 companies willing to be design partners.
5. Build a target list of 200 named contacts.

---

## 2. Interview Script (20–30 Questions)

Protocol: Interview script structured below is executed by the Founder; findings are recorded in the [Assumptions Register](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) to satisfy **Gate G1** (10+ interviews required prior to customer data entry).

### Section A — Role and context (5 min)
- What is your title and what do you own day-to-day in the risk/fraud function?
- How large is your team? How many transactions do you process per month?
- What payment rails do you operate on (UPI, card, wallet, BNPL)?

### Section B — Current state (10 min)
- Walk me through what happens when a suspicious transaction hits your system today.
- Where do your rules live right now — code, a spreadsheet, a vendor platform?
- How long does it take to test and deploy a rule change? What happens if the rule is wrong?
- How do you know a rule is working? What does a false positive cost you?
- Who owns the fraud threshold — Risk, Product, Engineering, or someone else?

### Section C — Pain depth (10 min)
- Tell me about the last time you wanted to change a rule and didn't, or waited longer than you should have.
- Have you ever had a rule change cause an unexpected spike in declines or fraud? What happened?
- How do you currently validate that a rule change is safe before it goes live?
- If you could have one thing you cannot have today in your fraud tooling, what would it be?
- What does one wrongly declined transaction cost you — in INR, in customer experience, in ops time?

### Section D — Current tools and alternatives (5 min)
- What tools do you use today? What do you like and dislike about each?
- Have you evaluated any new fraud or risk platforms in the last 12 months? What stopped you from buying?
- What does your current vendor lock you in on?

### Section E — Backtest offer (5 min)
- If we could run 90 days of your anonymized historical transactions through our engine and show you what Aegis would have flagged vs your current system — in a week, with no integration required — would that be useful?
- What format is your transaction data in? Could you export it?
- Who else would need to be in the room to evaluate results?

### Section F — Pricing (5 min)
- What are you paying today for fraud tooling (per-decision, seat, or platform fee)?
- If a per-decision price were published publicly, would that help or hurt the buying process?
- What does "good ROI" look like for fraud tooling in your org?

### Section G — Fit check
- Is there any regulatory or security reason you could not share anonymized transaction data with a vendor?
- Who in your org would make the final decision on a new fraud platform?
- Protocol: Incorporate questions on DPDP consent workflows and RBI tokenisation migration challenges during interviews (Gate G1).

---

## 3. Target List — 200 Companies

Sourcing Strategy: Build 200-company target list from RBI-registered payment aggregators, NBFC lenders active in BNPL/personal finance, RBI-licensed prepaid wallet operators, fintech lenders, and neobanks (tracked under Gate G1).

Sources: RBI NBFC list, PA/PG licence holders list on RBI website, Entrackr
and Inc42 fintech databases, LinkedIn Sales Navigator, NASSCOM DSCI member list.

### Segment breakdown (hypothesis)

| Segment | Target count | First contact role |
|---------|-------------|-------------------|
| Payment aggregators and gateways | 30 | Head of Risk or VP Engineering |
| Fintech lenders and BNPL | 60 | Head of Fraud or Chief Risk Officer |
| Neobanks and wallets | 30 | VP Product or Head of Payments |
| E-commerce and marketplaces | 40 | Head of Fraud or VP Operations |
| Gig, gaming, crypto platforms | 20 | CTO or Head of Product |
| System integrators and consultants | 20 | Partner or Practice Head |
| Total | 200 | |

---

## 4. The Backtest Offer

**Offer:** "Give us 90 days of anonymized transaction data with labels
(fraud = 1, legitimate = 0 where available). In one week we will show you
what Aegis's rule engine would have flagged compared with your current system:
false positives avoided, fraud caught that your current system missed, and
the p99 latency of every decision."

**Conditions:**
- Data is anonymized before leaving the customer's systems (no name, email, phone, or PAN).
- Data is used only for this engagement and deleted after the report is delivered.
- Report is delivered under NDA.
- No payment required for the backtest.
- If the customer wants to proceed to a pilot, an SOW is signed.

Specification (Decision D8): Data transfer occurs via the Aegis Dataset Import Tool ([doc 06 §8](file:///c:/dev/aegis/docs/06-api-and-event-schemas.md#8-dataset-import-api-and-schema-mapping-specification-decision-d8)) using pre-signed S3 upload URLs with client-side salting (Assumption [ASM-GTM-01](file:///c:/dev/aegis/docs/00-decisions/assumptions-register.md) / Gate G1).

---

## 5. Interview Logistics

- Target: 20–30 interviews in weeks 1–3.
- Duration: 30 minutes per interview.
- Format: video call; record with consent.
- Synthesise notes within 24 hours of each interview.
- After 10 interviews: review themes, adjust script, and update the ICP hypothesis.
- After 20+ interviews: write the full discovery report and update docs/03-prd.md.

Target: Founder initiates outreach to 3 mid-size payment aggregator contacts from personal network in month 1 (Gate G1).
