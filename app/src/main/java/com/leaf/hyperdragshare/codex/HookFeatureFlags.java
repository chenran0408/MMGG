package com.leaf.hyperdragshare.codex;

import android.content.SharedPreferences;

/** One startup snapshot shared by the hook entry points in this process. */
public record HookFeatureFlags(boolean portal, boolean freeform, Boolean gesturesOverride) {
    public static final String PREFS = "mmgg_hook_features";
    public static final String PORTAL = "portal";
    public static final String FREEFORM = "freeform";
    public static final String GESTURES = "gestures";
    private static volatile HookFeatureFlags startup = new HookFeatureFlags(true, true, null);

    public static HookFeatureFlags read(SharedPreferences prefs) {
        return new HookFeatureFlags(prefs.getBoolean(PORTAL, true),
                prefs.getBoolean(FREEFORM, true),
                prefs.contains(GESTURES) ? prefs.getBoolean(GESTURES, true) : null);
    }

    static void setStartup(HookFeatureFlags flags) {
        startup = flags;
    }

    public static HookFeatureFlags startup() {
        return startup;
    }

    public boolean gestures(boolean legacyEnabled) {
        return gesturesOverride == null ? legacyEnabled : gesturesOverride;
    }

    public boolean windowRuntime() {
        return gestures(true) || freeform;
    }
}
