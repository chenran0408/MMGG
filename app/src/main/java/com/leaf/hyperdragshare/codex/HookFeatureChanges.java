package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.system.Os;

/** Tracks changes made since the current Android runtime started. */
final class HookFeatureChanges {
    private static final String BASELINE = "restart_baseline.";
    private static final String RUNTIME = "restart_runtime.";

    private HookFeatureChanges() {}

    static String runtime(Context context) {
        // The settings Activity is in a normal zygote child. Reopening it keeps the
        // same parent; restarting Android replaces zygote. Boot count covers PID reuse after reboot.
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        return boot + ":" + Os.getppid();
    }

    static boolean pending(SharedPreferences prefs, String key, boolean desired, String runtime) {
        return runtime.equals(prefs.getString(RUNTIME + key, null))
                && prefs.getBoolean(BASELINE + key, desired) != desired;
    }

    static boolean save(SharedPreferences prefs, String key, boolean previous, boolean desired,
                        String runtime) {
        boolean baseline = runtime.equals(prefs.getString(RUNTIME + key, null))
                ? prefs.getBoolean(BASELINE + key, previous) : previous;
        SharedPreferences.Editor change = prefs.edit().putBoolean(key, desired);
        if (desired == baseline) {
            change.remove(BASELINE + key).remove(RUNTIME + key);
        } else {
            change.putBoolean(BASELINE + key, baseline).putString(RUNTIME + key, runtime);
        }
        return change.commit();
    }
}
