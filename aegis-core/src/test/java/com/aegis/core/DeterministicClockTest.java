package com.aegis.core;

import com.aegis.core.domain.DecisionOutcome;
import com.aegis.core.domain.EvaluationResult;
import com.aegis.core.domain.PaymentEvent;
import com.aegis.core.domain.RuleOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeterministicClockTest {

    @Test
    @DisplayName("Assert exact microsecond stepping on DeterministicClock.advanceMicros()")
    void testExactMicrosecondStepping() {
        Instant baseTime = Instant.parse("2026-10-22T12:00:00.000Z");
        DeterministicClock clock = new DeterministicClock(baseTime);

        assertThat(clock.now()).isEqualTo(baseTime);
        long initialMicros = clock.currentTimeMicros();

        clock.advanceMicros(150);
        assertThat(clock.currentTimeMicros()).isEqualTo(initialMicros + 150);
        assertThat(clock.now()).isEqualTo(baseTime.plusNanos(150_000));

        clock.advanceMillis(10);
        assertThat(clock.currentTimeMicros()).isEqualTo(initialMicros + 150 + 10_000);

        assertThatThrownBy(() -> clock.advanceMicros(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backwards in time");
    }

    @Test
    @DisplayName("Assert set forward works and setting backwards throws exception")
    void testClockSet() {
        Instant t0 = Instant.parse("2026-10-22T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-22T10:05:00Z");
        Instant past = Instant.parse("2026-10-22T09:59:59Z");

        DeterministicClock clock = new DeterministicClock(t0);
        clock.set(t1);
        assertThat(clock.now()).isEqualTo(t1);

        assertThatThrownBy(() -> clock.set(past))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backwards in time");
    }

    @Test
    @DisplayName("Assert PaymentEvent and EvaluationResult immutability and score clamping")
    void testDomainRecords() {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-22T12:00:00Z");

        PaymentEvent event = new PaymentEvent(
                "evt_1001",
                now,
                1L,
                tenantId,
                "acc_hash_123",
                "tok_card_456",
                5_500_000L, // 55,000 INR
                "INR",
                "CARD",
                "m_99",
                "5411",
                "Superstore",
                "WEB",
                "192.168.1.1",
                "dev_1",
                "Mozilla/5.0",
                null,
                Map.of("note", "order #101")
        );

        assertThat(event.getAmountInr()).isEqualTo(55000.00);

        RuleOutcome rule = new RuleOutcome("RULE-01", "High Amount Rule", 25.0, "AMOUNT_GT_50K");
        assertThat(rule.ruleId()).isEqualTo("RULE-01");

        // Test score clamping (> 100.0 clamped to 100.0)
        EvaluationResult resultHigh = new EvaluationResult(
                UUID.randomUUID(),
                event.eventId(),
                DecisionOutcome.REVIEW,
                150.0,
                false,
                "default",
                "hash",
                List.of(rule),
                Map.of("velocity", 3),
                false,
                null,
                2.5,
                now
        );
        assertThat(resultHigh.score()).isEqualTo(100.0);

        // Test score clamping (< 0.0 clamped to 0.0)
        EvaluationResult resultLow = new EvaluationResult(
                UUID.randomUUID(),
                event.eventId(),
                DecisionOutcome.APPROVE,
                -10.0,
                false,
                "default",
                "hash",
                List.of(),
                Map.of(),
                false,
                null,
                1.2,
                now
        );
        assertThat(resultLow.score()).isEqualTo(0.0);
    }
}
