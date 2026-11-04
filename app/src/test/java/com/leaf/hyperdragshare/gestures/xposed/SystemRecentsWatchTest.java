package com.leaf.hyperdragshare.gestures.xposed;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SystemRecentsWatchTest {
    @Test public void firstPollsProbeQuicklyUntilRecentsAppear() {
        assertEquals(SystemRecentsWatch.Decision.PROBE,
                SystemRecentsWatch.decide(0L, false, false, 0));
        assertEquals(SystemRecentsWatch.Decision.PROBE,
                SystemRecentsWatch.decide(1000L, false, false, 11));
    }

    @Test public void probeWindowClosesAfterTwelveAttempts() {
        assertEquals(SystemRecentsWatch.Decision.STOP_CLOSED,
                SystemRecentsWatch.decide(2000L, false, false, 12));
    }

    @Test public void closingAfterBeingVisibleStopsTheWatch() {
        assertEquals(SystemRecentsWatch.Decision.STOP_CLOSED,
                SystemRecentsWatch.decide(6000L, false, true, 3));
    }

    @Test public void visiblePollingBacksOffByElapsed() {
        assertEquals(SystemRecentsWatch.Decision.FAST,
                SystemRecentsWatch.decide(1000L, true, false, 2));
        assertEquals(SystemRecentsWatch.Decision.SLOW,
                SystemRecentsWatch.decide(6000L, true, true, 4));
        assertEquals(SystemRecentsWatch.Decision.IDLE,
                SystemRecentsWatch.decide(130_000L, true, true, 8));
    }

    @Test public void hardTimeoutAlwaysStops() {
        assertEquals(SystemRecentsWatch.Decision.STOP_TIMEOUT,
                SystemRecentsWatch.decide(600_001L, true, true, 100));
    }

    @Test public void delaysMatchPreviousSchedule() {
        assertEquals(100L, SystemRecentsWatch.delayFor(SystemRecentsWatch.Decision.PROBE));
        assertEquals(450L, SystemRecentsWatch.delayFor(SystemRecentsWatch.Decision.FAST));
        assertEquals(1500L, SystemRecentsWatch.delayFor(SystemRecentsWatch.Decision.SLOW));
        assertEquals(10_000L, SystemRecentsWatch.delayFor(SystemRecentsWatch.Decision.IDLE));
    }
}
