package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.WindowInsets;
import java.util.List;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import kotlin.Unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ScreenOcrLifecycleTest {
    @Test public void fullScreenCropRemovesOnlyVisibleStatusBarAtCaptureResolution() {
        int status = WindowInsets.Type.statusBars();
        int navigation = WindowInsets.Type.navigationBars();
        WindowInsets insets = new WindowInsets.Builder()
                .setInsets(status, Insets.of(0, 36, 0, 0)).setVisible(status, true)
                .setInsets(navigation, Insets.of(0, 0, 0, 24)).setVisible(navigation, true)
                .build();
        assertEquals(new Rect(0, 48, 1440, 3200), ScreenOcrSessionKt.screenOcrContentBounds(
                new Rect(0, 0, 1080, 2400), insets, 1440, 3200));
    }

    @Test public void immersiveOrRotatedCaptureLeavesImageIntact() {
        int status = WindowInsets.Type.statusBars();
        WindowInsets hidden = new WindowInsets.Builder()
                .setInsetsIgnoringVisibility(status, Insets.of(0, 36, 0, 0))
                .setVisible(status, false).build();
        WindowInsets visible = new WindowInsets.Builder()
                .setInsets(status, Insets.of(0, 36, 0, 0)).setVisible(status, true).build();
        Rect bounds = new Rect(0, 0, 1080, 2400);
        assertNull(ScreenOcrSessionKt.screenOcrContentBounds(bounds, hidden, 1080, 2400));
        assertNull(ScreenOcrSessionKt.screenOcrContentBounds(bounds, visible, 2400, 1080));
    }

    @Test
    @Config(shadows = TextPanelShadow.class)
    public void fullScreenResultOpensTheSharedWordPanelAndKeepsItsHost() throws Exception {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity host = controller.get();
            List<Boolean> closeEvents = new ArrayList<>();
            ScreenOcrSession session = new ScreenOcrSession(host.getApplicationContext(), false,
                    host, finishHost -> { closeEvents.add(finishHost); return Unit.INSTANCE; });
            TextPanelShadow.text = null;
            session.start(() -> Unit.INSTANCE);
            try {
                var completed = ScreenOcrSession.class.getDeclaredMethod("recognitionCompleted", List.class);
                completed.setAccessible(true);
                completed.invoke(session, List.of("第一行", "第二行"));

                assertEquals("第一行\n第二行", TextPanelShadow.text);
                assertEquals(List.of(false), closeEvents);
            } finally {
                session.close(true);
            }
        }
    }

    @Implements(value = BigBangOverlayLauncher.class, isInAndroidSdk = false)
    public static class TextPanelShadow {
        static String text;

        @Implementation
        protected static boolean requestHosted(Activity host, Bundle input) {
            text = input.getString("text");
            return true;
        }
    }

    @Test public void closingBeforeCaptureCancelsBothPendingAndAlreadyDispatchedWork() throws Exception {
        AtomicInteger closed = new AtomicInteger();
        ScreenOcrSession session = new ScreenOcrSession(RuntimeEnvironment.getApplication(),
                true, null, finishHost -> { closed.incrementAndGet(); return Unit.INSTANCE; });
        AtomicBoolean sourceRemoved = new AtomicBoolean();
        session.start(() -> { sourceRemoved.set(true); return Unit.INSTANCE; });
        session.close(true);
        // A callback already dispatched to the main thread must not use the shut-down executor.
        var capture = ScreenOcrSession.class.getDeclaredMethod("captureAndRecognize");
        capture.setAccessible(true);
        capture.invoke(session);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(1));
        session.close(true);
        assertTrue(sourceRemoved.get());
        assertEquals(1, closed.get());
    }
}
