package com.leaf.hyperdragshare.gestures.xposed;

import android.os.Handler;
import android.os.Looper;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.net.Uri;
import android.graphics.Insets;
import android.view.View;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.leaf.hyperdragshare.gestures.config.ConfigContract;
import com.leaf.hyperdragshare.gestures.config.ConfigProvider;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
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
    @Test public void compactKeyboardKeepsResultsAndBackRestoresTheSamePanel() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        var visibilityChanges = new java.util.concurrent.atomic.AtomicInteger();
        var launches = new java.util.concurrent.atomic.AtomicInteger();
        var controller = new MoreAppsOverlayController(context, new Handler(Looper.getMainLooper()),
                visibilityChanges::incrementAndGet);
        try {
            Field cache = controller.getClass().getDeclaredField("cachedEntries");
            cache.setAccessible(true);
            cache.set(controller, List.of(catalogEntry("Alpha", MoreAppsCatalog.COLOR_RED)));
            controller.show(target -> launches.incrementAndGet(), true);
            View root = (View) field(controller, "attached");
            View tabs = (View) field(controller, "tabs");
            View colors = (View) field(controller, "colorScroll");
            View index = (View) field(controller, "indexBar");
            View grid = (View) field(controller, "grid");
            ((EditText) field(controller, "search")).setText("Alpha");
            float density = context.getResources().getDisplayMetrics().density;
            int width = View.MeasureSpec.makeMeasureSpec(Math.round(360 * density), View.MeasureSpec.EXACTLY);
            int height = View.MeasureSpec.makeMeasureSpec(Math.round(460 * density), View.MeasureSpec.EXACTLY);
            WindowInsets keyboard = new WindowInsets.Builder()
                    .setVisible(WindowInsets.Type.ime(), true)
                    .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, Math.round(260 * density)))
                    .build();
            root.dispatchApplyWindowInsets(keyboard);
            root.measure(width, height);
            assertEquals(View.GONE, tabs.getVisibility());
            assertEquals(View.GONE, colors.getVisibility());
            assertEquals(View.GONE, index.getVisibility());
            assertTrue("An app row must still fit above the keyboard",
                    grid.getMeasuredHeight() >= Math.round(98 * density));
            EditText search = (EditText) field(controller, "search");
            search.requestFocus();
            int wide = View.MeasureSpec.makeMeasureSpec(Math.round(640 * density), View.MeasureSpec.EXACTLY);
            int shortDisplay = View.MeasureSpec.makeMeasureSpec(Math.round(420 * density), View.MeasureSpec.EXACTLY);
            root.measure(wide, shortDisplay);
            var panel = (android.widget.LinearLayout) ((ViewGroup) root).getChildAt(0);
            assertEquals(android.widget.LinearLayout.HORIZONTAL, panel.getOrientation());
            assertTrue(grid.getMeasuredHeight() >= Math.round(98 * density));
            assertTrue(search.hasFocus());
            root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
            assertTrue(controller.isVisible());

            root.dispatchApplyWindowInsets(new WindowInsets.Builder().build());
            root.measure(width, height);
            assertEquals(android.widget.LinearLayout.VERTICAL, panel.getOrientation());
            assertEquals(View.VISIBLE, tabs.getVisibility());
            assertEquals(View.VISIBLE, colors.getVisibility());
            assertEquals(View.VISIBLE, index.getVisibility());
            assertEquals("Alpha", ((EditText) field(controller, "search")).getText().toString());
            ArrayList<View> closeButtons = new ArrayList<>();
            root.findViewsWithText(closeButtons, "关闭更多应用", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
            assertEquals(1, closeButtons.size());
            closeButtons.get(0).performClick();
            assertFalse(controller.isVisible());
            assertEquals(2, visibilityChanges.get());
            assertEquals(0, launches.get());
        } finally {
            controller.removeNow();
            ((java.util.concurrent.ExecutorService) field(controller, "loader")).shutdownNow();
        }
    }

    @Test public void queryAndColorDoNotRemoveAnExplicitInitialFilter() throws Exception {
        var controller = new MoreAppsOverlayController(RuntimeEnvironment.getApplication(),
                new Handler(Looper.getMainLooper()), () -> { });
        try {
            var cache = controller.getClass().getDeclaredField("cachedEntries");
            cache.setAccessible(true);
            cache.set(controller, List.of(
                    catalogEntry("Alpha", MoreAppsCatalog.COLOR_RED),
                    catalogEntry("Beta", MoreAppsCatalog.COLOR_BLUE)));
            controller.show(target -> { }, true);
            ViewGroup index = (ViewGroup) field(controller, "indexBar");
            index.layout(0, 0, 48, 200);
            MotionEvent select = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 12, 25, 0);
            index.dispatchTouchEvent(select);
            select.recycle();
            assertEquals("A", field(controller, "selectedInitial"));

            EditText search = (EditText) field(controller, "search");
            search.setText("Beta");
            assertEquals(View.VISIBLE, ((View) field(controller, "empty")).getVisibility());
            assertEquals("A", field(controller, "selectedInitial"));
            assertTrue(index.getChildAt(0).isSelected());

            search.setText("");
            ViewGroup colors = (ViewGroup) field(controller, "colorBar");
            View blue = null;
            for (int i = 0; i < colors.getChildCount(); i++) {
                View chip = colors.getChildAt(i);
                if ("蓝色图标".contentEquals(chip.getContentDescription())) blue = chip;
            }
            assertNotNull(blue);
            blue.performClick();
            assertEquals(View.VISIBLE, ((View) field(controller, "empty")).getVisibility());
            assertEquals("A", field(controller, "selectedInitial"));
            ((View) field(controller, "clearFilters")).performClick();
            assertEquals("", field(controller, "selectedInitial"));
            assertEquals(View.GONE, ((View) field(controller, "empty")).getVisibility());
        } finally {
            controller.removeNow();
            ((java.util.concurrent.ExecutorService) field(controller, "loader")).shutdownNow();
        }
    }

    private static Object catalogEntry(String name, int color) throws Exception {
        String key = "example." + name.toLowerCase(java.util.Locale.ROOT);
        var component = new ComponentName(key, key + ".Main");
        var constructor = Class.forName(MoreAppsOverlayController.class.getName() + "$Entry")
                .getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(component.flattenToString(),
                new RuntimeTarget(component, name, null), false, name.substring(0, 1), color, null);
    }

    @Test public void emptyStateClearsFiltersAndBackClosesPanel() throws Exception {
        var controller = new MoreAppsOverlayController(RuntimeEnvironment.getApplication(),
                new Handler(Looper.getMainLooper()), () -> { });
        try {
            Field cache = controller.getClass().getDeclaredField("cachedEntries");
            cache.setAccessible(true);
            cache.set(controller, List.of());
            controller.show(target -> { }, true);
            TextView empty = (TextView) field(controller, "empty");
            assertEquals("没有匹配结果", empty.getText());
            EditText search = (EditText) field(controller, "search");
            ViewGroup colors = (ViewGroup) field(controller, "colorBar");
            View pink = colors.getChildAt(colors.getChildCount() - 1);
            pink.performClick();
            assertTrue(pink.isSelected());
            search.setText("No matching app");
            View clear = (View) field(controller, "clearFilters");
            assertEquals(View.VISIBLE, clear.getVisibility());
            clear.performClick();
            assertEquals("", search.getText().toString());
            assertTrue(colors.getChildAt(0).isSelected());
            assertFalse(pink.isSelected());
            assertEquals(View.GONE, clear.getVisibility());
            View root = (View) field(controller, "attached");
            assertTrue(root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));
            assertTrue(controller.isVisible());
            assertTrue(root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)));
            assertFalse(controller.isVisible());
        } finally {
            controller.removeNow();
            ((java.util.concurrent.ExecutorService) field(controller, "loader")).shutdownNow();
        }
    }
    @Test public void packageChangesDuringCatalogLoadAreKeptForTheNextPass() throws Exception {
        Handler ui = new Handler(Looper.getMainLooper());
        var context = RuntimeEnvironment.getApplication();
        var controller = new MoreAppsOverlayController(context, ui, () -> { });
        var loader = (java.util.concurrent.ExecutorService) field(controller, "loader");
        var unblock = new java.util.concurrent.CountDownLatch(1);
        loader.execute(() -> {
            try {
                unblock.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            controller.prepare();
            ShadowLooper.shadowMainLooper().idle();
            var receiver = (BroadcastReceiver) field(controller, "packageChanges");
            for (String pkg : List.of("example.first", "example.second", "example.first")) {
                receiver.onReceive(context,
                        new Intent(Intent.ACTION_PACKAGE_REPLACED, Uri.fromParts("package", pkg, null)));
            }
            ShadowLooper.shadowMainLooper().idle();
            assertEquals(java.util.Set.of("example.first", "example.second"),
                    field(controller, "changedPackages"));
        } finally {
            loader.shutdownNow();
            unblock.countDown();
        }
    }

    @Test public void shellThreadOpenAndCloseUseTheCatalogCallbackLooper() throws Exception {
        Handler ui = new Handler(Looper.getMainLooper());
        MoreAppsOverlayController controller = new MoreAppsOverlayController(
                RuntimeEnvironment.getApplication(), ui, () -> { });
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
                RuntimeEnvironment.getApplication(), new Handler(Looper.getMainLooper()), () -> { });
        CatalogApps.appsPublished.set(false);
        try {
            java.lang.reflect.Method load = MoreAppsOverlayController.class.getDeclaredMethod(
                    "loadEntries", List.class, java.util.Set.class, boolean.class, Consumer.class);
            load.setAccessible(true);
            Consumer<List<?>> publishApps = entries -> {
                assertEquals(1, entries.size());
                CatalogApps.appsPublished.set(true);
            };
            List<?> result = (List<?>) load.invoke(controller, null, java.util.Set.of(), false, publishApps);
            assertTrue(CatalogApps.appsPublished.get());
            assertEquals("Shortcuts are added after the initial apps snapshot", 2, result.size());
        } finally {
            ((java.util.concurrent.ExecutorService) field(controller, "loader")).shutdownNow();
        }
    }

    @Test
    @Config(shadows = {PackageRefreshApps.class, PackageRefreshActivity.class})
    public void refreshesOnlyChangedLauncherPackagesAndReusesAppsForShortcutUpdates()
            throws Exception {
        var controller = new MoreAppsOverlayController(RuntimeEnvironment.getApplication(),
                new Handler(Looper.getMainLooper()), () -> { });
        PackageRefreshApps.queries.clear();
        try {
            var load = MoreAppsOverlayController.class.getDeclaredMethod(
                    "loadEntries", List.class, Set.class, boolean.class, Consumer.class);
            load.setAccessible(true);
            Consumer<List<?>> publishApps = entries -> { };
            List<?> initial = (List<?>) load.invoke(controller, null, Set.of(), false, publishApps);
            assertEquals(List.of("Keep", "Old", "Removed"), appLabels(initial));
            assertEquals(Collections.singletonList(null), PackageRefreshApps.queries);

            PackageRefreshApps.queries.clear();
            List<?> updated = (List<?>) load.invoke(controller, initial,
                    Set.of("example.updated", "example.removed", "example.added"), false, publishApps);
            assertEquals(List.of("Added", "Keep", "Updated"), appLabels(updated));
            assertEquals(3, PackageRefreshApps.queries.size());
            assertTrue(PackageRefreshApps.queries.containsAll(
                    List.of("example.updated", "example.removed", "example.added")));

            PackageRefreshApps.queries.clear();
            List<?> fromShortcutChange = (List<?>) load.invoke(controller, updated, Set.of(), true, publishApps);
            assertEquals(List.of("Added", "Keep", "Updated"), appLabels(fromShortcutChange));
            assertTrue(PackageRefreshApps.queries.isEmpty());
        } finally {
            ((java.util.concurrent.ExecutorService) field(controller, "loader")).shutdownNow();
        }
    }

    private static List<String> appLabels(List<?> entries) throws Exception {
        List<String> labels = new ArrayList<>();
        for (Object entry : entries) {
            if (!(boolean) field(entry, "shortcut")) labels.add(((RuntimeTarget) field(entry, "target")).label);
        }
        return labels;
    }

    @Test
    @Config(shadows = {PackageRefreshApps.class, PackageRefreshActivity.class})
    public void reopensAfterDirectoryFailureAndCachesSuccessfulEmptyResult() throws Exception {
        var controller = new MoreAppsOverlayController(RuntimeEnvironment.getApplication(),
                new Handler(Looper.getMainLooper()), () -> { });
        var loader = (java.util.concurrent.ExecutorService) field(controller, "loader");
        PackageRefreshApps.queries.clear();
        try {
            PackageRefreshApps.failApps = true;
            controller.show(target -> { }, true);
            finishCatalogLoad(loader);
            assertEquals("应用列表读取失败，重新打开可重试", ((TextView) field(controller, "empty")).getText());
            assertEquals(1, PackageRefreshApps.queries.size());

            controller.removeNow();
            PackageRefreshApps.failApps = false;
            controller.show(target -> { }, true);
            finishCatalogLoad(loader);
            assertEquals(List.of("Keep", "Old", "Removed"), appLabels((List<?>) field(controller, "cachedEntries")));
            assertEquals(2, PackageRefreshApps.queries.size());

            PackageRefreshApps.failApps = true;
            var receiver = (BroadcastReceiver) field(controller, "packageChanges");
            receiver.onReceive(RuntimeEnvironment.getApplication(), new Intent(Intent.ACTION_PACKAGE_REPLACED,
                    Uri.fromParts("package", "example.updated", null)));
            ShadowLooper.shadowMainLooper().idle();
            finishCatalogLoad(loader);
            assertEquals(List.of("Keep", "Old", "Removed"), appLabels((List<?>) field(controller, "cachedEntries")));

            controller.removeNow();
            PackageRefreshApps.failApps = false;
            PackageRefreshApps.emptyApps = true;
            controller.show(target -> { }, true);
            finishCatalogLoad(loader);
            assertEquals("没有匹配结果", ((TextView) field(controller, "empty")).getText());
            int queriesAfterRecovery = PackageRefreshApps.queries.size();
            controller.removeNow();
            controller.show(target -> { }, true);
            finishCatalogLoad(loader);
            assertEquals(queriesAfterRecovery, PackageRefreshApps.queries.size());
        } finally {
            PackageRefreshApps.failApps = false;
            PackageRefreshApps.emptyApps = false;
            controller.removeNow();
            loader.shutdownNow();
        }
    }

    @Test
    @Config(shadows = {PackageRefreshApps.class, PackageRefreshActivity.class})
    public void oneFailedShortcutSourceDoesNotHideTheOtherOrDiscardItsNewRows() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        var controller = new MoreAppsOverlayController(context, new Handler(Looper.getMainLooper()), () -> { });
        var loader = (java.util.concurrent.ExecutorService) field(controller, "loader");
        var provider = new ConfigProvider();
        var info = new android.content.pm.ProviderInfo();
        info.authority = ConfigContract.AUTHORITY;
        provider.attachInfo(context, info);
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority, provider);
        var prefs = context.getSharedPreferences(ConfigContract.PREFS, android.content.Context.MODE_PRIVATE);
        try {
            prefs.edit().putString(ConfigContract.KEY_SHORTCUT_CATALOG,
                    "[{\"kind\":\"launcher\",\"package\":\"example.desktop\",\"id\":\"desk\","
                            + "\"label\":\"Desktop\",\"intent\":\"intent:#Intent;package=example.desktop;end\"}]").commit();
            PackageRefreshApps.failShortcuts = true;
            controller.show(target -> { }, true);
            finishCatalogLoad(loader);
            ((Button[]) field(controller, "tabButtons"))[1].performClick();
            var adapter = (android.widget.BaseAdapter) field(controller, "adapter");
            assertEquals(1, adapter.getCount());
            assertEquals("Desktop", ((RuntimeTarget) field(adapter.getItem(0), "target")).label);

            controller.removeNow();
            PackageRefreshApps.failShortcuts = false;
            PackageRefreshApps.systemShortcut = true;
            prefs.edit().putString(ConfigContract.KEY_SHORTCUT_CATALOG, "unreadable").commit();
            controller.show(target -> { }, true);
            finishCatalogLoad(loader);
            ((Button[]) field(controller, "tabButtons"))[1].performClick();
            adapter = (android.widget.BaseAdapter) field(controller, "adapter");
            assertEquals(2, adapter.getCount());
            assertEquals(Set.of("Desktop", "System"), Set.of(
                    ((RuntimeTarget) field(adapter.getItem(0), "target")).label,
                    ((RuntimeTarget) field(adapter.getItem(1), "target")).label));
        } finally {
            PackageRefreshApps.failShortcuts = false;
            PackageRefreshApps.systemShortcut = false;
            controller.removeNow();
            loader.shutdownNow();
        }
    }

    @Test @Config(shadows = {PackageRefreshApps.class, PackageRefreshActivity.class})
    public void componentEnabledBroadcastRemovesAndRestoresOnlyThatAppsEntry() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        var controller = new MoreAppsOverlayController(context, new Handler(Looper.getMainLooper()), () -> { });
        var loader = (ExecutorService) field(controller, "loader");
        try {
            controller.show(target -> { }, true);
            finishCatalogLoad(loader);
            var before = (List<?>) field(controller, "cachedEntries");
            Object kept = null;
            for (Object entry : before) {
                if (((RuntimeTarget) field(entry, "target")).packageName.equals("example.keep")) kept = entry;
            }
            assertNotNull(kept);
            Intent changed = new Intent(Intent.ACTION_PACKAGE_CHANGED, Uri.parse("package:example.updated"))
                    .putExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST, new String[]{"example.updated.Main"});
            for (boolean enabled : new boolean[]{false, true}) {
                PackageRefreshApps.componentEnabled = enabled;
                PackageRefreshApps.queries.clear();
                context.sendBroadcast(changed);
                ShadowLooper.shadowMainLooper().idle();
                finishCatalogLoad(loader);
                var entries = (List<?>) field(controller, "cachedEntries");
                assertEquals(List.of("example.updated"), PackageRefreshApps.queries);
                assertEquals(enabled, appLabels(entries).contains("Updated"));
                assertTrue(entries.contains(kept));
            }
        } finally {
            PackageRefreshApps.componentEnabled = true;
            controller.removeNow();
            loader.shutdownNow();
        }
    }

    private static void finishCatalogLoad(java.util.concurrent.ExecutorService loader) throws Exception {
        loader.submit(() -> { }).get(5, java.util.concurrent.TimeUnit.SECONDS);
        ShadowLooper.shadowMainLooper().idle();
    }

    @Test
    @Config(shadows = {PackageRefreshApps.class, PackageRefreshActivity.class})
    public void visibleAppIconAndLaunchDoNotWaitForShortcutService() throws Exception {
        var controller = new MoreAppsOverlayController(RuntimeEnvironment.getApplication(),
                new Handler(Looper.getMainLooper()), () -> { });
        var loader = (ExecutorService) field(controller, "loader");
        var iconLoader = (ExecutorService) field(controller, "iconLoader");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        PackageRefreshApps.systemShortcut = true;
        PackageRefreshApps.beforeShortcutsRead = () -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        };
        try {
            var launched = new AtomicReference<RuntimeTarget>();
            controller.show(launched::set, true);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            ShadowLooper.shadowMainLooper().idle();
            var grid = (ViewGroup) field(controller, "grid");
            var adapter = (android.widget.BaseAdapter) field(controller, "adapter");
            assertEquals(3, adapter.getCount());
            var row = (ViewGroup) adapter.getView(0, null, grid);
            ShadowLooper.shadowMainLooper().idle();
            finishCatalogLoad(iconLoader);
            adapter.getView(0, row, grid);
            assertNotNull(((android.widget.ImageView) row.getChildAt(0)).getDrawable());
            row.performClick();
            assertEquals("example.keep", launched.get().packageName);

            release.countDown();
            finishCatalogLoad(loader);
            controller.show(launched::set, true);
            ((Button[]) field(controller, "tabButtons"))[1].performClick();
            adapter = (android.widget.BaseAdapter) field(controller, "adapter");
            assertEquals("System", ((RuntimeTarget) field(adapter.getItem(0), "target")).label);
        } finally {
            release.countDown();
            PackageRefreshApps.systemShortcut = false;
            PackageRefreshApps.beforeShortcutsRead = () -> { };
            controller.removeNow();
            iconLoader.shutdownNow();
            loader.shutdownNow();
        }
    }

    @Test
    @Config(shadows = {PackageRefreshApps.class, PackageRefreshActivity.class})
    public void prewarmReadsOnlyNamesAndPanelReusesOnDemandIcons() throws Exception {
        var controller = new MoreAppsOverlayController(RuntimeEnvironment.getApplication(),
                new Handler(Looper.getMainLooper()), () -> { });
        var loader = (java.util.concurrent.ExecutorService) field(controller, "loader");
        var iconLoader = (ExecutorService) field(controller, "iconLoader");
        PackageRefreshApps.queries.clear();
        PackageRefreshApps.largeCatalog = true;
        PackageRefreshActivity.iconsRead.clear();
        try {
            ShadowLooper.shadowMainLooper().idle();
            loader.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue("Constructing SystemUI panel must not warm the directory", PackageRefreshApps.queries.isEmpty());
            controller.prepare();
            loader.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
            ShadowLooper.shadowMainLooper().idle();
            assertEquals(Collections.singletonList(null), PackageRefreshApps.queries);
            assertTrue("Warming names must not decode icons", PackageRefreshActivity.iconsRead.isEmpty());

            controller.show(target -> { }, true);
            var adapter = (android.widget.BaseAdapter) field(controller, "adapter");
            var grid = (ViewGroup) field(controller, "grid");
            String pkg = ((RuntimeTarget) field(adapter.getItem(0), "target")).packageName;
            adapter.getView(0, null, grid);
            for (int i = 0; i < 3; i++) {
                ShadowLooper.shadowMainLooper().idle();
                iconLoader.submit(() -> {}).get(5, TimeUnit.SECONDS);
            }
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(PackageRefreshActivity.iconsRead.contains(pkg));
            assertTrue(PackageRefreshActivity.iconsRead.size() < adapter.getCount());
            var item = (MoreAppsCatalog.Item) field(adapter.getItem(0), "searchItem");
            assertTrue(item.color != MoreAppsCatalog.COLOR_PENDING);
            ((EditText) field(controller, "search")).setText("App 0");
            ((ViewGroup) field(controller, "colorBar")).getChildAt(item.color + 1).performClick();
            assertEquals("A loaded icon should already be available for filtering", 1, adapter.getCount());

            controller.removeNow();
            controller.show(target -> { }, true);
            adapter = (android.widget.BaseAdapter) field(controller, "adapter");
            adapter.getView(0, null, (ViewGroup) field(controller, "grid"));
            ShadowLooper.shadowMainLooper().idle();
            iconLoader.submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertEquals(1, Collections.frequency(PackageRefreshActivity.iconsRead, pkg));
        } finally {
            PackageRefreshApps.largeCatalog = false;
            controller.removeNow();
            iconLoader.shutdownNow();
            loader.shutdownNow();
        }
    }

    @Implements(LauncherApps.class)
    public static class PackageRefreshApps {
        static final List<String> queries = new ArrayList<>();
        static boolean largeCatalog;
        static boolean componentEnabled = true;
        static boolean failApps;
        static boolean emptyApps;
        static boolean failShortcuts;
        static boolean systemShortcut;
        static Runnable beforeShortcutsRead = () -> { };

        @Implementation protected List<LauncherActivityInfo> getActivityList(String pkg, UserHandle user) {
            queries.add(pkg);
            if (failApps) throw new IllegalStateException("Directory temporarily unavailable");
            if (emptyApps) return List.of();
            if (pkg == null) {
                if (largeCatalog) {
                    List<LauncherActivityInfo> apps = new ArrayList<>();
                    for (int i = 0; i < 100; i++) apps.add(activity("example.app" + i, "App " + i));
                    return apps;
                }
                return List.of(activity("example.keep", "Keep"), activity("example.updated", "Old"),
                        activity("example.removed", "Removed"));
            }
            return switch (pkg) {
                case "example.updated" -> componentEnabled ? List.of(activity(pkg, "Updated")) : List.of();
                case "example.added" -> List.of(activity(pkg, "Added"));
                default -> List.of();
            };
        }

        @Implementation protected List<ShortcutInfo> getShortcuts(LauncherApps.ShortcutQuery query, UserHandle user) {
            beforeShortcutsRead.run();
            if (failShortcuts) throw new SecurityException("Shortcut host unavailable");
            if (systemShortcut) return List.of(new ShortcutInfo.Builder(RuntimeEnvironment.getApplication(), "sys")
                    .setShortLabel("System").setIntent(new Intent(Intent.ACTION_VIEW)).build());
            return List.of();
        }

        private static LauncherActivityInfo activity(String pkg, String label) {
            LauncherActivityInfo info = Shadow.newInstanceOf(LauncherActivityInfo.class);
            PackageRefreshActivity shadow = Shadow.extract(info);
            shadow.component = new ComponentName(pkg, pkg + ".Main");
            shadow.label = label;
            return info;
        }
    }

    @Implements(LauncherActivityInfo.class)
    public static class PackageRefreshActivity {
        static final java.util.concurrent.ConcurrentLinkedQueue<String> iconsRead =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        private ComponentName component;
        private String label;

        @Implementation protected ComponentName getComponentName() { return component; }
        @Implementation protected CharSequence getLabel() { return label; }
        @Implementation protected android.graphics.drawable.Drawable getBadgedIcon(int density) {
            iconsRead.add(component.getPackageName());
            return new android.graphics.drawable.ColorDrawable(0xffff0000);
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
