package com.leaf.hyperdragshare.gestures.xposed;

import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.os.Bundle;

import com.leaf.hyperdragshare.codex.ScreenOcrActivity;
import com.leaf.hyperdragshare.gestures.config.AppTarget;
import com.leaf.hyperdragshare.gestures.config.ConfigContract;
import com.leaf.hyperdragshare.gestures.config.ConfigProvider;
import com.leaf.hyperdragshare.gestures.config.ConfigStore;

import java.util.List;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPendingIntent;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RegionOcrTargetTest {
    @Test public void oldTargetKeepsItsPositionAndResolvesForFullscreenInBothMenus() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ConfigStore store = new ConfigStore(context);
        AppTarget ocr = AppTarget.fromJson(new JSONObject("{\"type\":\"region_ocr\",\"label\":\"区域识字\"}"));
        assertEquals("全屏识字", ocr.shortcutLabel);
        assertEquals("[\"region_ocr\"]", ocr.positionIdentity());
        AppTarget more = AppTarget.moreApps();
        store.setTargets(List.of(more, ocr));
        store.setHoneycombTargets(List.of(ocr, more));

        assertEquals(List.of(more, ocr), store.getTargets());
        assertEquals(List.of(ocr, more), store.getHoneycombTargets());
        GestureConfig config = GestureConfig.from(provider(context).call("get", null, null));
        Context systemUi = new ContextWrapper(context) {
            @Override public String getPackageName() { return "com.android.systemui"; }
            @Override public Resources getResources() { return Resources.getSystem(); }
        };
        FanRuntimeTargetResolver.ResolvedTargets resolved = new FanRuntimeTargetResolver(systemUi).resolve(config);
        assertTrue(resolved.targets.get(0).isMoreApps());
        assertTrue(resolved.targets.get(1).isRegionOcr());
        assertNotNull(resolved.targets.get(1).icon);
        RuntimeTarget honeycombOcr = resolved.honeycombTargets.get(0);
        assertTrue(honeycombOcr.isRegionOcr());
        assertNotNull(honeycombOcr.icon);
        assertFalse(honeycombOcr.isShortcut());
        assertEquals(ocr.positionIdentity(), honeycombOcr.positionIdentity());
        assertNotNull(config.honeycombPositions.find(ocr.positionIdentity()));
    }

    @Test public void gestureEntryStartsThePrivateOcrActivityWithoutRegionSelection() {
        Context context = RuntimeEnvironment.getApplication();
        Bundle result = provider(context).call(ConfigContract.METHOD_GET_SCREEN_OCR_ENTRY, null, null);
        PendingIntent pending = result.getParcelable(ConfigContract.KEY_SCREEN_OCR_ENTRY, PendingIntent.class);
        assertNotNull(pending);
        assertTrue(pending.isImmutable());
        Intent intent = Shadow.<ShadowPendingIntent>extract(pending).getSavedIntent();
        assertEquals(ScreenOcrActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("com.leaf.hyperdragshare.codex.SCREEN_OCR", intent.getAction());
        assertFalse(intent.hasExtra(ScreenOcrActivity.EXTRA_SELECT_REGION));
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    private static ConfigProvider provider(Context context) {
        ConfigProvider provider = new ConfigProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = ConfigContract.AUTHORITY;
        provider.attachInfo(context, info);
        return provider;
    }
}
