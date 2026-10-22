package com.aegis.decision.service;

import com.aegis.core.EvaluationCore;
import com.aegis.core.EventClock;
import com.aegis.core.domain.DecisionOutcome;
import com.aegis.core.domain.EvaluationResult;
import com.aegis.core.domain.PaymentEvent;
import com.aegis.core.domain.RuleOutcome;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * StubEvaluationCore implementation of EvaluationCore.
 * Stub rule: amount_inr >= 50000 -> REVIEW, else APPROVE.
 */
@Service
public class StubEvaluationCore implements EvaluationCore {
    public static final double REVIEW_THRESHOLD_INR = 50000.0;

    @Override
    public EvaluationResult evaluate(PaymentEvent event, EventClock clock) {
        long startNanos = System.nanoTime();
        double amountInr = event.getAmountInr();

        DecisionOutcome outcome;
        double score;
        List<RuleOutcome> rulesFired;

        if (amountInr >= REVIEW_THRESHOLD_INR) {
            outcome = DecisionOutcome.REVIEW;
            score = 75.0;
            rulesFired = List.of(new RuleOutcome(
                    "STUB-HIGH-AMOUNT",
                    "Transaction amount exceeds 50,000 INR threshold",
                    75.0,
                    "AMOUNT_GE_50K"
            ));
        } else {
            outcome = DecisionOutcome.APPROVE;
            score = 15.0;
            rulesFired = List.of();
        }

        double latencyMs = (System.nanoTime() - startNanos) / 1_000_000.0;

        return new EvaluationResult(
                UUID.randomUUID(),
                event.eventId(),
                outcome,
                score,
                outcome == DecisionOutcome.REVIEW,
                "stub-ruleset-v1",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                rulesFired,
                Map.of("amount_inr", amountInr, "currency", event.currency()),
                false,
                null,
                latencyMs,
                clock.now()
        );
    }
}
