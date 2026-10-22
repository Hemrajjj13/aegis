package com.aegis.core;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 100% deterministic, step-controllable clock implementation of EventClock.
 * Derives time strictly from internal microsecond state with zero wall-clock dependencies.
 */
public class DeterministicClock implements EventClock {
    private final AtomicLong currentMicros;

    public DeterministicClock(Instant initialInstant) {
        this(initialInstant.getEpochSecond() * 1_000_000L + initialInstant.getNano() / 1_000L);
    }

    public DeterministicClock(long initialMicros) {
        this.currentMicros = new AtomicLong(initialMicros);
    }

    @Override
    public Instant now() {
        long micros = currentMicros.get();
        return Instant.ofEpochSecond(micros / 1_000_000L, (micros % 1_000_000L) * 1_000L);
    }

    @Override
    public long currentTimeMicros() {
        return currentMicros.get();
    }

    public void advanceMicros(long deltaMicros) {
        if (deltaMicros < 0) {
            throw new IllegalArgumentException("Clock cannot advance backwards in time: " + deltaMicros);
        }
        currentMicros.addAndGet(deltaMicros);
    }

    public void advanceMillis(long deltaMillis) {
        advanceMicros(deltaMillis * 1_000L);
    }

    public void set(Instant newInstant) {
        long newMicros = newInstant.getEpochSecond() * 1_000_000L + newInstant.getNano() / 1_000L;
        if (newMicros < currentMicros.get()) {
            throw new IllegalArgumentException("Clock cannot be set backwards in time: " + newInstant);
        }
        currentMicros.set(newMicros);
    }
}
