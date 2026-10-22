# Rule DSL and Explainability Specification

**Document:** 09  
**Status:** Draft  
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

> **Assumption note:** The Common Expression Language (CEL) syntax, scoring mechanics, threshold defaults, and reason codes defined herein represent the foundational baseline for Project Aegis. Any item marked `[interview-driven]` is subject to change based on how partner risk teams currently structure and author rules.

---

## 1. Purpose and Architecture Principles

This document specifies the domain-specific language (DSL), execution semantics, explainability model, and versioning standards for fraud detection rules in Project Aegis.

The rule engine satisfies six non-negotiable architectural requirements:
1. **Deterministic and Side-Effect Free:** A rule is a pure function: `f(event, features, clock) -> RuleOutcome`. Given identical inputs, it produces the identical verdict on any machine at any time (ADR-0003).
2. **Sub-Millisecond Evaluation:** A complete ruleset containing 50–200 rules must evaluate in < 10 ms (budgeted 10 ms p99 in doc 01 §3.1).
3. **Common Expression Language (CEL):** Instead of building a custom parser or embedding an unconstrained language (like JavaScript or Groovy), Aegis uses CEL (Google Common Expression Language). CEL is non-Turing complete, type-safe, side-effect free, memory-bounded, and evaluates in microseconds.
4. **Full Explainability:** Every decision must detail exactly which rules fired, their numerical score contribution, and standardized machine-readable reason codes (doc 01 §9.1).
5. **Hard Rule Precedence:** Critical blocks (e.g., sanctioned accounts, stolen cards) bypass soft scoring and force an immediate verdict.
6. **Immutable Cryptographic Versioning:** Rulesets are hashed canonically (SHA-256). Decisions link to this immutable version hash for life (ADR-0003).

---

## 2. Evaluation Engine Architecture

The rule evaluation engine is implemented as a self-contained Java 21 library: `evaluation-core`.

```
                    +-----------------------------+
                    |       Transaction Event     |
                    +--------------+--------------+
                                   |
                                   v
+------------------+      +------------------+      +-------------------+
| Velocity Features|<---->| Evaluation Core  |<---->| Injected Clock    |
| (Redis / In-Mem) |      | (CEL Evaluator)  |      | (event_time)      |
+------------------+      +--------+---------+      +-------------------+
                                   |
                                   v
                    +-----------------------------+
                    |        Decision Result      |
                    | (Outcome, Score, Fired List)|
                    +-----------------------------+
```

### 2.1 The Execution Context

When evaluating a transaction, `evaluation-core` binds a typed context containing three namespaces:
- `event`: Raw, schema-validated transaction attributes.
- `features`: Derived temporal, velocity, and statistical profile metrics.
- `lists`: Deterministic set-membership lookups (blocklists, allowlists, VIP tiers).

No external network calls, database queries, or wall-clock lookups are permitted during evaluation. All required features are pre-fetched or extracted before invoking the CEL interpreter.

### 2.2 Injected Clock Invariant

Per ADR-0003, no rule or CEL extension function may call `System.currentTimeMillis()`, `Instant.now()`, or `Clock.systemUTC()`. The temporal reference is strictly `event.event_time`.

---

## 3. Rule Anatomy and Schema

A ruleset is a JSON/YAML document containing metadata, threshold configurations, and an ordered collection of rules.

### 3.1 Field Specification

```yaml
ruleset_id: "rs_card_ecommerce_v1"
name: "E-Commerce Card Payment Ruleset"
description: "Production fraud ruleset for Indian e-commerce checkout"
version: 1
thresholds:
  review_threshold: 40.0   # [interview-driven] Score >= 40 triggers manual REVIEW
  decline_threshold: 75.0  # [interview-driven] Score >= 75 triggers immediate DECLINE
rules:
  - rule_id: "RUL-VEL-001"
    name: "Card 10-Minute Velocity Exceeded"
    description: "Detects rapid testing or automated card bursting"
    rule_type: "SOFT"       # SOFT (scoring) or HARD (override)
    enabled: true
    condition: "features.velocity.card_token_10m_count >= 5"
    weight: 35.0            # Score impact if condition evaluates to true
    reason_code: "VELOCITY_CARD_10M_EXCEEDED"
    reason_description: "More than 5 transactions attempted on this card within 10 minutes"
```

### 3.2 Rule Types: Soft vs. Hard

