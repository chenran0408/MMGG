package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.*;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.leaf.hyperdragshare.compat.xposed.XC_MethodHook;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ContentCatcherProbeHooksTest {
    @Before public void setup() {
        DragShareLog.configure(DragShareSettings.defaults());
        ShadowLog.clear();
    }

    @Test public void imageProbePreservesHostPayloadAndDoesNotLogItsContents() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        ImageView view = new ImageView(RuntimeEnvironment.getApplication());
        view.layout(0, 0, 160, 90);
        view.setImageDrawable(new BitmapDrawable(view.getResources(), bitmap));
        Bundle bundle = new Bundle();
        bundle.putParcelable("viewBitmap", bitmap);
        bundle.putInt("catchType", 2);
        bundle.putString("imgUrl", "https://example.test/private.jpg?token=secret-token");
        bundle.putString("viewContent", "private-message");
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.thisObject = new FakeFetcher();
        param.args = new Object[]{view, 3, false};
        param.result = bundle;
        Object[] args = param.args;

        observe("fetcherObserver", "afterHookedMethod", param);

        assertSame(bundle, param.getResult());
        assertSame(args, param.args);
        assertSame(bitmap, bundle.getParcelable("viewBitmap"));
        assertFalse(bitmap.isRecycled());
        assertFalse(param.isReturnEarly());
        assertEquals(4, bundle.size());
        String logs = messages();
        assertTrue(logs.contains("bitmap=320x180"));
        assertTrue(logs.contains("imgUrl=present/https"));
        assertFalse(logs.contains("private-message"));
        assertFalse(logs.contains("private.jpg"));
        assertFalse(logs.contains("secret-token"));
        bitmap.recycle();
    }

    @Test public void inputProbeReadsDexFieldNamesWithoutChangingGestureOrEvent() throws Exception {
        FakeListener listener = new FakeListener();
        MotionEvent event = MotionEvent.obtain(100, 450, MotionEvent.ACTION_DOWN, 20, 30, 0);
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.thisObject = listener;
        param.args = new Object[]{event, new View(RuntimeEnvironment.getApplication())};

        observe("inputObserver", "beforeHookedMethod", param);

        assertEquals(2, listener.f);
        assertFalse(listener.i);
        assertFalse(listener.j);
        assertSame(event, param.args[0]);
        assertEquals(100, event.getDownTime());
        assertEquals(450, event.getEventTime());
        assertEquals(MotionEvent.ACTION_DOWN, event.getActionMasked());
        assertFalse(param.isReturnEarly());
        assertTrue(messages().contains("mode=2 fired=false accepted=false"));
        event.recycle();
    }

    @Test public void failedHostExtractionKeepsOriginalException() throws Exception {
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.args = new Object[]{new View(RuntimeEnvironment.getApplication()), 3, false};
        Throwable failure = new IllegalStateException("private-source");
        param.throwable = failure;

        observe("fetcherObserver", "afterHookedMethod", param);

        assertSame(failure, param.getThrowable());
        assertFalse(param.isReturnEarly());
        assertNull(param.getResult());
        assertTrue(messages().contains("error=java.lang.IllegalStateException"));
        assertFalse(messages().contains("private-source"));
    }

    @Test public void nullExtractionIsRecordedInsteadOfLookingLikeMissingProbe() throws Exception {
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.args = new Object[]{new View(RuntimeEnvironment.getApplication()), 3, false};

        observe("fetcherObserver", "afterHookedMethod", param);

        assertNull(param.getResult());
        assertFalse(param.isReturnEarly());
        assertTrue(messages().contains("empty candidate package=test.host"));
        assertTrue(messages().contains("error=none"));
    }

    private static void observe(String factoryName, String callbackName,
            XC_MethodHook.MethodHookParam param) throws Exception {
        Method factory = "fetcherObserver".equals(factoryName)
                ? ContentCatcherProbeHooks.class.getDeclaredMethod(factoryName,
                        String.class, String.class, String.class)
                : ContentCatcherProbeHooks.class.getDeclaredMethod(factoryName,
                        String.class, String.class);
        factory.setAccessible(true);
        Object hook = "fetcherObserver".equals(factoryName)
                ? factory.invoke(null, "test.host", "test.host", "view")
                : factory.invoke(null, "test.host", "test.host");
        Method callback = hook.getClass().getDeclaredMethod(callbackName,
                XC_MethodHook.MethodHookParam.class);
        callback.setAccessible(true);
        callback.invoke(hook, param);
    }

    private static String messages() {
        StringBuilder output = new StringBuilder();
        for (ShadowLog.LogItem log : ShadowLog.getLogsForTag("DragShare/ContentProbe")) {
            output.append(log.msg).append('\n');
        }
        return output.toString();
    }

    static final class FakeFetcher {
        final Map<String, String> mViewClassReflectionMap = new HashMap<>();
    }

    static final class FakeListener {
        Activity o;
        int f = 2;
        boolean i;
        boolean j;
    }
}
