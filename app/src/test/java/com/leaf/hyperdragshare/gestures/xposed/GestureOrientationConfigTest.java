package com.leaf.hyperdragshare.gestures.xposed;

import com.leaf.hyperdragshare.gestures.config.ConfigContract;

import android.os.Bundle;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class GestureOrientationConfigTest {
    @Test
    public void hotBottomGapLoadsSeparatelyFromLegacyHeightAndDefaultsToZero() {
        GestureConfig legacy = GestureConfig.from(new Bundle());
        assertEquals(ConfigContract.DEFAULT_HOT_HEIGHT_PERCENT, legacy.hotHeightPercent);
        assertEquals(0, legacy.hotBottomGapDp);

        Bundle values = new Bundle();
        values.putInt(ConfigContract.KEY_HOT_HEIGHT_PERCENT, 15);
        values.putInt(ConfigContract.KEY_HOT_BOTTOM_GAP_DP, 48);
        GestureConfig configured = GestureConfig.from(values);
        assertEquals(15, configured.hotHeightPercent);
        assertEquals(48, configured.hotBottomGapDp);
    }

    @Test
    public void bottomStagesAndLaunchModesAreIndependentByOrientation() {
        GestureConfig config = GestureConfig.defaults();
        config.bottomPortraitEnabled = true;
        config.bottomLandscapeEnabled = false;
        config.bottomPortraitSecondStageEnabled = true;
        config.bottomLandscapeSecondStageEnabled = false;
        config.bottomPortraitFullscreen = false;
        config.bottomLandscapeFullscreen = true;
        config.bottomPortraitHoneycombFreeform = true;
        config.bottomLandscapeHoneycombFreeform = false;

        assertTrue(config.bottomEnabledFor(false));
        assertFalse(config.bottomEnabledFor(true));
        assertTrue(config.bottomSecondStageEnabledFor(false));
        assertFalse(config.bottomSecondStageEnabledFor(true));
        assertFalse(config.bottomFullscreenFor(false));
        assertTrue(config.bottomFullscreenFor(true));
        assertTrue(config.bottomHoneycombFreeformFor(false));
        assertFalse(config.bottomHoneycombFreeformFor(true));
    }

    @Test
    public void bottomCornerSettingsDefaultOnAndLoadIndependently() {
        GestureConfig defaults = GestureConfig.from(new Bundle());
        assertTrue(defaults.bottomLeftEnabled);
        assertTrue(defaults.bottomRightEnabled);

        Bundle values = new Bundle();
        values.putBoolean(ConfigContract.KEY_BOTTOM_LEFT_ENABLED, false);
        GestureConfig config = GestureConfig.from(values);
        assertFalse(config.bottomCornerEnabled(GestureGeometry.Corner.LEFT));
        assertTrue(config.bottomCornerEnabled(GestureGeometry.Corner.RIGHT));

        values.putBoolean(ConfigContract.KEY_BOTTOM_RIGHT_ENABLED, false);
        config = GestureConfig.from(values);
        assertFalse(config.bottomCornerEnabled(GestureGeometry.Corner.LEFT));
        assertFalse(config.bottomCornerEnabled(GestureGeometry.Corner.RIGHT));
    }

    @Test
    public void outsideMasterSwitchGatesBothOrientationsWithoutOverridingOrientationSettings() {
        Bundle values = new Bundle();
        values.putBoolean(ConfigContract.KEY_OUTSIDE_ENABLED, false);
        values.putBoolean(ConfigContract.KEY_OUTSIDE_PORTRAIT_ENABLED, true);
        values.putBoolean(ConfigContract.KEY_OUTSIDE_LANDSCAPE_ENABLED, true);
        GestureConfig disabled = GestureConfig.from(values);
        assertFalse(disabled.outsideEnabledFor(false));
        assertFalse(disabled.outsideEnabledFor(true));

        values.putBoolean(ConfigContract.KEY_OUTSIDE_ENABLED, true);
        values.putBoolean(ConfigContract.KEY_OUTSIDE_LANDSCAPE_ENABLED, false);
        GestureConfig enabledByOrientation = GestureConfig.from(values);
        assertTrue(enabledByOrientation.outsideEnabledFor(false));
        assertFalse(enabledByOrientation.outsideEnabledFor(true));
    }

    @Test
    public void outsideLongActionDefaultsToNoOpAndLoadsOnlySupportedActions() {
        GestureConfig defaults = GestureConfig.from(new Bundle());
        assertEquals(ConfigContract.ACTION_NONE, defaults.outsideLongAction);

        Bundle values = new Bundle();
        values.putInt(ConfigContract.KEY_OUTSIDE_LONG_ACTION, ConfigContract.ACTION_EDGE_PIN);
        assertEquals(ConfigContract.ACTION_EDGE_PIN,
                GestureConfig.from(values).outsideLongAction);

        values.putInt(ConfigContract.KEY_OUTSIDE_LONG_ACTION, 99);
        assertEquals(ConfigContract.ACTION_NONE,
                GestureConfig.from(values).outsideLongAction);
    }

    @Test
    public void optionalFreeformExtensionsDefaultOffAndReadFromSnapshot() {
        GestureConfig legacy = GestureConfig.from(new Bundle());
        assertFalse(legacy.notificationFreeformEnabled);
        assertFalse(legacy.miniMediaControlsEnabled);
        assertFalse(legacy.miniMediaAutoMute);
        assertFalse(legacy.removeFreeformBlacklist);
        assertFalse(legacy.removeFreeformCountLimit);

        Bundle values = new Bundle();
        values.putBoolean(ConfigContract.KEY_NOTIFICATION_FREEFORM_ENABLED, true);
        values.putBoolean(ConfigContract.KEY_MINI_MEDIA_CONTROLS_ENABLED, true);
        values.putBoolean(ConfigContract.KEY_MINI_MEDIA_AUTO_MUTE, true);
        values.putBoolean(ConfigContract.KEY_REMOVE_FREEFORM_BLACKLIST, true);
        values.putBoolean(ConfigContract.KEY_REMOVE_FREEFORM_COUNT_LIMIT, true);
        GestureConfig configured = GestureConfig.from(values);
        assertTrue(configured.notificationFreeformEnabled);
        assertTrue(configured.miniMediaControlsEnabled);
        assertTrue(configured.miniMediaAutoMute);
        assertTrue(configured.removeFreeformBlacklist);
        assertTrue(configured.removeFreeformCountLimit);
    }

}
