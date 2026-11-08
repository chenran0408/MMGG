package com.leaf.hyperdragshare.gestures.xposed;

import android.os.Bundle;

import com.leaf.hyperdragshare.gestures.config.ConfigContract;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MoreAppsSettingTest {
    @Test public void runtimeUsesThePersistedEnhancedPanelSetting() {
        Bundle enabled = new Bundle();
        enabled.putBoolean(ConfigContract.KEY_MORE_APPS_ENHANCED, true);
        assertTrue(GestureConfig.from(enabled).moreAppsEnhanced);

        Bundle disabled = new Bundle();
        disabled.putBoolean(ConfigContract.KEY_MORE_APPS_ENHANCED, false);
        assertFalse(GestureConfig.from(disabled).moreAppsEnhanced);

        assertTrue(GestureConfig.from(new Bundle()).moreAppsEnhanced
                == ConfigContract.DEFAULT_MORE_APPS_ENHANCED);
    }
}
