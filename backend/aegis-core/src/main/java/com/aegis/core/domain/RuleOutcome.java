package com.aegis.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable outcome descriptor for an individual rule execution.
 */
public record RuleOutcome(
    @JsonProperty("rule_id") String ruleId,
    @JsonProperty("name") String name,
    @JsonProperty("score_impact") double scoreImpact,
    @JsonProperty("reason_code") String reasonCode
) {
    public RuleOutcome {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("rule_id must not be blank");
        }
        if (name == null || name.isBlank()) {
            name = ruleId;
        }
        if (reasonCode == null || reasonCode.isBlank()) {
            reasonCode = "RULE_FIRED";
        }
    }
}
