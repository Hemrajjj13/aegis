package com.aegis.core;

import java.time.Instant;

/**
 * Deterministic clock interface for all temporal operations in Project Aegis.
 * Per Law 3.2, direct wall-clock calls (System.currentTimeMillis, Instant.now) are prohibited.
 */
public interface EventClock {
    Instant now();
    long currentTimeMicros();
}
