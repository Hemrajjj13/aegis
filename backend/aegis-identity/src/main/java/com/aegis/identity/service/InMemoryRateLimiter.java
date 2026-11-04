package com.aegis.identity.service;

import com.aegis.identity.port.RateLimiter;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InMemoryRateLimiter implements RateLimiter {
    private Clock clock = Clock.systemUTC();

    private final ConcurrentHashMap<String, Deque<Instant>> requestBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> failureCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lockouts = new ConcurrentHashMap<>();

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public Clock getClock() {
        return this.clock;
    }

    @Override
    public synchronized boolean tryAcquire(String key, int maxRequests, Duration window) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);

        Deque<Instant> timestamps = requestBuckets.computeIfAbsent(key, k -> new ArrayDeque<>());

        // Evict expired timestamps
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequests) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    @Override
    public int recordFailure(String key) {
        return failureCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    @Override
    public int getFailureCount(String key) {
        AtomicInteger counter = failureCounters.get(key);
        return counter != null ? counter.get() : 0;
    }

    @Override
    public void resetFailures(String key) {
        failureCounters.remove(key);
        lockouts.remove(key);
    }

    public void resetAll() {
        requestBuckets.clear();
        failureCounters.clear();
        lockouts.clear();
    }

    @Override
    public boolean isLocked(String key) {
        Instant until = lockouts.get(key);
        if (until == null) {
            return false;
        }
        if (clock.instant().isAfter(until)) {
            lockouts.remove(key);
            failureCounters.remove(key);
            return false;
        }
        return true;
    }

    @Override
    public Instant getLockoutUntil(String key) {
        return lockouts.get(key);
    }

    @Override
    public void lock(String key, Duration duration) {
        lockouts.put(key, clock.instant().plus(duration));
    }
}

