package com.leaf.hyperdragshare.codex;

import android.view.MotionEvent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PortalInputHandoffTest {
    @Test public void rootRecoveryWaitsForTheExistingFallbackGestureToFinish() {
        PortalInputHandoff handoff = new PortalInputHandoff();
        assertTrue(handoff.shouldListen(false));
        assertFalse(handoff.acceptFallback(MotionEvent.ACTION_MOVE, 100, false));
        assertTrue(handoff.acceptFallback(MotionEvent.ACTION_DOWN, 100, false));
        assertFalse(handoff.acceptFallback(MotionEvent.ACTION_DOWN, 100, false));
        assertTrue(handoff.shouldListen(true));
        assertTrue(handoff.acceptFallback(MotionEvent.ACTION_MOVE, 100, true));
        assertFalse(handoff.acceptFallback(MotionEvent.ACTION_UP, 200, true));
        assertTrue(handoff.acceptFallback(MotionEvent.ACTION_UP, 100, true));
        handoff.finishFallback();
        assertFalse(handoff.shouldListen(true));
        assertFalse(handoff.acceptFallback(MotionEvent.ACTION_DOWN, 200, true));
        assertTrue(handoff.acceptFallback(MotionEvent.ACTION_DOWN, 200, false));
    }

    @Test public void aNewPhysicalDownCanEndAnInterruptedFallbackStream() {
        PortalInputHandoff handoff = new PortalInputHandoff();
        assertTrue(handoff.acceptFallback(MotionEvent.ACTION_DOWN, 100, false));
        assertFalse(handoff.missedFallbackEnd(90, true));
        assertFalse(handoff.missedFallbackEnd(100, true));
        assertFalse(handoff.missedFallbackEnd(200, false));
        assertTrue(handoff.missedFallbackEnd(200, true));
    }
}
