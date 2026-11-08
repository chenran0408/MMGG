package com.leaf.hyperdragshare.gestures.xposed;

import android.os.Handler;
import android.os.Looper;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import org.robolectric.shadow.api.Shadow;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@LooperMode(LooperMode.Mode.PAUSED)
public class MoreAppsOverlayControllerTest {
    @Test public void shellThreadOpenAndCloseUseTheCatalogCallbackLooper() throws Exception {
        Handler ui = new Handler(Looper.getMainLooper());
        MoreAppsOverlayController controller = new MoreAppsOverlayController(
                RuntimeEnvironment.getApplication(), ui);
        try {
            runOnShellThread(() -> controller.show(target -> { }, true));
            assertFalse("View creation must wait for its UI looper", controller.isVisible());
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(controller.isVisible());
            View root = (View) field(controller, "attached");
            assertNotNull(root.getHandler());
            assertEquals(ui.getLooper(), root.getHandler().getLooper());

            Button[] tabs = (Button[]) field(controller, "tabButtons");
            assertEquals(2, tabs.length);
            assertEquals("全部应用", tabs[0].getText().toString());
            assertEquals("快捷方式", tabs[1].getText().toString());
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) field(controller, "attachedParams");
            assertEquals("Search must focus on the first touch", 0,
                    params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

            runOnShellThread(controller::removeNow);
            assertTrue("Removal must use the same UI looper", controller.isVisible());
            ShadowLooper.shadowMainLooper().idle();
            assertFalse(controller.isVisible());

            controller.show(target -> { }, false);
            assertTrue(controller.isVisible());
            assertEquals(View.VISIBLE, ((View) field(controller, "basicList")).getVisibility());
            assertEquals(View.GONE, ((View) field(controller, "indexBar")).getVisibility());
        } finally {
            controller.removeNow();
            ((java.util.concurrent.ExecutorService) field(controller, "loader")).shutdownNow();
        }
    }

    @Test
    @Config(shadows = {CatalogApps.class, CatalogActivity.class})
    public void appMetadataIsPublishedBeforeShortcutQueryAndIconLoading() throws Exception {
        MoreAppsOverlayController controller = new MoreAppsOverlayController(
                RuntimeEnvironment.getApplication(), new Handler(Looper.getMainLooper()));
        CatalogApps.appsPublished.set(false);
        try {
            java.lang.reflect.Method load = MoreAppsOverlayController.class.getDeclaredMethod(
                    "loadEntries", List.class, String.class, boolean.class, Consumer.class);
            load.setAccessible(true);
            Consumer<List<?>> publishApps = entries -> {
                assertEquals(1, entries.size());
                CatalogApps.appsPublished.set(true);
            };
            List<?> result = (List<?>) load.invoke(controller, null, null, false, publishApps);
            assertTrue(CatalogApps.appsPublished.get());
            assertEquals("Shortcuts are added after the initial apps snapshot", 2, result.size());
        } finally {
            ((java.util.concurrent.ExecutorService) field(controller, "loader")).shutdownNow();
        }
    }

    @Implements(LauncherApps.class)
    public static class CatalogApps {
        static final AtomicBoolean appsPublished = new AtomicBoolean();
        @Implementation protected List<LauncherActivityInfo> getActivityList(String pkg, UserHandle user) {
            return Collections.singletonList(Shadow.newInstanceOf(LauncherActivityInfo.class));
        }
        @Implementation protected List<ShortcutInfo> getShortcuts(LauncherApps.ShortcutQuery query, UserHandle user) {
            assertTrue("App names must not wait for the shortcut query", appsPublished.get());
            return Collections.singletonList(new ShortcutInfo.Builder(
                    RuntimeEnvironment.getApplication(), "orders")
                    .setShortLabel("订单详情")
                    .setIntent(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.test/orders")))
                    .build());
        }
    }

    @Implements(LauncherActivityInfo.class)
    public static class CatalogActivity {
        @Implementation protected ComponentName getComponentName() {
            return new ComponentName("example.app", "example.app.Main");
        }
        @Implementation protected CharSequence getLabel() { return "测试应用"; }
        @Implementation protected android.graphics.drawable.Drawable getBadgedIcon(int density) {
            throw new AssertionError("Metadata publication must not decode icons");
        }
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void runOnShellThread(Runnable action) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread shell = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "wmshell.main");
        shell.start();
        shell.join();
        assertNull("Shell input must not create or mutate panel views: " + failure.get(),
                failure.get());
    }
}
