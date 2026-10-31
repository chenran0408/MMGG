package com.leaf.hyperdragshare.gestures.xposed;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Converts only Xiaomi's existing notification freeform action to freeform launch options. */
public final class NotificationFreeformHook {
    private static final String TAG = "DragShare/NotificationFreeform";
    private static final String FREEFORM_OPEN_RESOURCE = "drag_shadow_freeform_open";
    private static final String MIUI_MULTI_WINDOW_UTILS = "android.util.MiuiMultiWindowUtils";
    private static boolean installed;

    private NotificationFreeformHook() {}

    /** Call from SystemUI only. The supplier is owned by the module's existing config path. */
    public static synchronized void install(ClassLoader classLoader, BooleanSupplier enabled) {
        if (installed || classLoader == null || enabled == null) return;
        try {
            Method method = RemoteViews.class.getMethod(
                    "startPendingIntent", View.class, PendingIntent.class, Pair.class);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!isEnabled(enabled)) return;
                    Object rewritten = rewriteArguments(
                            (View) param.args[0], (PendingIntent) param.args[1],
                            (Pair<?, ?>) param.args[2], classLoader);
                    if (rewritten != null) param.args[2] = rewritten;
                }
            });
            installed = true;
            log("RemoteViews startPendingIntent hook installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            log("hook unavailable; original notification behavior retained: " + error);
        }
    }

    private static Object rewriteArguments(View view, PendingIntent pendingIntent,
            Pair<?, ?> originalPair, ClassLoader classLoader) {
        if (!(view instanceof TextView) || pendingIntent == null) return null;
        TextView textView = (TextView) view;
        Context context = textView.getContext();
        if (context == null || !isFreeformAction(textView, context)) return null;
        try {
            Intent launchIntent = pendingIntentIntent(pendingIntent);
            ComponentName component = launchIntent == null ? null : launchIntent.getComponent();
            if (component == null && launchIntent != null) {
                component = launchIntent.resolveActivity(context.getPackageManager());
            }
            Object xiaomiOptions = getXiaomiActivityOptions(classLoader, context, component);
            return mergeOptionsPair(originalPair, xiaomiOptions);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            log("freeform options unavailable; original notification behavior retained: "
                    + error.getClass().getSimpleName());
            return null;
        }
    }

    static boolean isFreeformAction(TextView view, Context context) {
        if (view == null || context == null || view.getId() == View.NO_ID) return false;
        try {
            Resources resources = view.getResources();
            if (!NotificationFreeformActionPolicy.ACTION_VIEW_ID.equals(
                    resources.getResourceEntryName(view.getId()))) return false;
            int resourceId = resources.getIdentifier(
                    FREEFORM_OPEN_RESOURCE, "string", null);
            return resourceId != 0 && NotificationFreeformActionPolicy.matches(
                    true, resources.getResourceEntryName(view.getId()), view.getText(),
                    resources.getText(resourceId));
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static Intent pendingIntentIntent(PendingIntent pendingIntent)
            throws ReflectiveOperationException {
        Method method = pendingIntent.getClass().getMethod("getIntent");
        return (Intent) method.invoke(pendingIntent);
    }

    private static Object getXiaomiActivityOptions(ClassLoader classLoader, Context context,
            ComponentName component) throws ReflectiveOperationException {
        Class<?> utils = Class.forName(MIUI_MULTI_WINDOW_UTILS, false, classLoader);
        Method method = utils.getMethod("getActivityOptions", Context.class, ComponentName.class);
        return method.invoke(null, context, component);
    }

    static Pair<?, ?> mergeOptionsPair(Pair<?, ?> originalPair, Object xiaomiOptions) {
        if (!(xiaomiOptions instanceof ActivityOptions)) return originalPair;
        try {
            ActivityOptions merged = (ActivityOptions) xiaomiOptions;
            Object originalOptions = originalPair == null ? null : originalPair.second;
            if (originalOptions != null) {
                if (!(originalOptions instanceof ActivityOptions)) return originalPair;
                Bundle bundle = ((ActivityOptions) originalOptions).toBundle();
                bundle.putAll(merged.toBundle());
                Method fromBundle = ActivityOptions.class.getMethod("fromBundle", Bundle.class);
                merged = (ActivityOptions) fromBundle.invoke(null, bundle);
            }
            Object fillIntent = originalPair == null ? null : originalPair.first;
            return new Pair<>(fillIntent, merged);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return originalPair;
        }
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
