package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public final class HookFeatureFlagsTest {
    @Test public void existingInstallationPreservesDefaultsAndLegacyGestureSetting() {
        HookFeatureFlags flags = HookFeatureFlags.read(preferences());
        assertTrue(flags.portal());
        assertTrue(flags.freeform());
        assertFalse(flags.gestures(false));
        assertTrue(flags.gestures(true));
    }

    @Test public void allEightSelectionsPersistAndOnlyWindowFeaturesStartWindowRuntime() {
        SharedPreferences prefs = preferences();
        for (int mask = 0; mask < 8; mask++) {
            boolean portal = (mask & 1) != 0;
            boolean freeform = (mask & 2) != 0;
            boolean gestures = (mask & 4) != 0;
            assertTrue(prefs.edit()
                    .putBoolean(HookFeatureFlags.PORTAL, portal)
                    .putBoolean(HookFeatureFlags.FREEFORM, freeform)
                    .putBoolean(HookFeatureFlags.GESTURES, gestures)
                    .commit());
            HookFeatureFlags flags = HookFeatureFlags.read(prefs);
            assertEquals(portal, flags.portal());
            assertEquals(freeform, flags.freeform());
            assertEquals(gestures, flags.gestures(!gestures));
            assertEquals(freeform || gestures, flags.windowRuntime());
        }
    }

    private static SharedPreferences preferences() {
        return RuntimeEnvironment.getApplication().getSharedPreferences(
                HookFeatureFlags.PREFS, Context.MODE_PRIVATE);
    }

    @Test public void savedSwitchSurvivesReopeningAndRevertingOnlyClearsThatSwitch() {
        SharedPreferences prefs = preferences();
        String runtime = "6:1210";
        assertTrue(HookFeatureChanges.save(prefs, HookFeatureFlags.PORTAL, true, false, runtime));
        assertTrue(HookFeatureChanges.save(prefs, HookFeatureFlags.FREEFORM, true, false, runtime));
        SharedPreferences reopened = preferences();
        assertFalse(HookFeatureFlags.read(reopened).portal());
        assertTrue(HookFeatureChanges.pending(reopened, HookFeatureFlags.PORTAL, false, runtime));
        assertTrue(HookFeatureChanges.save(reopened, HookFeatureFlags.PORTAL, false, true, runtime));
        assertFalse(HookFeatureChanges.pending(reopened, HookFeatureFlags.PORTAL, true, runtime));
        assertTrue(HookFeatureChanges.pending(reopened, HookFeatureFlags.FREEFORM, false, runtime));
    }

    @Test public void softAndFullRestartEstablishNewBaselineIncludingLegacyGestureValue() {
        SharedPreferences prefs = preferences();
        String key = HookFeatureFlags.GESTURES;
        boolean legacy = HookFeatureFlags.read(prefs).gestures(false);
        assertFalse(legacy);
        assertTrue(HookFeatureChanges.save(prefs, key, legacy, true, "6:1210"));
        assertTrue(HookFeatureChanges.pending(prefs, key, true, "6:1210"));
        assertFalse(HookFeatureChanges.pending(prefs, key, true, "6:8020"));
        assertFalse(HookFeatureChanges.pending(prefs, key, true, "7:1210"));
        assertTrue(HookFeatureChanges.save(prefs, key, true, false, "6:8020"));
        assertTrue(HookFeatureChanges.pending(prefs, key, false, "6:8020"));
    }
}
