package com.leaf.hyperdragshare.codex;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import com.leaf.hyperdragshare.compat.xposed.XC_MethodHook;
import com.leaf.hyperdragshare.compat.xposed.XposedHelpers;
import java.util.List;

/** Runs native extraction only during MMGG's explicit point query. */
final class ContentCatcherNativeTextHooks {
    private static final String TAG = "DragShare/NativeText";
    private static final ThreadLocal<Bundle> request = new ThreadLocal<>();

    private ContentCatcherNativeTextHooks() {}

    static void install(Object interceptor) {
        try {
            ClassLoader loader = interceptor.getClass().getClassLoader();
            Object fetcher = XposedHelpers.getObjectField(interceptor, "r");
            if (fetcher == null) throw new IllegalStateException("Active fetcher missing");
            Class<?> sanitizer = XposedHelpers.findClass("z.d", loader);
            // ContentCatcher 5.1.5 DEX: z.f is the UI-thread raw-View export Runnable;
            // c is its request, whose d field is the original request Bundle.
            XposedHelpers.findAndHookMethod("z.f", loader, "run", exportObserver());
            ContentCatcherMediaHooks.install(loader);
            XposedHelpers.findAndHookMethod("z.l", loader, "c", View.class, Matrix.class,
                    Rect.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable() || request.get() == null) return;
                            try {
                                supplement(param.thisObject, (View) param.args[0], fetcher,
                                        request.get(), sanitizer);
                                ContentCatcherMediaHooks.collect(param.thisObject, (View) param.args[0],
                                        fetcher, request.get());
                            } catch (RuntimeException | LinkageError error) {
                                // Leave the original export intact if this optional extraction fails.
                                DragShareLog.w(TAG, "custom text unavailable error="
                                        + error.getClass().getSimpleName());
                            }
                        }
                    });
            DragShareLog.i(TAG, "custom text adapter installed version=" + BuildConfig.VERSION_CODE);
        } catch (XposedHelpers.ClassNotFoundError | RuntimeException | LinkageError error) {
            DragShareLog.w(TAG, "custom text adapter unavailable error="
                    + error.getClass().getSimpleName());
        }
    }

    static XC_MethodHook exportObserver() {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                request.remove();
                try {
                    Object task = XposedHelpers.getObjectField(param.thisObject, "c");
                    Bundle args = (Bundle) XposedHelpers.getObjectField(task, "d");
                    if (isCaptureRequest(args)) request.set(args);
                } catch (RuntimeException error) {
                    DragShareLog.w(TAG, "export request unavailable error="
                            + error.getClass().getSimpleName());
                }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                request.remove();
            }
        };
    }

    static boolean isCaptureRequest(Bundle args) {
        return args != null && (args.getBoolean(SystemUiAgent.CAPTURE_TEXT)
                || args.getBoolean(SystemUiAgent.CAPTURE_MEDIA))
                && args.getInt("uiAgentType", -1) == 0 && args.getInt("catch_way", -1) == 1
                && Float.isFinite(args.getFloat(SystemUiAgent.TOUCH_X, Float.NaN))
                && Float.isFinite(args.getFloat(SystemUiAgent.TOUCH_Y, Float.NaN));
    }

    static boolean supplement(Object record, View view, Object fetcher, Bundle args,
            Class<?> sanitizer) {
        if (!isCaptureRequest(args) || !args.getBoolean(SystemUiAgent.CAPTURE_TEXT) || view instanceof TextView
                || !"android.widget.TextView".contentEquals(view.getAccessibilityClassName())) return false;
        List<?> names = (List<?>) XposedHelpers.getObjectField(record, "b");
        List<?> values = (List<?>) XposedHelpers.getObjectField(record, "c");
        if (names.contains("text")) return false;
        int password = names.indexOf("password");
        if (password >= 0 && "true".equals(values.get(password))) return false;
        int boundsIndex = names.indexOf("bounds");
        if (boundsIndex < 0) return false;
        Rect bounds = SystemUiAgent.parseBounds((String) values.get(boundsIndex));
        if (bounds == null || !bounds.contains((int) args.getFloat(SystemUiAgent.TOUCH_X),
                (int) args.getFloat(SystemUiAgent.TOUCH_Y))) return false;
        AccessibilityNodeInfo info = view.createAccessibilityNodeInfo();
        try {
            if (info != null && info.isPassword()) return false;
        } finally {
            if (info != null) info.recycle();
        }
        Bundle content = (Bundle) XposedHelpers.callMethod(fetcher, "fetchViewContent", view, 1, false);
        if (content == null || content.getInt("catchType") != 1) return false;
        CharSequence text = content.getCharSequence("viewContent");
        if (text == null || text.length() == 0) return false;
        String clean = (String) XposedHelpers.callStaticMethod(sanitizer, "d", text);
        XposedHelpers.callMethod(record, "a", "text", clean);
        DragShareLog.i(TAG, "custom text filled view=" + view.getClass().getName()
                + " textLen=" + text.length());
        return true;
    }
}
