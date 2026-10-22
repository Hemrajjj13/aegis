package com.aegis.core;

import com.aegis.core.domain.EvaluationResult;
import com.aegis.core.domain.PaymentEvent;

/**
 * Pure evaluation SPI in aegis-core. Zero Spring / zero I/O dependencies.
 */
public interface EvaluationCore {
    EvaluationResult evaluate(PaymentEvent event, EventClock clock);
}
