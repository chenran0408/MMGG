package com.leaf.hyperdragshare.gestures.xposed;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The Xposed API artifact is a compile-time stub ("Stub!" bodies), so the vendor
 * reflection helpers can only be JVM-verified on their fallback paths; successful
 * parsing still needs the real framework on device.
 */
@RunWith(RobolectricTestRunner.class)
public final class MiniMediaControlsTest {

    @Test public void taskIdAndBoundsFallBackWhenVendorReflectionIsUnavailable() {
        Object info = new Object() {
            public int getTaskId() { return 7; }
            public Object getScaledBounds() { return new Object(); }
        };
        assertEquals(-1, MiniMediaControls.miniMediaTaskId(info));
        assertEquals(-1, MiniMediaControls.miniMediaTaskId(null));
        assertNull(MiniMediaControls.miniMediaBounds(info));
        assertNull(MiniMediaControls.miniMediaBounds(null));
    }

    @Test public void packageNameFallsBackToEmptyWhenVendorReflectionIsUnavailable() {
        Object info = new Object() {
            public Object getTaskInfo() { return new Object(); }
        };
        assertEquals("", MiniMediaControls.miniMediaPackage(info));
        assertEquals("", MiniMediaControls.miniMediaPackage(null));
    }

    @Test public void visibilityFallsBackToVisibleWhenVendorReflectionIsUnavailable() {
        assertTrue(MiniMediaControls.isMiniMediaTaskVisible(new Object()));
        assertTrue(MiniMediaControls.isMiniMediaTaskVisible(null));
    }

    @Test public void booleanCallFallsBackOnNullTargetsAndUnavailableReflection() {
        Object info = new Object() {
            public boolean isMiniState() { return true; }
        };
        assertTrue(MiniMediaControls.booleanCall(null, "isMiniState", true));
        assertFalse(MiniMediaControls.booleanCall(info, "isMiniState", false));
        assertFalse(MiniMediaControls.booleanCall(new Object(), "isMiniState", false));
    }

    @Test public void gatedTaskStatesNeverCreateTheController() {
        MiniMediaControls controls = new MiniMediaControls(
                RuntimeEnvironment.getApplication(), null, null, null);

        controls.updateMiniMedia(null, enabledConfig());
        controls.updateMiniMedia(new Object() {
            public int getTaskId() { return 7; }
            public boolean isMiniState() { return false; }
        }, enabledConfig());
        controls.clearMiniMediaForTask(7);
        controls.refreshMiniMediaConfig(enabledConfig());
    }

    private static GestureConfig enabledConfig() {
        GestureConfig config = GestureConfig.defaults();
        config.miniMediaControlsEnabled = true;
        return config;
    }
}
