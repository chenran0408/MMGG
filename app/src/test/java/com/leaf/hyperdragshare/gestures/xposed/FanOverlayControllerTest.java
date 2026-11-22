package com.leaf.hyperdragshare.gestures.xposed;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class FanOverlayControllerTest {
    private final List<View> windows = new ArrayList<>();
    private final FanOverlayController controller = newController();

    @Test public void shellReleaseClosesOnMainBeforeLaunchingNextScreen() throws InterruptedException {
        show();
        List<Boolean> launched = new ArrayList<>();
        Thread shell = new Thread(() -> controller.removeNowThen(() -> {
            assertTrue(Looper.getMainLooper().isCurrentThread());
            assertTrue(windows.isEmpty());
            launched.add(true);
        }));
        shell.start();
        shell.join();
        assertEquals(1, windows.size());
        assertTrue(launched.isEmpty());

        ShadowLooper.shadowMainLooper().idle();
        assertEquals(List.of(true), launched);
        assertTrue(windows.isEmpty());
    }

    @Test public void cancellingDismissAnimationDoesNotLeaveTheWindowAttached() {
        show();
        controller.hide();
        windows.get(0).animate().cancel();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(1));
        assertTrue(windows.isEmpty());
    }

    @Test public void oldDismissCallbackLeavesReplacementWindowAttached() {
        show();
        controller.hide();
        show();
        View replacement = windows.get(0);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(1));
        assertEquals(List.of(replacement), windows);
    }

    private void show() {
        controller.show(List.of(), GestureGeometry.Corner.LEFT, 150f, 44f,
                true, true, true, 100, 100, 90, 100, false, 0, 6, 3, 1, false);
        assertEquals(1, windows.size());
    }

    private FanOverlayController newController() {
        WindowManager windowManager = (WindowManager) Proxy.newProxyInstance(
                WindowManager.class.getClassLoader(), new Class<?>[]{WindowManager.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "addView" -> windows.add((View) args[0]);
                        case "removeViewImmediate" -> {
                            assertTrue(Looper.getMainLooper().isCurrentThread());
                            assertTrue(windows.remove((View) args[0]));
                        }
                        default -> throw new AssertionError(method.getName());
                    }
                    return null;
                });
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public Object getSystemService(String name) {
                return WINDOW_SERVICE.equals(name) ? windowManager : super.getSystemService(name);
            }
        };
        return new FanOverlayController(context, new Handler(Looper.getMainLooper()));
    }
}
