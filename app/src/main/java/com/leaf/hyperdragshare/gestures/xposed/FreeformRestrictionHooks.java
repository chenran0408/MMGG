package com.leaf.hyperdragshare.gestures.xposed;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BooleanSupplier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Opt-in Xiaomi freeform eligibility and stack-count adjustments for the system process. */
public final class FreeformRestrictionHooks {
    private static final String TAG = "DragShare/FreeformRestrictions";
    private static final String ADAPTER = "android.util.MiuiMultiWindowAdapter";
    private static final String UTILS = "android.util.MiuiMultiWindowUtils";
    private static final String STACK_STRATEGY =
            "com.android.server.wm.MiuiFreeFormStackDisplayStrategy";
    private static final String ACTIVITY_STACK = "com.android.server.wm.MiuiFreeFormActivityStack";
    /** Value used by the reference Xiaomi hook for this exact method contract. */
    static final int REFERENCE_MAX_FREEFORM_STACK_COUNT = 128;

    private FreeformRestrictionHooks() {}

    /** Call from android only; both options are supplied by the existing module config. */
    public static void install(ClassLoader classLoader, BooleanSupplier removeBlacklist,
            BooleanSupplier removeCountLimit) {
        if (classLoader == null) return;
        installBlacklist(classLoader, removeBlacklist);
        installCountLimit(classLoader, removeCountLimit);
    }

    private static void installBlacklist(ClassLoader loader, BooleanSupplier enabled) {
        if (enabled == null) return;
        try {
            Class<?> adapter = Class.forName(ADAPTER, false, loader);
            Class<?> utils = Class.forName(UTILS, false, loader);
            Method blacklist = adapter.getMethod("getFreeformBlackList");
            Method forceResizable = utils.getMethod("isForceResizeable");
            if (!supportsBlacklist(blacklist) || !supportsBoolean(forceResizable)) {
                log("blacklist hook skipped; Xiaomi method contract is unknown");
                return;
            }
            XposedBridge.hookMethod(blacklist, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (isEnabled(enabled)) param.setResult(new ArrayList<>());
                }
            });
            XposedBridge.hookMethod(forceResizable, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (isEnabled(enabled)) param.setResult(Boolean.TRUE);
                }
            });
            log("opt-in freeform blacklist hooks installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            log("blacklist hook unavailable; Xiaomi behavior retained: "
                    + error.getClass().getSimpleName());
        }
    }

    private static void installCountLimit(ClassLoader loader, BooleanSupplier enabled) {
        if (enabled == null) return;
        try {
            Class<?> strategy = Class.forName(STACK_STRATEGY, false, loader);
            Class<?> activityStack = Class.forName(ACTIVITY_STACK, false, loader);
            Method maxCount = strategy.getDeclaredMethod(
                    "getMaxMiuiFreeFormStackCount", String.class, activityStack);
            if (!supportsInt(maxCount, activityStack)) {
                log("count hook skipped; Xiaomi method contract is unknown");
                return;
            }
            XposedBridge.hookMethod(maxCount, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (isEnabled(enabled)) {
                        param.setResult(REFERENCE_MAX_FREEFORM_STACK_COUNT);
                    }
                }
            });
            log("opt-in freeform stack-count hook installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            log("count hook unavailable; Xiaomi behavior retained: "
                    + error.getClass().getSimpleName());
        }
    }

    static boolean supportsBlacklist(Method method) {
        return method != null && method.getParameterCount() == 0
                && method.getReturnType().isAssignableFrom(ArrayList.class)
                && Collection.class.isAssignableFrom(method.getReturnType());
    }

    static boolean supportsBoolean(Method method) {
        return method != null && method.getParameterCount() == 0
                && (method.getReturnType() == boolean.class
                || method.getReturnType() == Boolean.class);
    }

    static boolean supportsInt(Method method, Class<?> activityStack) {
        return method != null && activityStack != null && method.getReturnType() == int.class
                && method.getParameterCount() == 2
                && method.getParameterTypes()[0] == String.class
                && method.getParameterTypes()[1] == activityStack;
    }

    static boolean isEnabled(BooleanSupplier enabled) {
        try {
            return enabled.getAsBoolean();
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
