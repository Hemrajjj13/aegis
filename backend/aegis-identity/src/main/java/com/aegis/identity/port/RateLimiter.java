package com.aegis.identity.port;

import java.time.Duration;
import java.time.Instant;

public interface RateLimiter {
    boolean tryAcquire(String key, int maxRequests, Duration window);
    int recordFailure(String key);
    int getFailureCount(String key);
    void resetFailures(String key);
    boolean isLocked(String key);
    Instant getLockoutUntil(String key);
    void lock(String key, Duration duration);
}

