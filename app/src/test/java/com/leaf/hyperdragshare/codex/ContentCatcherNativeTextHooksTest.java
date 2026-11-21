package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.*;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import com.leaf.hyperdragshare.compat.xposed.XC_MethodHook;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ContentCatcherNativeTextHooksTest {
    @Test public void missingCustomTextUsesExistingExtractorWithoutChangingHostView() {
        CustomText view = new CustomText();
        ExportRecord record = new ExportRecord();
        Fetcher fetcher = new Fetcher();
        Bundle args = captureRequest();

        assertTrue(fill(record, view, fetcher, args));

        assertEquals("private sample", record.c.get(record.b.indexOf("text")));
        assertEquals(1, fetcher.flags);
        assertFalse(fetcher.needHint);
        assertEquals(1, fetcher.calls);
        assertEquals("android.widget.TextView", view.getAccessibilityClassName());
        assertEquals(5, args.size());
        for (ShadowLog.LogItem log : ShadowLog.getLogsForTag("DragShare/NativeText")) {
            assertFalse(log.msg.contains("private sample"));
        }
    }

    @Test public void ordinaryRequestsAndImageOnlyRequestsDoNotReadCustomText() {
        for (int variant = 0; variant < 4; variant++) {
            Bundle args = captureRequest();
            if (variant == 0) args.remove(SystemUiAgent.CAPTURE_TEXT);
            if (variant == 1) args.putBoolean(SystemUiAgent.CAPTURE_TEXT, false);
            if (variant == 2) args.putInt("uiAgentType", 1);
            if (variant == 3) args.remove(SystemUiAgent.TOUCH_X);
            Fetcher fetcher = new Fetcher();
            assertFalse(fill(new ExportRecord(), new CustomText(), fetcher, args));
            assertEquals(0, fetcher.calls);
        }
    }

    @Test public void existingTextStandardTextAndOtherTouchLocationsRemainUntouched() {
        Fetcher fetcher = new Fetcher();
        ExportRecord record = new ExportRecord();
        record.a("text", "existing");
        assertFalse(fill(record, new CustomText(), fetcher, captureRequest()));
        assertEquals("existing", record.c.get(record.b.indexOf("text")));
        assertFalse(fill(new ExportRecord(), new TextView(RuntimeEnvironment.getApplication()),
                fetcher, captureRequest()));
        Bundle outside = captureRequest();
        outside.putFloat(SystemUiAgent.TOUCH_Y, 500);
        assertFalse(fill(new ExportRecord(), new CustomText(), fetcher, outside));
        assertEquals(0, fetcher.calls);
    }

    @Test public void passwordMetadataAndPasswordNodesNeverReachTheExtractor() {
        Fetcher fetcher = new Fetcher();
        ExportRecord protectedRecord = new ExportRecord();
        protectedRecord.a("password", "true");
        assertFalse(fill(protectedRecord, new CustomText(), fetcher, captureRequest()));
        CustomText protectedView = new CustomText();
        protectedView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setPassword(true);
            }
        });
        assertFalse(fill(new ExportRecord(), protectedView, fetcher, captureRequest()));
        assertEquals(0, fetcher.calls);
    }

    @Test public void imageAndEmptyResultsCannotBecomeText() {
        Fetcher fetcher = new Fetcher();
        fetcher.result.putInt("catchType", 2);
        ExportRecord record = new ExportRecord();
        assertFalse(fill(record, new CustomText(), fetcher, captureRequest()));
        assertFalse(record.b.contains("text"));
        fetcher.result.putInt("catchType", 1);
        fetcher.result.putString("viewContent", "");
        assertFalse(fill(record, new CustomText(), fetcher, captureRequest()));
    }

    @Test public void exportExceptionClearsRequestScopeAndPreservesTheException() throws Exception {
        XC_MethodHook hook = ContentCatcherNativeTextHooks.exportObserver();
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.thisObject = new ExportRunnable(captureRequest());
        param.args = new Object[0];
        Method before = hook.getClass().getDeclaredMethod("beforeHookedMethod", XC_MethodHook.MethodHookParam.class);
        Method after = hook.getClass().getDeclaredMethod("afterHookedMethod", XC_MethodHook.MethodHookParam.class);
        before.setAccessible(true);
        after.setAccessible(true);
        Field scopeField = ContentCatcherNativeTextHooks.class.getDeclaredField("request");
        scopeField.setAccessible(true);
        ThreadLocal<?> scope = (ThreadLocal<?>) scopeField.get(null);
        before.invoke(hook, param);
        assertNotNull(scope.get());
        Throwable original = new IllegalStateException("original failure");
        param.throwable = original;
        after.invoke(hook, param);
        assertNull(scope.get());
        assertSame(original, param.getThrowable());
        assertFalse(param.isReturnEarly());
    }

    private static boolean fill(ExportRecord record, View view, Fetcher fetcher, Bundle request) {
        return ContentCatcherNativeTextHooks.supplement(record, view, fetcher, request, Sanitizer.class);
    }

    private static Bundle captureRequest() {
        Bundle args = new Bundle();
        args.putInt("uiAgentType", 0);
        args.putInt("catch_way", 1);
        args.putBoolean(SystemUiAgent.CAPTURE_TEXT, true);
        args.putFloat(SystemUiAgent.TOUCH_X, 50);
        args.putFloat(SystemUiAgent.TOUCH_Y, 50);
        return args;
    }

    static final class CustomText extends View {
        CustomText() { super(RuntimeEnvironment.getApplication()); }
        @Override public CharSequence getAccessibilityClassName() { return "android.widget.TextView"; }
    }
    static final class ExportRecord {
        final List<String> b = new ArrayList<>();
        final List<String> c = new ArrayList<>();
        ExportRecord() { a("bounds", "[0,0][100,100]"); }
        public void a(String key, String value) { b.add(key); c.add(value); }
    }
    static final class Fetcher {
        int calls, flags;
        boolean needHint;
        final Bundle result = new Bundle();
        Fetcher() { result.putInt("catchType", 1); result.putString("viewContent", "private sample"); }
        public Bundle fetchViewContent(View view, int flags, boolean needHint) {
            calls++; this.flags = flags; this.needHint = needHint;
            return result;
        }
    }
    static final class Sanitizer {
        public static String d(CharSequence value) { return value.toString(); }
    }
    static final class ExportRunnable {
        final Request c;
        ExportRunnable(Bundle args) { c = new Request(args); }
    }
    static final class Request {
        final Bundle d;
        Request(Bundle args) { d = args; }
    }
}