| Dimension | Soft Rules (`rule_type: "SOFT"`) | Hard Rules (`rule_type: "HARD"`) |
|---|---|---|
| **Purpose** | Probabilistic risk scoring | Deterministic, non-negotiable policy enforcement |
| **Execution** | All soft rules are evaluated to calculate total score | Evaluated in sequence; halts score pipeline on match |
| **Score Impact** | Adds or subtracts `weight` (-100.0 to +100.0) | Does not alter score; flags `hard_rule_fired = true` |
| **Outcome Effect** | Combined score compared against thresholds | Overrides outcome directly to `DECLINE` (or `REVIEW`) |
| **Examples** | Velocity bursts, new device, high amount, odd hours | Sanctioned entity, confirmed stolen card, blocked MCC |

---

## 4. Scoring Logic and Threshold Clamping

### 4.1 Scoring Pipeline

1. **Initialize Score:** Start with `score = 0.0`.
2. **Evaluate Hard Rules:**
   - Iterate through all active `HARD` rules.
   - If any `HARD` rule evaluates to `true`, record the rule in `rules_fired`, set `hard_rule_fired = true`, set `outcome = rule.action` (default `DECLINE`), and bypass soft scoring.
3. **Evaluate Soft Rules:**
   - If no hard rule fired, iterate through all active `SOFT` rules.
   - For every rule where `condition == true`:
     - Append to `rules_fired` with its configured `weight` and `reason_code`.
     - `score += rule.weight`.
4. **Score Clamping:**
   - Fraud score is clamped to the closed interval `[0.0, 100.0]`:
     $$\text{final\_score} = \min(100.0, \max(0.0, \text{score}))$$
5. **Threshold Outcome Assignment:**
   - If $\text{final\_score} \ge \text{decline\_threshold} \implies \text{outcome} = \text{DECLINE}$
   - Else if $\text{final\_score} \ge \text{review\_threshold} \implies \text{outcome} = \text{REVIEW}$
   - Else $\implies \text{outcome} = \text{APPROVE}$

---

## 5. Ten Worked Rule Examples

The following 10 examples demonstrate production patterns for Indian fintechs, payment gateways, and lenders.

### Example 1: Rapid Card Bursting (Soft Rule)
- **ID:** `RUL-VEL-001`
- **Pattern:** Fraudsters testing stolen card numbers using automated bots at merchant checkout.
- **CEL Condition:**
  ```cel
  features.velocity.card_token_10m_count >= 5
  ```
- **Type:** `SOFT` | **Weight:** `+45.0`
- **Reason Code:** `CARD_RAPID_BURST_10M`

### Example 2: Sudden Transaction Amount Spike (Soft Rule)
- **ID:** `RUL-AMT-002`
- **Pattern:** High-value transaction significantly exceeding the account's historical spending profile.
- **CEL Condition:**
  ```cel
  event.amount_inr > 10000.0 && event.amount_inr > (features.profile.account_30d_avg_amount_inr * 5.0)
  ```
- **Type:** `SOFT` | **Weight:** `+30.0`
- **Reason Code:** `AMOUNT_SPIKE_5X_HISTORICAL`

### Example 3: Late-Night High-Value Transfer (Soft Rule)
- **ID:** `RUL-TIME-003`
- **Pattern:** High-value transfer executed in the early morning hours (01:00 to 05:00 IST), typical of account takeover or compromised credentials.
- **CEL Condition:**
  ```cel
  event.amount_inr >= 50000.0 && 
  (event.event_time.getHours('Asia/Kolkata') >= 1 && event.event_time.getHours('Asia/Kolkata') < 5)
  ```
- **Type:** `SOFT` | **Weight:** `+25.0`
- **Reason Code:** `LATE_NIGHT_HIGH_VALUE_TRANSFER`

### Example 4: High-Risk MCC with Foreign IP (Soft Rule)
- **ID:** `RUL-GEO-004`
- **Pattern:** Transactions at high-risk Merchant Category Codes (e.g., crypto, gaming, forex) originating from an IP address outside India.
- **CEL Condition:**
  ```cel
  event.mcc in ['7995', '6211', '6051'] && event.device.ip_country != 'IN'
  ```
- **Type:** `SOFT` | **Weight:** `+40.0`
- **Reason Code:** `HIGH_RISK_MCC_CROSS_BORDER_IP`

