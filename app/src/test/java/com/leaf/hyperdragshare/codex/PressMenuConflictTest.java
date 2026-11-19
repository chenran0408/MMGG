package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.*;
import android.content.Context;
import android.os.Binder;
import android.os.SystemClock;
import android.view.MotionEvent;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class PressMenuConflictTest {
    @Test public void qualifiedSingleFingerClaimsTouchBeforeImagePixelsReturn() throws Exception {
        check(true, false, false, 1);
    }

    @Test public void unconfirmedCandidateDoesNotCancelTheAppGesture() throws Exception {
        check(false, false, false, 0);
    }

    @Test public void twoFingerCandidateKeepsItsExistingSegmentationPath() throws Exception {
        check(true, true, false, 0);
    }

    @Test public void supersededCandidateCannotCancelANewerGesture() throws Exception {
        check(true, false, true, 0);
    }

    @Test public void candidateReadyBeforeDeadlineClaimsTouchOnlyWhenDurationIsConfirmed() throws Exception {
        check(true, false, false, 1, true);
    }

    @Test public void aRecordingStartedDuringTheHoldKeepsTheOriginalTouchWhileCaptureIsBusy() throws Exception {
        check(true, false, false, 0, true, true);
    }

    private void check(boolean confirmed, boolean twoFinger, boolean stale, int expected) throws Exception {
        check(confirmed, twoFinger, stale, expected, false);
    }

    private void check(boolean confirmed, boolean twoFinger, boolean stale, int expected,
                       boolean candidateFirst) throws Exception {
        check(confirmed, twoFinger, stale, expected, candidateFirst, false);
    }

    private void check(boolean confirmed, boolean twoFinger, boolean stale, int expected,
                       boolean candidateFirst, boolean recording) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        PressCaptureSource source = new PressCaptureSource(context, null, controller, DragShareSettings.defaults());
        BackgroundTouchBlocker old = (BackgroundTouchBlocker) field(source, "blocker").get(source);
        old.close();
        AtomicInteger cancelled = new AtomicInteger();
        BackgroundTouchBlocker blocker = new BackgroundTouchBlocker(context, (target, method, args) -> {
            if ("cancelCurrentTouch".equals(method)) cancelled.incrementAndGet();
            return null;
        }, () -> false);
        field(source, "blocker").set(source, blocker);
        long now = SystemClock.uptimeMillis();
        CountDownLatch releaseCapture = new CountDownLatch(1);
        try {
            ExecutorService capture = (ExecutorService) field(source, "worker").get(source);
            capture.submit(() -> {}).get(5, TimeUnit.SECONDS);
            if (recording) capture.submit(() -> {
                releaseCapture.await();
                return null;
            });
            ShadowLooper.shadowMainLooper().idle();
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20, 30, now, "root");
            ShadowLooper.shadowMainLooper().idle();
            field(source, "pointerDown").setBoolean(source, true);
            field(source, "downTime").setLong(source, now);
            field(source, "twoFingerModeForGesture").setBoolean(source, twoFinger);
            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            detector.start(20, 30, now, 500);
            if (confirmed && !candidateFirst) detector.fire(now + 500);
            long generation = field(source, "generation").getLong(source);
            var ready = PressCaptureSource.class.getDeclaredMethod("onCaptureCandidateReady", long.class, String.class);
            ready.setAccessible(true);
            ready.invoke(source, stale ? generation - 1 : generation, recording ? "private.chat" : null);
            ShadowLooper.shadowMainLooper().idle();
            ExecutorService executor = (ExecutorService) field(blocker, "background").get(blocker);
            if (candidateFirst) {
                executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
                assertEquals("a content node alone cannot consume the app gesture", 0, cancelled.get());
                IRootNodeLease lease = (IRootNodeLease) java.lang.reflect.Proxy.newProxyInstance(
                        IRootNodeLease.class.getClassLoader(), new Class<?>[]{IRootNodeLease.class},
                        (proxy, method, args) -> {
                            if ("asBinder".equals(method.getName())) return new Binder();
                            if ("close".equals(method.getName())) return null;
                            if ("startedMicRecording".equals(method.getName())) {
                                assertEquals("private.chat", args[0]);
                                assertEquals(now, args[1]);
                                return recording;
                            }
                            throw new AssertionError("Unexpected lease call: " + method.getName());
                        });
                field(source, "nodeSession").set(source, new SharedRootNodeCapture.Connection(
                        context, new Binder(), 10000, lease));
                field(source, "lookupRunning").setBoolean(source, true);
                ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(500));
                var fire = PressCaptureSource.class.getDeclaredMethod("firePress");
                fire.setAccessible(true);
                fire.invoke(source);
                assertTrue(detector.isFired());
            }
            if (recording) {
                ((ExecutorService) field(source, "recordingWorker").get(source)).submit(() -> {}).get(5, TimeUnit.SECONDS);
                ShadowLooper.shadowMainLooper().idle();
                assertFalse(field(source, "pointerDown").getBoolean(source));
            }
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertNull("test deliberately has no decoded image", field(source, "cached").get(source));
            assertEquals(expected, cancelled.get());
        } finally {
            releaseCapture.countDown();
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    private static Field field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
