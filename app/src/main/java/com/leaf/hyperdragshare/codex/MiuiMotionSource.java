package com.leaf.hyperdragshare.codex;

import android.os.Handler;
import android.os.HandlerThread;
import android.view.MotionEvent;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.leaf.hyperdragshare.compat.xposed.XposedHelpers;

/**
 * Taplus-side fallback input source. Some apps, especially custom rendered
 * chat surfaces, deliver motion through MIUI's input manager before the root
 * evdev reader becomes ready.
 */
final class MiuiMotionSource {
    interface Listener {
        void onMotionEvent(MotionEvent event);
    }

    private static final String TAG = "DragShare/MiuiInput";
    private static final String INPUT_MANAGER_CLASS =
            "miui.hardware.input.MiuiInputManager";
    private static final String MOTION_LISTENER_CLASS =
            "miui.hardware.input.MiuiInputManager$MiuiMotionEventListener";
    private static final String GET_INSTANCE = "getInstance";
    private static final String REGISTER = "registerMiuiMotionEventListener";
    private static final String UNREGISTER = "unregisterMiuiMotionEventListener";
    private static final String ON_MOTION_EVENT = "onMotionEvent";

    private final ClassLoader classLoader;
    private final Listener listener;
    private HandlerThread thread;
    private Object manager;
    private Object motionListener;
    private boolean firstEventLogged;

    MiuiMotionSource(ClassLoader classLoader, Listener listener) {
        this.classLoader = classLoader;
        this.listener = listener;
    }

    synchronized void start() {
        if (motionListener != null) return;
        try {
            Class<?> managerClass = XposedHelpers.findClassIfExists(
                    INPUT_MANAGER_CLASS, classLoader);
            Class<?> listenerClass = XposedHelpers.findClassIfExists(
                    MOTION_LISTENER_CLASS, classLoader);
            if (managerClass == null || listenerClass == null) {
                DragShareLog.i(TAG, "MIUI motion fallback classes unavailable");
                return;
            }
            Object inputManager = XposedHelpers.callStaticMethod(managerClass, GET_INSTANCE);
            if (inputManager == null) return;

            HandlerThread inputThread = new HandlerThread("drag-share-input");
            inputThread.start();
            Handler handler = new Handler(inputThread.getLooper());
            InvocationHandler invocationHandler = (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return handleObjectMethod(proxy, method, args);
                }
                if (ON_MOTION_EVENT.equals(method.getName())
                        && args != null && args.length == 1
                        && args[0] instanceof MotionEvent) {
                    MotionEvent source = (MotionEvent) args[0];
                    if (!firstEventLogged) {
                        firstEventLogged = true;
                        DragShareLog.i(TAG, "first event action="
                                + MotionEvent.actionToString(source.getActionMasked())
                                + " point=" + Math.round(source.getRawX()) + ","
                                + Math.round(source.getRawY()));
                    }
                    listener.onMotionEvent(MotionEvent.obtain(source));
                }
                return null;
            };
            Object proxy = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    invocationHandler);
            XposedHelpers.callMethod(inputManager, REGISTER, proxy, handler);
            thread = inputThread;
            manager = inputManager;
            motionListener = proxy;
            DragShareLog.i(TAG, "fallback listener registered");
        } catch (Throwable error) {
            DragShareLog.w(TAG, "fallback listener unavailable", error);
            stop();
        }
    }

    synchronized void stop() {
        Object inputManager = manager;
        Object listenerObject = motionListener;
        HandlerThread inputThread = thread;
        manager = null;
        motionListener = null;
        thread = null;
        if (inputManager != null && listenerObject != null) {
            try {
                XposedHelpers.callMethod(inputManager, UNREGISTER, listenerObject);
            } catch (Throwable error) {
                DragShareLog.w(TAG, "unable to unregister fallback listener", error);
            }
        }
        if (inputThread != null) inputThread.quitSafely();
    }

    private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals":
                return args != null && args.length == 1 && proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "DragShareMiuiMotionListener";
            default:
                return null;
        }
    }
}
