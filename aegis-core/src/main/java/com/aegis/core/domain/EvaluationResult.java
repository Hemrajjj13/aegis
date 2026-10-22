package com.aegis.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable complete evaluation result produced by EvaluationCore.
 */
public record EvaluationResult(
    @JsonProperty("decision_id") UUID decisionId,
    @JsonProperty("event_id") String eventId,
    @JsonProperty("outcome") DecisionOutcome outcome,
    @JsonProperty("score") double score,
    @JsonProperty("hard_rule_fired") boolean hardRuleFired,
    @JsonProperty("ruleset_id") String rulesetId,
    @JsonProperty("ruleset_version_hash") String rulesetVersionHash,
    @JsonProperty("rules_fired") List<RuleOutcome> rulesFired,
    @JsonProperty("features_snapshot") Map<String, Object> featuresSnapshot,
    @JsonProperty("degraded") boolean degraded,
    @JsonProperty("degraded_reason") String degradedReason,
    @JsonProperty("latency_ms") double latencyMs,
    @JsonProperty("timestamp") Instant timestamp
) {
    public EvaluationResult {
        if (decisionId == null) {
            decisionId = UUID.randomUUID();
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("event_id must not be blank");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (rulesetId == null || rulesetId.isBlank()) {
            rulesetId = "default-ruleset";
        }
        if (rulesetVersionHash == null || rulesetVersionHash.isBlank()) {
            rulesetVersionHash = "0000000000000000000000000000000000000000000000000000000000000000";
        }
        if (rulesFired == null) {
            rulesFired = List.of();
        } else {
            rulesFired = List.copyOf(rulesFired);
        }
        if (featuresSnapshot == null) {
            featuresSnapshot = Map.of();
        } else {
            featuresSnapshot = Map.copyOf(featuresSnapshot);
        }
        // Score clamping invariant: 0.00 to 100.00
        score = Math.max(0.0, Math.min(100.0, score));
    }
}
