package com.leaf.hyperdragshare.gestures.xposed;

import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Process-local breaker for bursts of newly appeared freeform tasks. */
final class FreeformStormGuard {
    static final long DEFAULT_WINDOW_MS = 2_000L;
    static final int DEFAULT_THRESHOLD = 5;
    static final long DEFAULT_COOLDOWN_MS = 15_000L;
    static final int DEFAULT_ESCALATION_FACTOR = 4;
    static final long DEFAULT_MAX_COOLDOWN_MS = 120_000L;
    static final long DEFAULT_RETRIGGER_MS = 30_000L;

    private static final class PackageState {
        final ArrayDeque<Long> events = new ArrayDeque<>();
        long suppressUntil;
        long lastTriggerAt;
        long lastCooldown;
    }

    interface LongSupplier { long getAsLong(); }

    private final long windowMs;
    private final int threshold;
    private final long cooldownMs;
    private final int escalationFactor;
    private final long maxCooldownMs;
    private final long retriggerMs;
    private final LongSupplier clock;
    private final Map<String, PackageState> states = new HashMap<>();

    FreeformStormGuard(LongSupplier clock) {
        this(DEFAULT_WINDOW_MS, DEFAULT_THRESHOLD, DEFAULT_COOLDOWN_MS,
                DEFAULT_ESCALATION_FACTOR, DEFAULT_MAX_COOLDOWN_MS, DEFAULT_RETRIGGER_MS,
                clock);
    }

    FreeformStormGuard(long windowMs, int threshold, long cooldownMs,
                       int escalationFactor, long maxCooldownMs, long retriggerMs,
                       LongSupplier clock) {
        this.windowMs = windowMs;
        this.threshold = threshold;
        this.cooldownMs = cooldownMs;
        this.escalationFactor = escalationFactor;
        this.maxCooldownMs = maxCooldownMs;
        this.retriggerMs = retriggerMs;
        this.clock = clock;
    }

    synchronized boolean isSuppressing(String packageName) {
        PackageState state = states.get(packageName);
        return state != null && state.suppressUntil > clock.getAsLong();
    }

    /** Records one newly appeared freeform task; returns true when cooldown starts. */
    synchronized boolean record(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        long now = clock.getAsLong();
        PackageState state = states.computeIfAbsent(packageName, ignored -> new PackageState());
        if (state.suppressUntil > now) return false;
        while (!state.events.isEmpty() && now - state.events.peekFirst() > windowMs) {
            state.events.removeFirst();
        }
        state.events.addLast(now);
        if (state.events.size() < threshold) return false;
        boolean escalating = state.lastTriggerAt > 0L
                && now - state.suppressUntil <= retriggerMs;
        long nextCooldown = escalating
                ? Math.min(maxCooldownMs, state.lastCooldown * escalationFactor)
                : cooldownMs;
        state.lastCooldown = nextCooldown;
        state.suppressUntil = now + nextCooldown;
        state.lastTriggerAt = now;
        state.events.clear();
        return true;
    }

    synchronized long remainingCooldownMs(String packageName) {
        PackageState state = states.get(packageName);
        return state == null ? 0L : Math.max(0L, state.suppressUntil - clock.getAsLong());
    }

    static final FreeformStormGuard processWide = new FreeformStormGuard(
            SystemClock::elapsedRealtime);
}
