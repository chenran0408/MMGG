package com.leaf.hyperdragshare.gestures.xposed;

import android.app.Application;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.leaf.hyperdragshare.gestures.config.ConfigContract;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Keeps the two optional system_server freeform switches in a local snapshot.
 * The Xiaomi hooks never perform Provider IPC on their hot path.
 */
public final class SystemServerFreeformConfig {
    private static final String TAG = "DragShare/SystemFreeformConfig";
    private static volatile boolean installed;
    private static volatile boolean removeBlacklist;
    private static volatile boolean removeCountLimit;
    private static boolean observerRegistered;

    private SystemServerFreeformConfig() {}

    static boolean isSystemServerProcess(String packageName, String processName) {
        return "android".equals(packageName) && "android".equals(processName);
    }

    /** Called only from the exact android/android Xposed process. */
    public static synchronized void install(String packageName, String processName,
            ClassLoader classLoader) {
        if (!isSystemServerProcess(packageName, processName)
                || installed || classLoader == null) return;
        installed = true;
        FreeformRestrictionHooks.install(classLoader,
                SystemServerFreeformConfig::removeBlacklistEnabled,
                SystemServerFreeformConfig::removeCountLimitEnabled);
        try {
            XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length > 0 && param.args[0] instanceof Context) {
                        attach((Context) param.args[0]);
                    }
                }
            });
        } catch (RuntimeException | LinkageError error) {
            log("Application.attach hook unavailable; trying system context hook: "
                    + error.getClass().getSimpleName());
        }
        try {
            Class<?> activityThread = Class.forName(
                    "android.app.ActivityThread", false, classLoader);
            XposedBridge.hookMethod(activityThread.getDeclaredMethod("getSystemContext"),
                    new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.getResult() instanceof Context) {
                        attach((Context) param.getResult());
                    }
                }
            });
        } catch (RuntimeException | LinkageError error) {
            log("system context hook unavailable; restrictions remain disabled: "
                    + error.getClass().getSimpleName());
        } catch (ReflectiveOperationException error) {
            log("system context method unavailable; restrictions remain disabled: "
                    + error.getClass().getSimpleName());
        }
    }

    static Flags flags(Bundle bundle) {
        if (bundle == null) return Flags.DISABLED;
        return new Flags(
                bundle.getBoolean(ConfigContract.KEY_REMOVE_FREEFORM_BLACKLIST, false),
                bundle.getBoolean(ConfigContract.KEY_REMOVE_FREEFORM_COUNT_LIMIT, false));
    }

    public static boolean removeBlacklistEnabled() {
        return removeBlacklist;
    }

    public static boolean removeCountLimitEnabled() {
        return removeCountLimit;
    }

    private static synchronized void attach(Context context) {
        if (observerRegistered) return;
        Handler handler = new Handler(Looper.getMainLooper());
        try {
            context.getContentResolver().registerContentObserver(
                    ConfigContract.RUNTIME_URI, false, new ContentObserver(handler) {
                        @Override public void onChange(boolean selfChange) {
                            refresh(context);
                        }
                    });
            observerRegistered = true;
        } catch (RuntimeException error) {
            removeBlacklist = false;
            removeCountLimit = false;
            log("runtime observer unavailable; restrictions disabled: "
                    + error.getClass().getSimpleName());
            return;
        }
        refresh(context);
    }

    private static void refresh(Context context) {
        try {
            Bundle bundle = context.getContentResolver().call(
                    ConfigContract.URI, "get", null, null);
            Flags next = flags(bundle);
            removeBlacklist = next.removeBlacklist;
            removeCountLimit = next.removeCountLimit;
        } catch (RuntimeException | LinkageError error) {
            removeBlacklist = false;
            removeCountLimit = false;
            log("configuration unavailable; restrictions disabled: "
                    + error.getClass().getSimpleName());
        }
    }

    static final class Flags {
        static final Flags DISABLED = new Flags(false, false);
        final boolean removeBlacklist;
        final boolean removeCountLimit;

        Flags(boolean removeBlacklist, boolean removeCountLimit) {
            this.removeBlacklist = removeBlacklist;
            this.removeCountLimit = removeCountLimit;
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
