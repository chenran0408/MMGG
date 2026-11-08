package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class TwoFingerPressTest {
    @Test public void emptyEarlyRootResultKeepsSingleFingerEligibleForPortalText() throws Exception {
        checkEmptyRootThenPortal(false, false, true);
    }

    @Test public void emptyEarlyRootResultKeepsTwoFingerPortalOnSegmentationRoute() throws Exception {
        checkEmptyRootThenPortal(true, false, true);
    }

    @Test public void binderPortalCallbackUsesTheSameTwoFingerRoute() throws Exception {
        checkEmptyRootThenPortal(true, true, true);
    }

    @Test public void miuiFallbackCanQualifyWhenRootInputIsUnavailable() throws Exception {
        checkEmptyRootThenPortal(true, true, false);
    }

    private void checkEmptyRootThenPortal(boolean twoFinger, boolean binderCallback,
                                        boolean rootInputReady) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, rootInputReady);
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings);
        java.lang.reflect.Field hookController = PortalHooks.class.getDeclaredField("controller");
        java.lang.reflect.Field hookPress = PortalHooks.class.getDeclaredField("thumbCaptureSource");
        java.lang.reflect.Field hookInput = PortalHooks.class.getDeclaredField("rootTouchSource");
        hookController.setAccessible(true);
        hookPress.setAccessible(true);
        hookInput.setAccessible(true);
        Object previousController = hookController.get(null);
        Object previousPress = hookPress.get(null);
        Object previousInput = hookInput.get(null);
        Handler ui = new Handler(Looper.getMainLooper());
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            hookController.set(null, controller);
            hookPress.set(null, source);
            hookInput.set(null, input);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            ui.post(() -> {
                if (rootInputReady) source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime);
                else {
                    try {
                        java.lang.reflect.Method dispatch = PortalHooks.class.getDeclaredMethod(
                                "dispatchMotion", MotionEvent.class);
                        dispatch.setAccessible(true);
                        dispatch.invoke(null, MotionEvent.obtain(downTime, downTime,
                                MotionEvent.ACTION_DOWN, 20f, 30f, 0));
                    } catch (ReflectiveOperationException error) {
                        throw new AssertionError(error);
                    }
                }
            });
            ShadowLooper.shadowMainLooper().idle();
            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(120));
            ShadowLooper.shadowMainLooper().idle();
            ExecutorService worker = (ExecutorService) field(source, "worker").get(source);
            worker.submit(() -> { }).get(5, TimeUnit.SECONDS);
            ShadowLooper.shadowMainLooper().idle();
            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertTrue("Empty node capture must not cancel the physical hold", detector.isActive());
            ui.post(() -> {
                if (twoFinger) assertTrue(source.onContactEvent(MotionEvent.ACTION_POINTER_DOWN,
                        20f, 30f, 40f, 50f, 2, SystemClock.uptimeMillis()));
                if (!binderCallback) assertTrue(source.acceptPortalContent(CapturedContent.text(
                        "portal message", "com.tencent.mm", null)));
            });
            ShadowLooper.shadowMainLooper().idle();
            if (binderCallback) {
                Thread binder = new Thread(() -> PortalHooks.dispatchPortalContent(null, null,
                        settings, CapturedContent.text("portal message", "com.tencent.mm", null)),
                        "binder:portal-test");
                binder.start();
                binder.join();
                assertNull("Binder result must wait for the qualification queue",
                        field(source, "cached").get(source));
                ShadowLooper.shadowMainLooper().idle();
                assertNotNull(field(source, "cached").get(source));
            }
            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(600));
            ShadowLooper.shadowMainLooper().idle();
            assertEquals(!twoFinger, controller.hasPendingImagePreview());
            assertEquals(twoFinger ? 1 : 0, provider.bigBangOpenCount);
            if (twoFinger) assertFalse(provider.bigBangRequest.getBoolean("drag_preview"));
            if (binderCallback) {
                ui.post(source::cancelGesture);
                ShadowLooper.shadowMainLooper().idle();
                Bitmap late = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
                ui.post(() -> PortalHooks.dispatchPortalContent(null, null, settings,
                        CapturedContent.portalMediaImage(late, "com.tencent.mm", new Rect())));
                ShadowLooper.shadowMainLooper().idle();
                assertTrue("An unowned native image must be released without a drag fallback", late.isRecycled());
                assertFalse(controller.hasPendingImagePreview() || controller.isActive());
            }
        } finally {
            hookController.set(null, previousController);
            hookPress.set(null, previousPress);
            hookInput.set(null, previousInput);
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test @Config(qualifiers = "land")
    public void landscapeDefaultConsumesPortalResultWithoutSingleFingerPreview() throws Exception {
        checkLandscapePress(true, -1L);
    }

    @Test @Config(qualifiers = "land")
    public void landscapeSwitchOffRestoresSingleFingerPreview() throws Exception {
        checkLandscapePress(false, -1L);
    }

    @Test @Config(qualifiers = "land")
    public void landscapeStillConfirmsTwoFingerAtItsOwnDeadline() throws Exception {
        checkLandscapePress(true, 100L);
    }

    @Test @Config(qualifiers = "land")
    public void landscapeSecondFingerCanJoinAfterSuppressedSingleFingerDeadline() throws Exception {
        checkLandscapePress(true, 900L);
    }

    private void checkLandscapePress(boolean disableSingleFinger, long secondFingerDelay)
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        Bundle stored = new Bundle();
        if (!disableSingleFinger) stored.putBoolean("disable_single_finger_in_landscape", false);
        DragShareSettings settings = DragShareSettings.fromBundle(stored);
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings);
        long downTime = SystemClock.uptimeMillis();
        Handler ui = new Handler(Looper.getMainLooper());
        try {
            field(source, "nodeSession").set(source, provider.connection);
            BackgroundTouchBlocker original = (BackgroundTouchBlocker) field(source, "blocker").get(source);
            original.close();
            field(source, "blocker").set(source, new BackgroundTouchBlocker(
                    context, (target, method, args) -> null, () -> false));
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            ui.post(() -> {
                source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime);
                assertTrue("Portal callback must be consumed by the live press, without fallback",
                        source.acceptPortalContent(CapturedContent.text("landscape text", "test.source", null)));
            });
            ShadowLooper.shadowMainLooper().idle();

            if (secondFingerDelay >= 0L) {
                ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(secondFingerDelay));
                ShadowLooper.shadowMainLooper().idle();
                assertFalse(controller.hasPendingImagePreview());
                ui.post(() -> assertTrue("Second finger must remain eligible in landscape",
                        source.onContactEvent(MotionEvent.ACTION_POINTER_DOWN,
                                20f, 30f, 40f, 50f, 2, downTime + secondFingerDelay)));
                ShadowLooper.shadowMainLooper().idle();
                ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(599));
                ShadowLooper.shadowMainLooper().idle();
                assertNull("Two fingers must wait for their own duration", provider.bigBangRequest);
                ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(1));
            } else {
                ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(900));
            }
            ShadowLooper.shadowMainLooper().idle();
            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            if (secondFingerDelay >= 0L) {
                assertNotNull("Two-finger hold opens segmentation before release", provider.bigBangRequest);
                assertFalse(provider.bigBangRequest.getBoolean("drag_preview"));
                assertEquals(1, provider.bigBangOpenCount);
            } else {
                assertEquals(!disableSingleFinger, detector.isFired());
                assertEquals(!disableSingleFinger, controller.hasPendingImagePreview());
                assertNull(provider.bigBangRequest);
                ui.post(() -> source.onPointerEvent(MotionEvent.ACTION_UP, 20f, 30f,
                        SystemClock.uptimeMillis()));
                ShadowLooper.shadowMainLooper().idle();
                assertFalse(controller.hasPendingImagePreview());
            }
        } finally {
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void loneSinglePointerStillConfirms() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() ->
                    source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime));
            ShadowLooper.shadowMainLooper().idle();

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(700));
            ShadowLooper.shadowMainLooper().idle();

            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertTrue("combined triggers keep the lone single-finger press qualified",
                    detector.isFired());
            assertNull("no release means no panel yet", provider.bigBangRequest);
        } finally {
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void earlyCachedCaptureStartsSingleFingerDragPreviewAtConfirmation() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        AtomicBoolean ocrStarted = new AtomicBoolean();
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings,
                (ocrContext, image, callback) -> {
                    ocrStarted.set(true);
                    return () -> { };
                });
        Bitmap screenshot = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() ->
                    source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime));
            ShadowLooper.shadowMainLooper().idle();

            // Model an early asynchronous capture result waiting for gesture qualification.
            field(source, "cached").set(source, CapturedContent.pressOcrRegion(
                    screenshot, "com.tencent.mm", new Rect(10, 20, 100, 120)));
            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(601));
            ShadowLooper.shadowMainLooper().idle();

            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertFalse("single-finger confirmation must not start OCR",
                    ocrStarted.get());
            assertTrue("combined triggers confirm the lone single-finger press",
                    detector.isFired());
            assertTrue("single-finger confirmation shows a cancellable drag preview",
                    controller.hasPendingImagePreview());
            assertFalse(controller.isActive());
        } finally {
            source.stop();
            controller.destroy();
            if (!screenshot.isRecycled()) screenshot.recycle();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void captureReturningIntoNewUnqualifiedTwoFingerGestureIsDroppedAndRecycled()
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        provider.captureStarted = new CountDownLatch(1);
        provider.allowCaptureReturn = new CountDownLatch(1);
        provider.regionCaptureReturned = new CountDownLatch(1);
        Bitmap screenshot = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        Rect bounds = new Rect(10, 20, 100, 120);
        provider.captureResponse = new Bundle();
        provider.captureResponse.putString("package", "com.tencent.mm");
        provider.captureResponse.putParcelable("bounds", bounds);
        provider.captureResponse.putBoolean("image", true);
        provider.captureResponse.putBoolean("press_ocr_region", true);
        provider.captureResponse.putInt("window_id", 7);
        provider.pressRegionResponse = new Bundle();
        provider.pressRegionResponse.putParcelable("bitmap", screenshot);
        provider.pressRegionResponse.putParcelable("bounds", bounds);
        provider.pressRegionResponse.putBoolean("image_cropped", true);

        DragShareSettings singleFinger = DragShareSettings.defaults().toBuilder()
                .build();
        DragShareSettings twoFinger = singleFinger.toBuilder()
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        AtomicBoolean ocrStarted = new AtomicBoolean();
        PressCaptureSource source = new PressCaptureSource(context, input, controller, singleFinger,
                (ocrContext, bitmap, callback) -> {
                    ocrStarted.set(true);
                    return () -> { };
                });
        long firstDownTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f,
                    firstDownTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() -> source.onPointerEvent(
                    MotionEvent.ACTION_DOWN, 20f, 30f, firstDownTime));
            ShadowLooper.shadowMainLooper().idle();

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(120));
            ShadowLooper.shadowMainLooper().idle();
            assertTrue("single-finger classification should start Root capture",
                    provider.captureStarted.await(5, TimeUnit.SECONDS));

            source.updateSettings(twoFinger);
            long secondGestureDownTime = firstDownTime + 1000L;
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f,
                    secondGestureDownTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() -> source.onPointerEvent(
                    MotionEvent.ACTION_DOWN, 20f, 30f, secondGestureDownTime));
            ShadowLooper.shadowMainLooper().idle();
            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertTrue("the new gesture must start as an unconfirmed single-finger press",
                    detector.isActive());
            assertFalse(detector.isFired());

            provider.allowCaptureReturn.countDown();
            assertTrue("the stale capture should finish constructing its image result",
                    provider.regionCaptureReturned.await(5, TimeUnit.SECONDS));
            ExecutorService worker = (ExecutorService) field(source, "worker").get(source);
            worker.submit(() -> { }).get(5, TimeUnit.SECONDS);
            ShadowLooper.shadowMainLooper().idle();

            assertTrue("stale Root image content must be recycled when dropped", screenshot.isRecycled());
            assertFalse("a stale result must not start OCR or its touch blocker", ocrStarted.get());
            assertNull(provider.bigBangRequest);
            assertFalse("a stale result must not open a preview", controller.isActive());
            assertEquals(-1L, field(controller, "pressPipelineDownTime").getLong(controller));
            assertTrue("the new single-finger press must remain unconfirmed",
                    detector.isActive());
            assertFalse(detector.isFired());
        } finally {
            provider.allowCaptureReturn.countDown();
            source.stop();
            controller.destroy();
            if (!screenshot.isRecycled()) screenshot.recycle();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void twoFingerModeDoesNotDowngradeAfterSecondFingerIsReleased() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings);
        AtomicBoolean reducedContactAccepted = new AtomicBoolean(true);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() -> {
                source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime);
                source.onContactEvent(MotionEvent.ACTION_POINTER_DOWN,
                        20f, 30f, 40f, 50f, 2, downTime + 100L);
                reducedContactAccepted.set(source.onContactEvent(MotionEvent.ACTION_POINTER_UP,
                        20f, 30f, 40f, 50f, 1, downTime + 200L));
            });
            ShadowLooper.shadowMainLooper().idle();

            // F1 remains down after F2's UP; it must not resume a single-finger timer.
            new Handler(Looper.getMainLooper()).post(() ->
                    source.onPointerEvent(MotionEvent.ACTION_MOVE, 20f, 30f, downTime + 300L));
            ShadowLooper.shadowMainLooper().idle();

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(3000));
            ShadowLooper.shadowMainLooper().idle();

            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertFalse("unconfirmed pointer reduction must reject the Root stream",
                    reducedContactAccepted.get());
            assertFalse("lifting finger two must not restore single-finger confirmation", detector.isFired());
            assertNull(provider.bigBangRequest);
        } finally {
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void confirmedTwoFingerHoldStartsImageOcrAtConfirmation() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings);
        Bitmap image = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() -> {
                source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime);
                source.onContactEvent(MotionEvent.ACTION_POINTER_DOWN,
                        20f, 30f, 40f, 50f, 2, downTime + 100L);
                fieldUnchecked(source, "cached", CapturedContent.image(
                        image, "test.source", new Rect(0, 0, 4, 4), true));
            });
            ShadowLooper.shadowMainLooper().idle();

            // The confirmation timer starts when the second finger joins.
            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(800));
            ShadowLooper.shadowMainLooper().idle();
            assertTrue("the hold is qualified and presents the image flow at confirmation",
                    ((PressTriggerDetector) field(source, "detector").get(source)).isFired());
            assertFalse("two-finger image flow must not start a drag session",
                    controller.isActive());
            assertTrue("two-finger confirmation starts the segmentation OCR candidate",
                    ((PressOcrFlow) field(source, "pressOcr").get(source)).hasCandidate());

            new Handler(Looper.getMainLooper()).post(() -> source.onContactEvent(
                    MotionEvent.ACTION_POINTER_UP, 20f, 30f, 40f, 50f, 1, downTime + 601L));
            ShadowLooper.shadowMainLooper().idle();
            assertFalse("the confirmed two-finger image flow never starts a drag session",
                    controller.isActive());
            assertFalse("the remaining finger should leave two-contact qualification mode",
                    field(source, "twoFingerContactSequence").getBoolean(source));

            new Handler(Looper.getMainLooper()).post(() ->
                    {
                        controller.acceptPointerEvent(MotionEvent.ACTION_MOVE,
                                80f, 90f, downTime + 850L, "root");
                        source.onPointerEvent(MotionEvent.ACTION_MOVE, 80f, 90f, downTime + 850L);
                    });
            ShadowLooper.shadowMainLooper().idle();
            assertFalse("movement after two-finger confirmation remains in OCR mode",
                    controller.isActive());

            new Handler(Looper.getMainLooper()).post(() ->
                    {
                        controller.acceptPointerEvent(MotionEvent.ACTION_UP,
                                80f, 90f, downTime + 900L, "root");
                    });
            ShadowLooper.shadowMainLooper().idle();
            source.onPointerEvent(MotionEvent.ACTION_UP, 80f, 90f, downTime + 900L);
            ShadowLooper.shadowMainLooper().idle();
            assertFalse("root release leaves no image drag session",
                    controller.isActive());
        } finally {
            source.stop();
            controller.destroy();
            if (!image.isRecycled()) image.recycle();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void asynchronousPortalImageBeforeConfirmationCachesForQualifiedSinglePress()
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings);
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        try {
            field(source, "nodeSession").set(source, provider.connection);
            long downTime = SystemClock.uptimeMillis();
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() ->
                    source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime));
            ShadowLooper.shadowMainLooper().idle();

            CapturedContent portalImage = CapturedContent.portalMediaImage(
                    bitmap, "com.tencent.mm", new Rect(0, 0, 2, 2));
            boolean handled = source.acceptPortalContent(portalImage);
            ShadowLooper.shadowMainLooper().idle();

            assertTrue("MMGG must handle the owned early callback to prevent host fallback", handled);
            assertTrue("early Portal payload should remain cached pending qualification",
                    field(source, "cached").get(source) == portalImage);
            assertFalse("a cached image must not open a preview before release", controller.isActive());
            assertEquals("the qualified single-finger press owns the pipeline", downTime,
                    field(controller, "pressPipelineDownTime").getLong(controller));
        } finally {
            source.stop();
            controller.destroy();
            if (!bitmap.isRecycled()) bitmap.recycle();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void twoFingerPortalImageWaitsForConfirmationBeforeStartingOcr() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings);
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        Bitmap screenshot = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        try {
            field(source, "nodeSession").set(source, provider.connection);
            long downTime = SystemClock.uptimeMillis();
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() -> {
                source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime);
                source.onContactEvent(MotionEvent.ACTION_POINTER_DOWN,
                        20f, 30f, 40f, 50f, 2, downTime + 100L);
            });
            ShadowLooper.shadowMainLooper().idle();

            Object pending = pendingOcr(CapturedContent.pressOcrRegion(
                    screenshot, "com.tencent.mm", new Rect()));
            setOcrCandidate(source, pending);
            CapturedContent portalImage = CapturedContent.portalMediaImage(
                    bitmap, "com.tencent.mm", new Rect(0, 0, 2, 2));
            assertTrue(source.acceptPortalContent(portalImage));
            ShadowLooper.shadowMainLooper().idle();

            assertTrue("the image should be cached while the two-finger timer is pending",
                    field(source, "cached").get(source) == portalImage);
            assertFalse("second-finger DOWN alone must not take over the Portal image",
                    controller.isActive());
            assertEquals(-1L, field(controller, "pressPipelineDownTime").getLong(controller));

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(599));
            ShadowLooper.shadowMainLooper().idle();
            assertFalse("preview must remain closed before the complete confirmation duration",
                    controller.isActive());

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(101));
            ShadowLooper.shadowMainLooper().idle();
            assertFalse("confirmed two-finger image must not enter drag mode",
                    controller.isActive());
            assertTrue("confirmed hold starts image segmentation OCR",
                    ((PressOcrFlow) field(source, "pressOcr").get(source)).hasCandidate());
            assertFalse("confirmed image is no longer waiting in the cache",
                    field(source, "cached").get(source) == portalImage);
            source.onContactEvent(MotionEvent.ACTION_POINTER_UP, 20f, 30f,
                    40f, 50f, 1, downTime + 650L);
            source.onPointerEvent(MotionEvent.ACTION_UP, 20f, 30f,
                    downTime + 700L);
            ShadowLooper.shadowMainLooper().idle();
            assertFalse("two-finger image release must not leave a drag preview",
                    controller.hasPendingImagePreview() || controller.isActive());
        } finally {
            source.stop();
            controller.destroy();
            if (!bitmap.isRecycled()) bitmap.recycle();
            if (!screenshot.isRecycled()) screenshot.recycle();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    private static PressOcrFlow ocrFlow(PressCaptureSource source) throws Exception {
        return (PressOcrFlow) field(source, "pressOcr").get(source);
    }

    private static void setOcrCandidate(PressCaptureSource source, Object pending) throws Exception {
        field(ocrFlow(source), "candidate").set(ocrFlow(source), pending);
    }

    private static Object pendingOcr(CapturedContent content) throws Exception {
        Class<?> type = Class.forName(PressOcrFlow.class.getName() + "$Candidate");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(
                CapturedContent.class, long.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(content, 1L, "test");
    }

    @Test
    public void twoFingerConfirmationUsesExistingBigBangTextHandoffAndStopReleasesListener()
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        PressCaptureSource source = null;
        long downTime = SystemClock.uptimeMillis();
        AtomicBoolean accepted = new AtomicBoolean();
        try {
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            source = new PressCaptureSource(context, input, controller, settings);
            field(source, "nodeSession").set(source, provider.connection);
            BackgroundTouchBlocker originalBlocker =
                    (BackgroundTouchBlocker) field(source, "blocker").get(source);
            originalBlocker.close();
            field(source, "blocker").set(source, new BackgroundTouchBlocker(
                    context, (target, method, args) -> null, () -> false));
            PressCaptureSource activeSource = source;
            new Handler(Looper.getMainLooper()).post(() -> {
                activeSource.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime);
                accepted.set(activeSource.onContactEvent(MotionEvent.ACTION_POINTER_DOWN,
                        20f, 30f, 40f, 50f, 2, downTime + 100L));
            });
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(accepted.get());
            field(source, "cached").set(source, CapturedContent.text("selected text", "test.source", null));

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(700));
            ShadowLooper.shadowMainLooper().idle();
            assertNotNull("confirmed hold opens BigBang before physical release",
                    provider.bigBangRequest);
            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertTrue(detector.isFired());
            new Handler(Looper.getMainLooper()).post(() -> activeSource.onContactEvent(
                    MotionEvent.ACTION_POINTER_UP, 20f, 30f, 40f, 50f, 1, downTime + 701L));
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(detector.isActive());
            controller.acceptPointerEvent(MotionEvent.ACTION_UP, 20f, 30f,
                    downTime + 702L, "root");
            ShadowLooper.shadowMainLooper().idle();
            activeSource.onPointerEvent(MotionEvent.ACTION_UP, 20f, 30f, downTime + 702L);
            ShadowLooper.shadowMainLooper().idle();
            assertNotNull(provider.bigBangRequest);
            assertEquals("selected text", provider.bigBangRequest.getString("text"));
            assertFalse("two-finger release opens pure segmentation without a drag handle",
                    provider.bigBangRequest.getBoolean("drag_preview"));
            assertTrue(provider.bigBangRequest.getLong("press_confirmed_at") > 0L);
            assertEquals("confirmed two-finger hold opens BigBang exactly once",
                    1, provider.bigBangOpenCount);
            assertFalse(detector.isActive());
        } finally {
            if (source != null) source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
        assertNull(field(input, "contactListener").get(input));
    }

    @Test
    public void singleFingerTextShowsCancellableDragPreview() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        PressCaptureSource source = null;
        long downTime = SystemClock.uptimeMillis();
        try {
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            source = new PressCaptureSource(context, input, controller, settings);
            field(source, "nodeSession").set(source, provider.connection);
            BackgroundTouchBlocker originalBlocker =
                    (BackgroundTouchBlocker) field(source, "blocker").get(source);
            originalBlocker.close();
            field(source, "blocker").set(source, new BackgroundTouchBlocker(
                    context, (target, method, args) -> null, () -> false));
            PressCaptureSource activeSource = source;
            new Handler(Looper.getMainLooper()).post(() ->
                    activeSource.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime));
            ShadowLooper.shadowMainLooper().idle();
            field(source, "cached").set(source, CapturedContent.text("selected text", "test.source", null));

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(700));
            ShadowLooper.shadowMainLooper().idle();
            assertNull("single-finger duration must not open BigBang",
                    provider.bigBangRequest);
            assertTrue("single-finger text shows a cancellable drag preview",
                    controller.hasPendingImagePreview());
            new Handler(Looper.getMainLooper()).post(() ->
                    activeSource.onPointerEvent(MotionEvent.ACTION_UP, 20f, 30f, downTime + 701L));
            ShadowLooper.shadowMainLooper().idle();
            assertEquals("single-finger release does not open BigBang",
                    0, provider.bigBangOpenCount);
            assertFalse("single-finger release cancels the drag preview",
                    controller.hasPendingImagePreview());
        } finally {
            if (source != null) source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void releasingConfirmedTwoFingerOcrKeepsAsyncTextHandoffAlive() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        AtomicReference<AicrOcrClient.Callback> ocrCallback = new AtomicReference<>();
        AtomicBoolean ocrCancelled = new AtomicBoolean();
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings,
                (ocrContext, bitmap, callback) -> {
                    ocrCallback.set(callback);
                    return () -> ocrCancelled.set(true);
                });
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            BackgroundTouchBlocker originalBlocker =
                    (BackgroundTouchBlocker) field(source, "blocker").get(source);
            originalBlocker.close();
            field(source, "blocker").set(source, new BackgroundTouchBlocker(
                    context, (target, method, args) -> null, () -> false));
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();

            AtomicBoolean accepted = new AtomicBoolean();
            new Handler(Looper.getMainLooper()).post(() -> {
                source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime);
                accepted.set(source.onContactEvent(MotionEvent.ACTION_POINTER_DOWN,
                        20f, 30f, 40f, 50f, 2, downTime + 100L));
                fieldUnchecked(source, "cached", CapturedContent.pressOcrRegion(
                        bitmap, "com.tencent.mm", new Rect()));
            });
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(accepted.get());

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(700));
            ShadowLooper.shadowMainLooper().idle();
            assertNotNull("OCR launches at two-finger confirmation", ocrCallback.get());

            new Handler(Looper.getMainLooper()).post(() -> source.onContactEvent(
                    MotionEvent.ACTION_POINTER_UP, 20f, 30f, 40f, 50f, 1, downTime + 701L));
            ShadowLooper.shadowMainLooper().idle();
            assertFalse(ocrCancelled.get());
            assertNull(provider.bigBangRequest);
            controller.acceptPointerEvent(MotionEvent.ACTION_UP, 20f, 30f,
                    downTime + 702L, "root");
            ShadowLooper.shadowMainLooper().idle();
            source.onPointerEvent(MotionEvent.ACTION_UP, 20f, 30f, downTime + 702L);
            ShadowLooper.shadowMainLooper().idle();
            assertNotNull(ocrCallback.get());
            assertFalse(ocrCancelled.get());

            new Handler(Looper.getMainLooper()).post(() ->
                    ocrCallback.get().onComplete("recognized after release", null));
            ShadowLooper.shadowMainLooper().idle();

            assertNotNull(provider.bigBangRequest);
            assertEquals("recognized after release", provider.bigBangRequest.getString("text"));
            assertTrue(bitmap.isRecycled());
        } finally {
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            if (!bitmap.isRecycled()) bitmap.recycle();
            clearPortalSettings(context);
        }
        assertNull(field(input, "contactListener").get(input));
    }

    @Test
    public void trueTwoFingerCancelBeforeReleaseDoesNotLaunchOcr() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        AtomicReference<AicrOcrClient.Callback> ocrCallback = new AtomicReference<>();
        AtomicBoolean ocrCancelled = new AtomicBoolean();
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings,
                (ocrContext, bitmap, callback) -> {
                    ocrCallback.set(callback);
                    return () -> ocrCancelled.set(true);
                });
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            BackgroundTouchBlocker originalBlocker =
                    (BackgroundTouchBlocker) field(source, "blocker").get(source);
            originalBlocker.close();
            field(source, "blocker").set(source, new BackgroundTouchBlocker(
                    context, (target, method, args) -> null, () -> false));
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() -> {
                source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime);
                source.onContactEvent(MotionEvent.ACTION_POINTER_DOWN,
                        20f, 30f, 40f, 50f, 2, downTime + 100L);
                fieldUnchecked(source, "cached", CapturedContent.pressOcrRegion(
                        bitmap, "com.tencent.mm", new Rect()));
            });
            ShadowLooper.shadowMainLooper().idle();
            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(700));
            ShadowLooper.shadowMainLooper().idle();
            assertNotNull("the confirmed press launches OCR before release", ocrCallback.get());

            new Handler(Looper.getMainLooper()).post(() -> source.onContactEvent(
                    MotionEvent.ACTION_CANCEL, 20f, 30f, Float.NaN, Float.NaN, 0, downTime + 701L));
            ShadowLooper.shadowMainLooper().idle();
            assertTrue("cancelling the confirmed press cancels its OCR request",
                    ocrCancelled.get());

            assertNull(provider.bigBangRequest);
        } finally {
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            if (!bitmap.isRecycled()) bitmap.recycle();
            clearPortalSettings(context);
        }
        assertNull(field(input, "contactListener").get(input));
    }

    @Test
    public void rootPreviewAlreadyActiveRejectsSecondFingerBeforeCandidateDeadline() {
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .build();
        DragShareController controller = new DragShareController(
                RuntimeEnvironment.getApplication(), OverlayWindowPolicy.portal());
        PressTriggerDetector detector = new PressTriggerDetector();
        detector.start(20f, 30f, 100L, 600L);
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        try {
            assertTrue(PressCaptureSource.canStartTwoFingerCandidate(controller, detector,
                    200L, 20f, 30f, 40f, 50f, false));

            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, 100L, "root");
            ShadowLooper.shadowMainLooper().idle();
            controller.handoffToPress(100L);
            controller.showContentForPressTakeover(
                    CapturedContent.image(bitmap, "com.tencent.mm", new Rect(), true),
                    20f, 30f, settings, 100L);
            ShadowLooper.shadowMainLooper().idle();

            assertTrue(controller.isActive());
            assertFalse(PressCaptureSource.canStartTwoFingerCandidate(controller, detector,
                    200L, 20f, 30f, 40f, 50f, false));
        } finally {
            controller.destroy();
            if (!bitmap.isRecycled()) bitmap.recycle();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    private static java.lang.reflect.Field field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field result = type.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void fieldUnchecked(Object target, String name, Object value) {
        try {
            field(target, name).set(target, value);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static void clearPortalSettings(Context context) {
        Settings.System.putString(context.getContentResolver(),
                "support_double_press_mode", null);
        Settings.System.putString(context.getContentResolver(),
                "support_long_press_mode", null);
    }

    private static final class BigBangProvider extends ContentProvider {
        volatile Bundle captureResponse;
        volatile Bundle pressRegionResponse;
        volatile CountDownLatch captureStarted;
        volatile CountDownLatch allowCaptureReturn;
        volatile CountDownLatch regionCaptureReturned;
        final IRootNodeLease lease = new IRootNodeLease.Stub() {
            @Override public Bundle capture(float x, float y, String[] blocked, boolean text,
                                            boolean images, boolean press, boolean early,
                                            IRootNodeCandidate callback) throws RemoteException {
                CountDownLatch started = captureStarted;
                if (started != null) started.countDown();
                CountDownLatch release = allowCaptureReturn;
                if (release != null) {
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new RemoteException("timed out waiting for test capture release");
                        }
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new RemoteException("test capture interrupted: " + error.getMessage());
                    }
                }
                return captureResponse;
            }
            @Override public Bundle insertTextAt(float x, float y, String text) { return null; }
            @Override public Bundle verifyWindow(String source, int windowId, Bundle bounds) { return bounds; }
            @Override public Bundle captureOcrRegion(String source, int windowId, Bundle bounds) {
                Bundle response = pressRegionResponse;
                if (response != null && regionCaptureReturned != null) {
                    regionCaptureReturned.countDown();
                    return response;
                }
                return bounds;
            }
            @Override public Bundle captureImage(String source, int windowId, Bundle bounds) { return bounds; }
            @Override public boolean verifySource(String source) { return true; }
            @Override public boolean isOpen() { return true; }
            @Override public void close() { }
        };
        final SharedRootNodeCapture.Connection connection = new SharedRootNodeCapture.Connection(
                RuntimeEnvironment.getApplication(), new Binder(), 10000, lease);
        Bundle bigBangRequest;
        int bigBangOpenCount;

        BigBangProvider(Context context) {
            ProviderInfo info = new ProviderInfo();
            info.authority = ImageStagingClient.AUTHORITY;
            attachInfo(context, info);
        }

        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] args, String order) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }

        @Override public Bundle call(String method, String arg, Bundle extras) {
            if (SharedRootNodeCapture.METHOD_OPEN.equals(method)) {
                Bundle result = new Bundle();
                result.putBinder("lease", lease.asBinder());
                return result;
            }
            if (BigBangOverlayLauncher.METHOD_OPEN.equals(method)) {
                bigBangOpenCount++;
                bigBangRequest = new Bundle(extras);
                Bundle result = new Bundle();
                result.putBoolean("accepted", true);
                return result;
            }
            return Bundle.EMPTY;
        }
    }
}
