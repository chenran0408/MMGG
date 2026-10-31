package com.leaf.hyperdragshare.gestures.xposed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import com.leaf.hyperdragshare.gestures.config.ConfigContract;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class SystemServerFreeformConfigTest {
    @Test public void onlyExactAndroidSystemProcessIsAccepted() {
        assertTrue(SystemServerFreeformConfig.isSystemServerProcess("android", "android"));
        assertFalse(SystemServerFreeformConfig.isSystemServerProcess(
                "android", "android:ui"));
        assertFalse(SystemServerFreeformConfig.isSystemServerProcess(
                "com.android.systemui", "com.android.systemui"));
    }

    @Test public void missingSnapshotIsDefaultOff() {
        SystemServerFreeformConfig.Flags flags = SystemServerFreeformConfig.flags(null);
        assertFalse(flags.removeBlacklist);
        assertFalse(flags.removeCountLimit);
    }

    @Test public void switchesAreIndependent() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ConfigContract.KEY_REMOVE_FREEFORM_BLACKLIST, true);

        SystemServerFreeformConfig.Flags flags = SystemServerFreeformConfig.flags(bundle);

        assertTrue(flags.removeBlacklist);
        assertFalse(flags.removeCountLimit);
    }

    @Test public void bothSwitchesCanBeEnabled() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ConfigContract.KEY_REMOVE_FREEFORM_BLACKLIST, true);
        bundle.putBoolean(ConfigContract.KEY_REMOVE_FREEFORM_COUNT_LIMIT, true);

        SystemServerFreeformConfig.Flags flags = SystemServerFreeformConfig.flags(bundle);

        assertTrue(flags.removeBlacklist);
        assertTrue(flags.removeCountLimit);
    }
}
