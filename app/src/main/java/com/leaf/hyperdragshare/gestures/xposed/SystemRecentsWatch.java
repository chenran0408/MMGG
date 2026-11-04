package com.leaf.hyperdragshare.gestures.xposed;

import android.os.Handler;
import android.os.SystemClock;

/**
 * Polls HyperOS system recents visibility after a side-list launch so outside
 * capture can resume once the recents screen closes. Version tokens invalidate
 * pending polls after a restart.
 */
final class SystemRecentsWatch {
    interface Host {
        boolean isRecentsVisible();

        void onWatchStopped(String reason);
    }

    enum Decision { PROBE, FAST, SLOW, IDLE, STOP_TIMEOUT, STOP_CLOSED }

    private static final long FAST_WATCH_MS = 5000L;
    private static final long SLOW_WATCH_MS = 120_000L;
    private static final long IDLE_WATCH_MS = 10_000L;
    private static final long MAX_WATCH_MS = 10L * 60L * 1000L;
    private static final int MAX_PROBE_ATTEMPTS = 12;
    private static final long PROBE_DELAY_MS = 100L;
    private static final long FAST_DELAY_MS = 450L;
    private static final long SLOW_DELAY_MS = 1500L;
    private static final long FIRST_DELAY_MS = 100L;

    private final Handler handler;
    private final Host host;
    private long version;
    private boolean active;

    SystemRecentsWatch(Handler handler, Host host) {
        this.handler = handler;
        this.host = host;
    }

    boolean isActive() {
        return active;
    }

    void start() {
        version++;
        active = true;
        long startedAt = SystemClock.uptimeMillis();
        long expected = version;
        handler.postDelayed(() -> poll(expected, 0, false, startedAt), FIRST_DELAY_MS);
    }

    private void poll(long expected, int attempts, boolean wasVisible, long startedAt) {
        if (expected != version)
            return;
        long elapsed = SystemClock.uptimeMillis() - startedAt;
        Decision decision = decide(elapsed, host.isRecentsVisible(), wasVisible, attempts);
        if (decision == Decision.STOP_TIMEOUT) {
            active = false;
            host.onWatchStopped("timeout");
            return;
        }
        if (decision == Decision.STOP_CLOSED) {
            active = false;
            host.onWatchStopped("closed");
            return;
        }
        handler.postDelayed(() -> poll(expected, attempts + 1, decision != Decision.PROBE, startedAt),
                delayFor(decision));
    }

    static Decision decide(long elapsed, boolean visible, boolean wasVisible, int attempts) {
        if (elapsed > MAX_WATCH_MS)
            return Decision.STOP_TIMEOUT;
        if (visible) {
            if (elapsed < FAST_WATCH_MS)
                return Decision.FAST;
            if (elapsed < SLOW_WATCH_MS)
                return Decision.SLOW;
            return Decision.IDLE;
        }
        if (!wasVisible && attempts < MAX_PROBE_ATTEMPTS)
            return Decision.PROBE;
        return Decision.STOP_CLOSED;
    }

    static long delayFor(Decision decision) {
        switch (decision) {
            case PROBE:
                return PROBE_DELAY_MS;
            case FAST:
                return FAST_DELAY_MS;
            case SLOW:
                return SLOW_DELAY_MS;
            default:
                return IDLE_WATCH_MS;
        }
    }
}
