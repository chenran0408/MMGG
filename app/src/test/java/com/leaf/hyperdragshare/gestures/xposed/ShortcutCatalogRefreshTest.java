package com.leaf.hyperdragshare.gestures.xposed;

import static org.junit.Assert.assertEquals;

import android.database.ContentObserver;

import com.leaf.hyperdragshare.gestures.config.ConfigContract;
import com.leaf.hyperdragshare.gestures.config.ConfigStore;
import com.leaf.hyperdragshare.gestures.config.ShortcutIconLoader;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ShortcutCatalogRefreshTest {
    @Test public void refreshRequestsDoNotLookLikePublishedUpdates() {
        var context = RuntimeEnvironment.getApplication();
        var resolver = context.getContentResolver();
        var requests = new AtomicInteger();
        var updates = new AtomicInteger();
        var requestObserver = new ContentObserver(null) {
            @Override public void onChange(boolean selfChange) { requests.incrementAndGet(); }
        };
        var updateObserver = new ContentObserver(null) {
            @Override public void onChange(boolean selfChange) { updates.incrementAndGet(); }
        };
        resolver.registerContentObserver(ConfigContract.CATALOG_REFRESH_URI, false, requestObserver);
        resolver.registerContentObserver(ConfigContract.CATALOG_URI, false, updateObserver);
        try {
            var store = new ConfigStore(context);
            store.requestShortcutCatalog();
            store.requestActivityCatalog();
            ShadowLooper.shadowMainLooper().idle();
            assertEquals(2, requests.get());
            assertEquals(0, updates.get());
        } finally {
            resolver.unregisterContentObserver(requestObserver);
            resolver.unregisterContentObserver(updateObserver);
        }
    }

    @Test public void pruningKeepsPublishedIconsForTheMatchingUser() throws Exception {
        Map<String, Long> versions = ReflectionHelpers.getStaticField(
                ShortcutHostRuntime.class, "publishedIconVersions");
        String standard = ShortcutIconLoader.key("sample", "order", 0);
        String cloned = ShortcutIconLoader.key("sample", "order", 999);
        versions.put(standard, 1L);
        versions.put(cloned, 2L);
        try {
            JSONArray catalog = new JSONArray("""
                    [{"package":"sample","id":"order","userId":999}]
                    """);
            ReflectionHelpers.callStaticMethod(ShortcutHostRuntime.class, "prunePublishedIconVersions",
                    ReflectionHelpers.ClassParameter.from(JSONArray.class, catalog));
            assertEquals(Map.of(cloned, 2L), versions);
        } finally {
            versions.clear();
        }
    }
}
