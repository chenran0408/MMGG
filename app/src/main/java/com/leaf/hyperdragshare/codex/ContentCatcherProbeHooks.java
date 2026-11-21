package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.leaf.hyperdragshare.compat.xposed.XC_MethodHook;
import com.leaf.hyperdragshare.compat.xposed.XposedBridge;
import com.leaf.hyperdragshare.compat.xposed.XposedHelpers;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Read-only ContentCatcher probe used for host-to-host diagnosis. */
final class ContentCatcherProbeHooks {
    private static final String TAG = "DragShare/ContentProbe";
    private static final String FETCHER_CLASS = "com.miui.catcherpatch.ViewContentFetcher";
    private static final String INPUT_LISTENER_CLASS = "u.a";
    private static final String VIEW_BITMAP = "viewBitmap";
    private static final String IMAGE_URL = "imgUrl";
    private static final AtomicBoolean runtimeHooked = new AtomicBoolean();

    private ContentCatcherProbeHooks() {}

    static void install(ClassLoader hostLoader, String packageName, String processName,
            Consumer<Object> onActiveInterceptor) {
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        String actualProcess = Application.getProcessName();
                        if (packageName.equals(actualProcess)) {
                            observeInterceptorAssignment(hostLoader, packageName, actualProcess,
                                    onActiveInterceptor);
                        }
                    }
                });
    }

    private static void observeInterceptorAssignment(ClassLoader hostLoader,
            String packageName, String processName, Consumer<Object> onActiveInterceptor) {
        try {
            // Observe the system's real assignment instead of loading another package context.
            // The object passed here is exactly what InterceptorProxy will dispatch events to.
            Class<?> proxy = XposedHelpers.findClass("miui.contentcatcher.InterceptorProxy", hostLoader);
            XposedHelpers.findAndHookMethod(proxy, "setInterceptor",
                    "miui.contentcatcher.IInterceptor", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            Object interceptor = param.args[0];
                            if (interceptor != null && runtimeHooked.compareAndSet(false, true)) {
                                installActiveInterceptor(interceptor, packageName, processName);
                                onActiveInterceptor.accept(interceptor);
                            }
                        }
                    });
            DragShareLog.i(TAG, "assignment probe installed package=" + packageName
                    + " process=" + processName + " version=" + BuildConfig.VERSION_CODE);
        } catch (Throwable error) {
            DragShareLog.w(TAG, "assignment probe unavailable package=" + packageName
                    + " process=" + processName, error);
        }
    }

    private static void installActiveInterceptor(Object instance,
            String packageName, String processName) {
        try {
            Class<?> interceptor = instance.getClass();
            ClassLoader catcherLoader = interceptor.getClassLoader();
            Class<?> inputListener = Class.forName(INPUT_LISTENER_CLASS, false, catcherLoader);
            XposedHelpers.findAndHookMethod(inputListener, "c", MotionEvent.class, View.class,
                    inputObserver(packageName, processName));
            XposedHelpers.findAndHookMethod(interceptor, "notifyActivityResume",
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            recordInterceptor(param.thisObject, packageName, processName);
                        }
                    });
            installFetcherHooks(instance, packageName, processName);
            DragShareLog.i(TAG, "runtime probe installed package=" + packageName
                    + " process=" + processName + " class=" + interceptor.getName()
                    + " loader=" + catcherLoader.getClass().getName()
                    + '@' + Integer.toHexString(System.identityHashCode(catcherLoader)));
        } catch (Throwable error) {
            DragShareLog.w(TAG, "runtime probe unavailable package=" + packageName
                    + " process=" + processName + " error="
                    + error.getClass().getSimpleName(), error);
        }
    }

    private static void installFetcherHooks(Object interceptor, String packageName,
            String processName) {
        try {
            // DEX names for ContentCatcher 5.1.5; f81r is only a JADX display alias.
            Object fetcher = XposedHelpers.getObjectField(interceptor, "r");
            if (fetcher == null) {
                DragShareLog.i(TAG, "fetcher unavailable package=" + packageName
                        + " process=" + processName);
                return;
            }
            Class<?> fetcherClass = fetcher.getClass();
            XposedHelpers.findAndHookMethod(fetcherClass, "fetchViewContent", View.class,
                    int.class, boolean.class, fetcherObserver(packageName, processName, "view"));
            XposedHelpers.findAndHookMethod(fetcherClass, "fetchWebViewBitmap", View.class,
                    int.class, fetcherObserver(packageName, processName, "webview"));
            for (String method : new String[]{"fetchTargetViewContent", "fetchWechatDefaultContent",
                    "fetchImageViewContent", "fetchViewBitmap", "fetchNativeViewContent"}) {
                XposedBridge.hookAllMethods(fetcherClass, method,
                        fetcherObserver(packageName, processName, method));
            }
            Object owner = XposedHelpers.getObjectField(fetcher, "mPackageName");
            Object mapping = XposedHelpers.getObjectField(fetcher, "mViewClassReflectionMap");
            DragShareLog.i(TAG, "active fetcher package=" + packageName + " process=" + processName
                    + " owner=" + String.valueOf(owner) + " class=" + fetcherClass.getName()
                    + " loader=" + fetcherClass.getClassLoader().getClass().getName() + '@'
                    + Integer.toHexString(System.identityHashCode(fetcherClass.getClassLoader()))
                    + " expectedClass=" + FETCHER_CLASS.equals(fetcherClass.getName())
                    + " mappingCount=" + (mapping instanceof Map ? ((Map<?, ?>) mapping).size() : -1));
        } catch (Throwable error) {
            DragShareLog.w(TAG, "active fetcher hook failed package=" + packageName
                    + " process=" + processName + " error="
                    + error.getClass().getSimpleName(), error);
        }
    }

    private static void recordInterceptor(Object interceptor, String packageName,
            String processName) {
        try {
            Object listener = XposedHelpers.getObjectField(interceptor, "d");
            Object activity = XposedHelpers.getObjectField(interceptor, "a");
            DragShareLog.i(TAG, "resumed package=" + packageName + " process=" + processName
                    + " activity=" + (activity == null ? "none" : activity.getClass().getName())
                    + " catchFlags=" + XposedHelpers.getIntField(interceptor, "q")
                    + " listener=" + (listener == null ? "none" : listener.getClass().getName())
                    + " mode=" + (listener == null ? -1 : XposedHelpers.getIntField(listener, "f")));
        } catch (Throwable error) {
            DragShareLog.w(TAG, "interceptor probe failed package=" + packageName
                    + " process=" + processName, error);
        }
    }

    private static XC_MethodHook inputObserver(String packageName, String processName) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length < 2
                        || !(param.args[0] instanceof MotionEvent)
                        || !(param.args[1] instanceof View)) return;
                MotionEvent event = (MotionEvent) param.args[0];
                int action = event.getActionMasked();
                if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN
                        && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) return;
                try {
                    Object activity = XposedHelpers.getObjectField(param.thisObject, "o");
                    DragShareLog.i(TAG, "input package=" + packageName + " process=" + processName
                            + " activity=" + (activity instanceof Activity
                            ? activity.getClass().getName() : "none")
                            + " mode=" + XposedHelpers.getIntField(param.thisObject, "f")
                            + " fired=" + XposedHelpers.getBooleanField(param.thisObject, "i")
                            + " accepted=" + XposedHelpers.getBooleanField(param.thisObject, "j")
                            + " action=" + action + " pointers=" + event.getPointerCount()
                            + " downTime=" + event.getDownTime()
                            + " elapsedMs=" + (event.getEventTime() - event.getDownTime())
                            + " view=" + ((View) param.args[1]).getClass().getName());
                } catch (Throwable error) {
                    DragShareLog.w(TAG, "input probe failed package=" + packageName
                            + " process=" + processName, error);
                }
            }
        };
    }

    private static XC_MethodHook fetcherObserver(String packageName, String processName,
            String path) {
        return new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.args.length == 0
                        || !(param.args[0] instanceof View)) return;
                View view = (View) param.args[0];
                if (!(param.getResult() instanceof Bundle)) {
                    DragShareLog.i(TAG, "empty candidate package=" + packageName
                            + " process=" + processName + " path=" + path
                            + " view=" + view.getClass().getName()
                            + " error=" + (param.hasThrowable()
                            ? param.getThrowable().getClass().getName() : "none"));
                    return;
                }
                Bundle bundle = (Bundle) param.getResult();
                Bitmap bitmap = bundle.getParcelable(VIEW_BITMAP);
                DragShareLog.i(TAG, "candidate package=" + packageName + " process=" + processName
                        + " path=" + path + " view=" + view.getClass().getName()
                        + "@" + view.getWidth() + "x" + view.getHeight()
                        + " catchType=" + bundle.getInt("catchType", 0)
                        + " bitmap=" + bitmapShape(bitmap)
                        + " viewContent=" + textShape(bundle.getString("viewContent"))
                        + " drawable=" + drawableShape(view)
                        + " viewTag=" + typeShape(view.getTag())
                        + " clipOutline=" + view.getClipToOutline()
                        + " mapping=" + reflectionMapping(param.thisObject, view)
                        + " imgUrl=" + referenceShape(bundle.getString(IMAGE_URL))
                        + " keys=" + bundle.keySet());
            }
        };
    }

    private static String drawableShape(View view) {
        if (!(view instanceof ImageView)) return "none";
        Drawable drawable = ((ImageView) view).getDrawable();
        if (drawable == null) return "null";
        return (drawable instanceof BitmapDrawable
                ? "BitmapDrawable/" + bitmapShape(((BitmapDrawable) drawable).getBitmap())
                : drawable.getClass().getName()) + "/intrinsic="
                + drawable.getIntrinsicWidth() + 'x' + drawable.getIntrinsicHeight()
                + "/current=" + typeShape(drawable.getCurrent());
    }

    private static String reflectionMapping(Object fetcher, View view) {
        try {
            Object mapping = XposedHelpers.getObjectField(fetcher, "mViewClassReflectionMap");
            if (!(mapping instanceof Map)) return "none";
            Object value = ((Map<?, ?>) mapping).get(view.getClass().getName());
            return value == null ? "none" : "present/len=" + String.valueOf(value).length();
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }

    private static String bitmapShape(Bitmap bitmap) {
        if (bitmap == null) return "none";
        return bitmap.isRecycled() ? "recycled" : bitmap.getWidth() + "x" + bitmap.getHeight();
    }

    private static String textShape(String value) {
        return value == null ? "none" : "present/len=" + value.length();
    }

    private static String typeShape(Object value) {
        return value == null ? "none" : value.getClass().getName();
    }

    private static String referenceShape(String value) {
        if (value == null || value.isEmpty()) return "none";
        try {
            android.net.Uri uri = android.net.Uri.parse(value.startsWith("//")
                    ? "http:" + value : value);
            String scheme = uri.getScheme();
            return "present/" + (scheme == null ? "none" : scheme);
        } catch (RuntimeException error) {
            return "invalid";
        }
    }
}