### Example 5: Sanctioned Account Match (Hard Rule)
- **ID:** `RUL-HARD-005`
- **Pattern:** Originating or beneficiary account is on an official RBI, FIU, or internal sanction blocklist.
- **CEL Condition:**
  ```cel
  lists.is_in_blocklist('sanctioned_accounts', event.account_id_hash)
  ```
- **Type:** `HARD` | **Action:** `DECLINE`
- **Reason Code:** `SANCTIONED_ACCOUNT_BLOCK`

### Example 6: Rapid Device Switching (Soft Rule)
- **ID:** `RUL-DEV-006`
- **Pattern:** Account accessed from multiple distinct device fingerprints within a 1-hour window, indicating session hijacking or credential stuffing.
- **CEL Condition:**
  ```cel
  features.velocity.account_1h_distinct_devices >= 3
  ```
- **Type:** `SOFT` | **Weight:** `+35.0`
- **Reason Code:** `MULTIPLE_DEVICES_1H`

### Example 7: New Account Large First Transaction (Soft Rule)
- **ID:** `RUL-ONB-007`
- **Pattern:** Account created within the last 24 hours attempting a large transaction immediately after onboarding.
- **CEL Condition:**
  ```cel
  features.profile.account_age_hours < 24 && event.amount_inr > 25000.0
  ```
- **Type:** `SOFT` | **Weight:** `+30.0`
- **Reason Code:** `NEW_ACCOUNT_LARGE_FIRST_TXN`

### Example 8: Dormant Account Reactivation Burst (Soft Rule)
- **ID:** `RUL-DORM-008`
- **Pattern:** Account inactive for >90 days suddenly experiences multiple rapid transactions.
- **CEL Condition:**
  ```cel
  features.profile.days_since_last_txn > 90 && features.velocity.account_1h_count >= 3
  ```
- **Type:** `SOFT` | **Weight:** `+40.0`
- **Reason Code:** `DORMANT_ACCOUNT_SUDDEN_BURST`

### Example 9: Confirmed Stolen Card Network Token (Hard Rule)
- **ID:** `RUL-HARD-009`
- **Pattern:** Card token previously reported fraudulent or flagged with high chargeback frequency across tenants.
- **CEL Condition:**
  ```cel
  lists.is_token_in_chargeback_blocklist(event.card_token)
  ```
- **Type:** `HARD` | **Action:** `DECLINE`
- **Reason Code:** `CONFIRMED_STOLEN_CARD_TOKEN`

### Example 10: Domestic Card Used Cross-Border (Soft Rule)
- **ID:** `RUL-CURR-010`
- **Pattern:** Transaction currency is non-INR on a domestic-only BIN or domestic card product.
- **CEL Condition:**
  ```cel
  event.currency != 'INR' && event.card.is_domestic_only == true
  ```
- **Type:** `SOFT` | **Weight:** `+50.0`
- **Reason Code:** `DOMESTIC_CARD_FOREIGN_CURRENCY`

---

## 6. Canonical Serialisation and Version Hashing

Every published ruleset is cryptographically hashed to produce an immutable `ruleset_version_hash` (stored in `decisions.ruleset_version_hash` and `ruleset_versions.version_hash`).

### 6.1 Canonical JSON Algorithm (RFC 8785 / JCS)

To ensure hash consistency across all languages and platforms:
1. **Sorted Keys:** Object dictionary keys are sorted lexicographically by UTF-16 code units.
2. **Normalized Whitespace:** Zero whitespace outside string literals (no formatting spaces, indentation, or newlines).
3. **Number Formatting:** Integers represented without decimals; floating point numbers follow IEEE 754 standard without exponential notation where avoidable.
4. **UTF-8 Encoding:** Output string encoded strictly in UTF-8.

### 6.2 Version Hash Computation

$$\text{ruleset\_version\_hash} = \text{SHA-256}(\text{CanonicalJSON}(\text{ruleset\_ast\_and\_config}))$$

```json
{"name":"E-Commerce Card Payment Ruleset","rules":[{"condition":"features.velocity.card_token_10m_count >= 5","enabled":true,"name":"Card 10-Minute Velocity Exceeded","reason_code":"VELOCITY_CARD_10M_EXCEEDED","rule_id":"RUL-VEL-001","rule_type":"SOFT","weight":35.0}],"ruleset_id":"rs_card_ecommerce_v1","thresholds":{"decline_threshold":75.0,"review_threshold":40.0},"version":1}
```

