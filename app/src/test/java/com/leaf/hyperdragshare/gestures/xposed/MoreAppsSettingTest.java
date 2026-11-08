package com.leaf.hyperdragshare.gestures.xposed;

import android.os.Bundle;

import com.leaf.hyperdragshare.gestures.config.ConfigContract;
import com.leaf.hyperdragshare.gestures.config.AppTarget;

import org.json.JSONArray;
import org.json.JSONObject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

    @Test public void moreAppsTargetParsesForBottomAndHoneycombWithStablePosition() throws Exception {
        JSONObject target = AppTarget.moreApps().toJson();
        Bundle config = new Bundle();
        config.putString(ConfigContract.KEY_COMPONENTS, new JSONArray().put(target).toString());
        config.putString(ConfigContract.KEY_HONEYCOMB_COMPONENTS,
                new JSONArray().put(target).toString());

        GestureConfig parsed = GestureConfig.from(config);

        assertEquals(1, parsed.targets.size());
        assertTrue(parsed.targets.get(0).isMoreApps());
        assertEquals(1, parsed.honeycombTargets.size());
        assertTrue(parsed.honeycombTargets.get(0).isMoreApps());
        assertNotNull(parsed.honeycombPositions.find(AppTarget.MORE_APPS_POSITION_ID));
    }
}
