package com.leaf.hyperdragshare.gestures.xposed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FreeformStormGuardTest {
    private static final class FakeClock {
        long now;
        void tick(long millis) { now += millis; }
    }

    private static FreeformStormGuard guard(FakeClock clock) {
        return new FreeformStormGuard(() -> clock.now);
    }

    @Test public void fiveTasksInWindowTripBreaker() {
        FakeClock clock = new FakeClock();
        FreeformStormGuard guard = guard(clock);
        for (int i = 0; i < 4; i++) {
            assertFalse(guard.record("com.coolapk.market"));
            clock.tick(300);
        }
        assertTrue(guard.record("com.coolapk.market"));
        assertTrue(guard.isSuppressing("com.coolapk.market"));
    }

    @Test public void cooldownIgnoresEventsAndExpires() {
        FakeClock clock = new FakeClock();
        FreeformStormGuard guard = guard(clock);
        for (int i = 0; i < 5; i++) {
            guard.record("com.coolapk.market");
            clock.tick(300);
        }
        for (int i = 0; i < 20; i++) {
            assertFalse(guard.record("com.coolapk.market"));
            clock.tick(300);
        }
        assertTrue(guard.isSuppressing("com.coolapk.market"));
        clock.tick(FreeformStormGuard.DEFAULT_COOLDOWN_MS);
        assertFalse(guard.isSuppressing("com.coolapk.market"));
    }

    @Test public void quickRetriggerEscalatesAndPackagesAreIndependent() {
        FakeClock clock = new FakeClock();
        FreeformStormGuard guard = guard(clock);
        for (int i = 0; i < 5; i++) {
            guard.record("com.coolapk.market");
            clock.tick(100);
        }
        clock.tick(FreeformStormGuard.DEFAULT_COOLDOWN_MS + 1);
        for (int i = 0; i < 5; i++) {
            guard.record("com.coolapk.market");
            clock.tick(100);
        }
        assertTrue(guard.isSuppressing("com.coolapk.market"));
        assertTrue(guard.remainingCooldownMs("com.coolapk.market") > 30_000L);
        assertFalse(guard.isSuppressing("com.tencent.mobileqq"));
    }

    @Test public void oldEventsSlideOutAndEmptyPackageIsIgnored() {
        FakeClock clock = new FakeClock();
        FreeformStormGuard guard = guard(clock);
        for (int i = 0; i < 4; i++) {
            assertFalse(guard.record("com.coolapk.market"));
            clock.tick(300);
        }
        clock.tick(FreeformStormGuard.DEFAULT_WINDOW_MS + 1_000L);
        for (int i = 0; i < 4; i++) {
            assertFalse(guard.record("com.coolapk.market"));
            clock.tick(300);
        }
        assertFalse(guard.isSuppressing("com.coolapk.market"));
        for (int i = 0; i < 10; i++) assertFalse(guard.record(""));
        assertFalse(guard.isSuppressing(""));
    }
}
