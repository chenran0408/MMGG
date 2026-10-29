package com.leaf.hyperdragshare.gestures.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import com.leaf.hyperdragshare.gestures.config.ConfigContract;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public final class HoneycombOverlayControllerTest {
    @Test public void liveBlurRemainsAppliedWhileOverlayMoves() throws Exception {
        Context base = RuntimeEnvironment.getApplication();
        WindowManager delegate = base.getSystemService(WindowManager.class);
        TrackingWindowManager tracking = new TrackingWindowManager(delegate);
        Context context = new WindowManagerContext(base, tracking.proxy);
        HoneycombOverlayController controller = new HoneycombOverlayController(
                context, new Handler(Looper.getMainLooper()));
        Bundle configBundle = new Bundle();
        configBundle.putBoolean(ConfigContract.KEY_HONEYCOMB_LIVE_BLUR_ENABLED, true);
        configBundle.putInt(ConfigContract.KEY_HONEYCOMB_BACKGROUND_STYLE,
                ConfigContract.HONEYCOMB_BACKGROUND_BLACK);
        GestureConfig config = GestureConfig.from(configBundle);
        RuntimeTarget target = new RuntimeTarget("pkg", "shortcut", "", "Target",
                new ColorDrawable(Color.RED), 0);

        assertTrue(controller.show(Collections.singletonList(target),
                GestureGeometry.Corner.RIGHT, 100f, 100f, config,
                new HoneycombOverlayController.Listener() {
                    @Override public void onLaunch(RuntimeTarget ignored) { }
                    @Override public void onClosed() { }
                }));
        assertEquals(1, tracking.addedFlags.size());
        assertTrue((tracking.addedFlags.get(0)
                & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0);

        HoneycombOverlayView view = (HoneycombOverlayView) field(controller, "view");
        assertNotNull(view);
        view.onExternalMove(100f, 100f);
        view.onExternalMove(110f, 100f);

        assertTrue("moving the overlay must not issue a blur-disabling layout update",
                tracking.updatedFlags.isEmpty());
        assertTrue((tracking.addedFlags.get(0)
                & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0);
        HoneycombOverlayContainer container = (HoneycombOverlayContainer)
                field(controller, "windowView");
        controller.removeNow();
        assertEquals(1, tracking.removedViews);
        assertTrue((boolean) field(field(container, "backgroundView"), "released"));
    }

    @Test public void failedBlurAttachmentRetriesWithoutBlurAndClosesCleanly()
            throws Exception {
        Context base = RuntimeEnvironment.getApplication();
        WindowManager delegate = base.getSystemService(WindowManager.class);
        TrackingWindowManager tracking = new TrackingWindowManager(delegate, true);
        Context context = new WindowManagerContext(base, tracking.proxy);
        HoneycombOverlayController controller = new HoneycombOverlayController(
                context, new Handler(Looper.getMainLooper()));
        Bundle configBundle = new Bundle();
        configBundle.putBoolean(ConfigContract.KEY_HONEYCOMB_LIVE_BLUR_ENABLED, true);
        configBundle.putInt(ConfigContract.KEY_HONEYCOMB_BACKGROUND_STYLE,
                ConfigContract.HONEYCOMB_BACKGROUND_BLACK);
        GestureConfig config = GestureConfig.from(configBundle);
        RuntimeTarget target = new RuntimeTarget("pkg", "shortcut", "", "Target",
                new ColorDrawable(Color.RED), 0);

        assertTrue(controller.show(Collections.singletonList(target),
                GestureGeometry.Corner.RIGHT, 100f, 100f, config,
                new HoneycombOverlayController.Listener() {
                    @Override public void onLaunch(RuntimeTarget ignored) { }
                    @Override public void onClosed() { }
                }));
        assertEquals(2, tracking.addedFlags.size());
        assertTrue((tracking.addedFlags.get(0)
                & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0);
        assertEquals(0, tracking.addedFlags.get(1)
                & WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        HoneycombOverlayContainer container = (HoneycombOverlayContainer)
                field(controller, "windowView");
        controller.removeNow();
        assertEquals(1, tracking.removedViews);
        assertTrue((boolean) field(field(container, "backgroundView"), "released"));
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class WindowManagerContext extends ContextWrapper {
        private final WindowManager windowManager;

        WindowManagerContext(Context base, WindowManager windowManager) {
            super(base);
            this.windowManager = windowManager;
        }

        @Override public Object getSystemService(String name) {
            if (Context.WINDOW_SERVICE.equals(name)) return windowManager;
            return super.getSystemService(name);
        }
    }

    private static final class TrackingWindowManager implements InvocationHandler {
        final WindowManager proxy;
        final List<Integer> addedFlags = new ArrayList<>();
        final List<Integer> updatedFlags = new ArrayList<>();
        int removedViews;
        private final WindowManager delegate;
        private boolean failBlurOnFirstAdd;

        TrackingWindowManager(WindowManager delegate) {
            this(delegate, false);
        }

        TrackingWindowManager(WindowManager delegate, boolean failBlurOnFirstAdd) {
            this.delegate = delegate;
            this.failBlurOnFirstAdd = failBlurOnFirstAdd;
            proxy = (WindowManager) Proxy.newProxyInstance(
                    WindowManager.class.getClassLoader(),
                    new Class<?>[] {WindowManager.class}, this);
        }

        @Override public Object invoke(Object ignored, Method method, Object[] args)
                throws Throwable {
            String name = method.getName();
            if ("addView".equals(name) && args[1] instanceof WindowManager.LayoutParams) {
                int flags = ((WindowManager.LayoutParams) args[1]).flags;
                addedFlags.add(flags);
                if (failBlurOnFirstAdd
                        && (flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0) {
                    failBlurOnFirstAdd = false;
                    throw new IllegalArgumentException("blur attachment rejected by test");
                }
                return null;
            } else if ("updateViewLayout".equals(name)
                    && args[1] instanceof WindowManager.LayoutParams) {
                updatedFlags.add(((WindowManager.LayoutParams) args[1]).flags);
                return null;
            } else if ("removeViewImmediate".equals(name)) {
                removedViews++;
                return null;
            } else if (method.getDeclaringClass() == Object.class) {
                if ("toString".equals(name)) return "TrackingWindowManager";
                if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                if ("equals".equals(name)) return proxy == args[0];
            }
            return method.invoke(delegate, args);
        }
    }
}