*Immutability Guarantee:* Once a version hash is committed to `ruleset_versions` with status `APPROVED` or `LIVE`, it can never be altered. Any modification by a Rule Author spawns a new draft with a different hash, subject to maker-checker review (doc 01 §11.2).

---

## 7. Simulation and Replay Interfaces

The `evaluation-core` provides standard Java interfaces for live evaluation, offline backtesting, and rule-comparison replays.

### 7.1 Core Evaluator Interface

```java
package com.projectaegis.evaluation;

import java.time.Clock;
import java.util.List;

public interface EvaluationEngine {
    /**
     * Evaluates a single transaction deterministically.
     *
     * @param event   The validated transaction event.
     * @param features Precomputed velocity and profile features.
     * @param ruleset The compiled immutable ruleset.
     * @param clock   Injected event-time clock (Clock.fixed(event.getEventTime(), ZoneOffset.UTC)).
     * @return DecisionResult containing outcome, clamped score, and fired rules.
     */
    DecisionResult evaluate(
        TransactionEvent event, 
        FeatureSnapshot features, 
        CompiledRuleset ruleset, 
        Clock clock
    );
}
```

### 7.2 Replay Engine Comparison Interface

```java
package com.projectaegis.replay;

import java.util.stream.Stream;

public interface ReplayEngine {
    /**
     * Executes backtest comparing two ruleset versions over historical data.
     * Runs in-process with InMemoryVelocityWindow (ADR-0005).
     *
     * @param events     Stream of historical events sorted by (event_time, sequence_number).
     * @param rulesetA   Baseline ruleset (e.g., current production).
     * @param rulesetB   Candidate ruleset (e.g., proposed new rules).
     * @return ReplayReport detailing decision changes, false positive shifts, and latencies.
     */
    ReplayReport executeReplay(
        Stream<HistoricalEvent> events, 
        CompiledRuleset rulesetA, 
        CompiledRuleset rulesetB
    );
}
```

### 7.3 Replay Metrics Output Schema

The replay engine generates a structured comparison report containing:
- **Decision Matrix:** Transitions between versions:
  - Approve $\rightarrow$ Decline (newly blocked transactions)
  - Decline $\rightarrow$ Approve (loosened policy)
  - Approve/Decline $\leftrightarrow$ Review
- **Confusion Matrix:** Evaluated against labeled historical datasets (ground truth fraud chargebacks):
  - True Positive Rate (Recall improvement)
  - False Positive Rate (FPR reduction)
- **Latency Distribution:** p50, p95, p99 microsecond execution times for Version A vs. Version B.
- **Live Divergence:** `replay_divergence_pct` tracking live vs. event-time concurrency ordering variance (ADR-0003).

---

## 8. Decision Register References (formerly Open Questions)

All rule DSL and scoring questions have been resolved or classified per Decision **D3** in the [Decision Register](file:///c:/dev/aegis/docs/00-decisions/decision-register.md):

| # | Decision ID | Question / Topic | Status | Decision / Assumed Value |
|---|---|---|---|---|
| 1 | [DR-DSL-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Direct CEL authoring vs UI builder:** Visual drag-and-drop vs code editor. | Decided | Direct CEL editor with syntax validation in Tier 0/1; drag-and-drop UI builder deferred to enterprise roadmap. |
| 2 | [DR-DSL-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Negative weights:** Negative rule weights and score clamping. | Decided | Negative rule weights supported (down to -50.0 for trusted signals); total score clamped strictly to `[0.0, 100.0]`. |
| 3 | [DR-DSL-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Default threshold ranges:** Standardized scoring scale. | Decided | Standardized 0.0 to 100.0 scale (default: Review $\ge 40.0$, Decline $\ge 75.0$), configurable per tenant ruleset. |
| 4 | [DR-DSL-04](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Rule evaluation timeout:** Guarding against regex backtracking. | Decided | Per-rule evaluation timeout of 50 µs with max 250 AST nodes per rule and max 250 rules per ruleset. |
| 5 | [DR-DSL-05](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) | **Multi-currency normalization:** Real-time FX vs pre-converted INR. | Decided | Callers submit `amount_inr` pre-converted; domestic rails are INR-native. Real-time FX conversion table deferred. |

