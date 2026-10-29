package com.leaf.hyperdragshare.gestures.xposed;

import android.graphics.Rect;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FanRuntimePolicyTest {

    @Test
    public void bottomRequiresReadyRuntimeNoImeEnoughTargetsAndEnabledEdge() {
        assertTrue(FanRuntimePolicy.canStartBottom(true, false, 3,
                true, true, false));
        assertTrue(FanRuntimePolicy.canStartBottom(true, false, 8,
                true, false, true));
        assertFalse(FanRuntimePolicy.canStartBottom(false, false, 3,
                true, true, false));
        assertFalse(FanRuntimePolicy.canStartBottom(true, true, 3,
                true, true, false));
        assertFalse(FanRuntimePolicy.canStartBottom(true, false, 2,
                true, true, false));
        assertFalse(FanRuntimePolicy.canStartBottom(true, false, 3,
                false, true, false));
        assertFalse(FanRuntimePolicy.canStartBottom(true, false, 3,
                true, false, false));
    }

    @Test
    public void sideRequiresReadyRuntimeAndResolvedTargets() {
        assertTrue(FanRuntimePolicy.canStartSide(true, true));
        assertFalse(FanRuntimePolicy.canStartSide(false, true));
        assertFalse(FanRuntimePolicy.canStartSide(true, false));
    }

    @Test
    public void imeCapturePreservesUnknownBoundsBehavior() {
        assertTrue(FanRuntimePolicy.shouldCaptureOutsideWithIme(false, null));
        assertTrue(FanRuntimePolicy.shouldCaptureOutsideWithIme(true,
                new Rect(0, 760, 2400, 1080)));
        assertFalse(FanRuntimePolicy.shouldCaptureOutsideWithIme(true, null));
    }
}
