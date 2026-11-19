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
}
