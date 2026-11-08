package com.leaf.hyperdragshare.gestures.config;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class MoreAppsTargetMigrationTest {
    @Test public void migratesLegacyEntryOnceAndKeepsUserRemoval() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences preferences = context.getSharedPreferences(
                ConfigContract.PREFS, Context.MODE_PRIVATE);
        preferences.edit().clear()
                .putString(ConfigContract.KEY_COMPONENTS, "[]")
                .putString(ConfigContract.KEY_HONEYCOMB_COMPONENTS, "[]")
                .putInt(ConfigContract.KEY_FAN_MAX_TARGETS,
                        ConfigContract.DEFAULT_FAN_MAX_TARGETS)
                .putInt(ConfigContract.KEY_HONEYCOMB_MAX_TARGETS,
                        ConfigContract.DEFAULT_HONEYCOMB_MAX_TARGETS)
                .apply();

        ConfigStore store = new ConfigStore(context);

        assertTrue(hasMoreApps(preferences.getString(ConfigContract.KEY_COMPONENTS, "[]")));
        assertTrue(hasMoreApps(preferences.getString(
                ConfigContract.KEY_HONEYCOMB_COMPONENTS, "[]")));
        assertTrue(preferences.getInt(ConfigContract.KEY_FAN_MAX_TARGETS, 0) >= 1);
        assertTrue(preferences.getInt(ConfigContract.KEY_HONEYCOMB_MAX_TARGETS, 0) >= 1);
        store.setTargets(java.util.Collections.emptyList());
        store.setHoneycombTargets(java.util.Collections.emptyList());

        new ConfigStore(context);

        assertFalse(hasMoreApps(preferences.getString(ConfigContract.KEY_COMPONENTS, "[]")));
        assertFalse(hasMoreApps(preferences.getString(
                ConfigContract.KEY_HONEYCOMB_COMPONENTS, "[]")));
    }

    private static boolean hasMoreApps(String raw) throws Exception {
        JSONArray targets = new JSONArray(raw);
        for (int i = 0; i < targets.length(); i++) {
            AppTarget target = AppTarget.fromJson(targets.optJSONObject(i));
            if (target != null && target.isMoreApps()) return true;
        }
        return false;
    }
}
