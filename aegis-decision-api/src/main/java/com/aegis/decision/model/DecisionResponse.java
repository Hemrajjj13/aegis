package com.aegis.decision.model;

import com.aegis.core.domain.DecisionOutcome;
import com.aegis.core.domain.EvaluationResult;
import com.aegis.core.domain.RuleOutcome;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * External DecisionResponse DTO conforming to OpenAPI /decisions schema.
 */
public record DecisionResponse(
    @JsonProperty("decision_id") UUID decisionId,
    @JsonProperty("event_id") String eventId,
    @JsonProperty("outcome") DecisionOutcome outcome,
    @JsonProperty("score") double score,
    @JsonProperty("hard_rule_fired") boolean hardRuleFired,
    @JsonProperty("ruleset_id") String rulesetId,
    @JsonProperty("ruleset_version_hash") String rulesetVersionHash,
    @JsonProperty("rules_fired") List<RuleOutcome> rulesFired,
    @JsonProperty("degraded") boolean degraded,
    @JsonProperty("degraded_reason") String degradedReason,
    @JsonProperty("latency_ms") double latencyMs,
    @JsonProperty("timestamp") Instant timestamp
) {
    public static DecisionResponse from(EvaluationResult result) {
        return new DecisionResponse(
            result.decisionId(),
            result.eventId(),
            result.outcome(),
            result.score(),
            result.hardRuleFired(),
            result.rulesetId(),
            result.rulesetVersionHash(),
            result.rulesFired(),
            result.degraded(),
            result.degradedReason(),
            result.latencyMs(),
            result.timestamp()
        );
    }
}
