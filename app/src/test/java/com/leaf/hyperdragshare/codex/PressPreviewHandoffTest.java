package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.os.Bundle;
import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class PressPreviewHandoffTest {
    @Test public void nativeImageWithoutPhysicalRootGestureDoesNotCreateAnOrphanPreview() throws Exception {
        DragShareController controller = controller();
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        controller.reservePortalHostFloatWindowSuppression();
        controller.show(CapturedContent.portalMediaImage(bitmap, "test.app", null),
                20f, 30f, DragShareSettings.defaults());
        ShadowLooper.shadowMainLooper().idle();
        assertNull(field(controller, "session").get(controller));
        assertFalse(controller.isActive());
        assertFalse(controller.shouldSuppressPortalHostFloatWindow());
        assertTrue(bitmap.isRecycled());
        controller.destroy();
    }

    @Test public void nativePreviewDoesNotCancelItsOwnPendingPress() throws Exception {
        DragShareController controller = controller();
        nativeSession(controller, 100L);

        assertTrue(controller.canContinuePress(100L));
        assertFalse(controller.canContinuePress(200L));
        assertFalse(controller.canContinuePress(-1L));
        controller.destroy();
    }

    @Test public void acceptedPressRemovesOnlyItsPreviewAndWaitsForPhysicalRelease() throws Exception {
        DragShareController controller = controller();
        down(controller, 100L);
        Object session = nativeSession(controller, 100L);
        controller.handoffToPress(100L);

        assertFalse(controller.isActive());
        assertTrue((boolean) field(session, "cancelled").get(session));
        assertTrue(controller.shouldSuppressPortalHostFloatWindow());
        int[] callbacks = {0};
        controller.acceptPointerEvent(MotionEvent.ACTION_UP, 20f, 30f, 900L, "root", () -> {
            assertTrue(controller.isAwaitingRootRelease());
            callbacks[0]++;
        });
        ShadowLooper.shadowMainLooper().idle();

        assertEquals(1, callbacks[0]);
        assertFalse(controller.isAwaitingRootRelease());
        assertFalse(controller.shouldSuppressPortalHostFloatWindow());
        controller.destroy();
    }

    @Test public void confirmedPressOwnsHostWindowUntilHandoffOrRootRelease() {
        DragShareController controller = controller();
        down(controller, 100L);
        controller.notePressPipeline(100L);
        assertTrue(controller.isAwaitingRootRelease());
        assertTrue(controller.shouldSuppressPortalHostFloatWindow());

        controller.endPressPipeline(100L);
        assertFalse(controller.isAwaitingRootRelease());
        controller.notePressPipeline(100L);
        controller.acceptPointerEvent(MotionEvent.ACTION_UP, 20f, 30f, 400L, "root");
        ShadowLooper.shadowMainLooper().idle();
        assertFalse(controller.isAwaitingRootRelease());
        controller.destroy();
    }

    @Test public void queuedNativeStartCannotReopenAfterEarlyAreaHandoff() throws Exception {
        DragShareController controller = controller();
        down(controller, 100L);
        Thread host = new Thread(() -> controller.show(
                CapturedContent.text("test", "test.app", null), 20f, 30f, DragShareSettings.defaults()));
        host.start();
        host.join();
        controller.handoffToPress(100L);
        ShadowLooper.shadowMainLooper().idle();

        assertFalse(controller.isActive());
        assertTrue(controller.isAwaitingRootRelease());
        assertEquals(null, field(controller, "session").get(controller));
        controller.destroy();
    }

    @Test public void rootMultitouchCancelFlushesCleanupWithoutWaitingForAnUp() throws Exception {
        for (boolean handoff : new boolean[]{false, true}) {
            DragShareController controller = controller();
            down(controller, 100L);
            nativeSession(controller, 100L);
            if (handoff) controller.handoffToPress(100L);
            int[] callbacks = {0};
            controller.acceptPointerEvent(MotionEvent.ACTION_CANCEL, 20f, 30f, 400L, "root",
                    () -> callbacks[0]++);
            ShadowLooper.shadowMainLooper().idle();

            assertEquals(1, callbacks[0]);
            assertFalse(controller.isActive());
            assertFalse(controller.isAwaitingRootRelease());
            controller.destroy();
        }
    }

    @Test public void oldPressCannotTakeOverTheNextGesture() throws Exception {
        DragShareController controller = controller();
        down(controller, 100L);
        controller.handoffToPress(100L);
        // Root receives the next DOWN before the press source's main-thread callback.
        controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, 1000L, "root");
        assertFalse(controller.canContinuePress(100L));
        ShadowLooper.shadowMainLooper().idle();
        nativeSession(controller, 1000L);
        controller.handoffToPress(100L);

        assertTrue(controller.isActive());
        assertTrue(controller.canContinuePress(1000L));
        controller.destroy();
    }

    @Test public void samePressImageTakeoverWaitsForThePhysicalRootUp() throws Exception {
        DragShareController controller = controller();
        down(controller, 100L);
        controller.handoffToPress(100L);
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        controller.showContentForPressTakeover(
                CapturedContent.image(bitmap, "com.tencent.mm", new Rect(), true),
                20f, 30f, DragShareSettings.defaults(), 100L);
        ShadowLooper.shadowMainLooper().idle();

        assertEquals(100L, field(controller, "rootDownTime").getLong(controller));
        assertEquals(-1L, field(controller, "pressHandoffTime").getLong(controller));
        controller.acceptPointerEvent(MotionEvent.ACTION_UP, 20f, 30f, 400L, "root");
        ShadowLooper.shadowMainLooper().idle();
        assertEquals(-1L, field(controller, "rootDownTime").getLong(controller));
        controller.destroy();
        if (!bitmap.isRecycled()) bitmap.recycle();
    }

    @Test public void staleImageTakeoverRecyclesOwnedBitmapWithoutEndingNextPress() throws Exception {
        DragShareController controller = controller();
        down(controller, 100L);
        controller.handoffToPress(100L);
        controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, 1000L, "root");
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        controller.showContentForPressTakeover(
                CapturedContent.image(bitmap, "com.tencent.mm", new Rect(), true),
                20f, 30f, DragShareSettings.defaults(), 100L);
        ShadowLooper.shadowMainLooper().idle();

        assertTrue(bitmap.isRecycled());
        assertEquals(1000L, field(controller, "rootDownTime").getLong(controller));
        assertEquals(-1L, field(controller, "pressHandoffTime").getLong(controller));
        controller.destroy();
    }

    @Test public void portalImageReplacesActiveRootImageWhenNoOcrIsPending() throws Exception {
        DragShareController controller = controller();
        down(controller, 100L);
        Object rootSession = nativeSession(controller, 100L);
        PressCaptureSource source = new PressCaptureSource(
                RuntimeEnvironment.getApplication(), null, controller, DragShareSettings.defaults());
        field(source, "pointerDown").setBoolean(source, true);
        field(source, "downTime").setLong(source, 100L);
        ((PressTriggerDetector) field(source, "detector").get(source)).start(20f, 30f, 100L, 500L);
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        CapturedContent content = CapturedContent.portalMediaImage(
                bitmap, "com.tencent.mm", new Rect(0, 0, 2, 2));

        try {
            assertTrue(source.acceptWeChatPortalContent(content));
            assertTrue((boolean) field(rootSession, "cancelled").get(rootSession));
        } finally {
            source.stop();
            controller.destroy();
            if (!bitmap.isRecycled()) bitmap.recycle();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void verifiedWeChatThumbnailCanEnterPreviewForCurrentRootPress() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        registerShareProvider(context);
        DragShareController controller = controller();
        down(controller, 100L);
        PressCaptureSource source = new PressCaptureSource(
                context, null, controller, DragShareSettings.defaults());
        field(source, "pointerDown").setBoolean(source, true);
        field(source, "downTime").setLong(source, 100L);
        Bitmap bitmap = Bitmap.createBitmap(3, 4, Bitmap.Config.ARGB_8888);
        CapturedContent thumbnail = CapturedContent.nodeThumbnailImage(
                bitmap, "com.tencent.mm", new Rect(10, 20, 300, 500));

        try {
            assertTrue(thumbnail.isWeChatThumbnail());
            java.lang.reflect.Method openImage = PressCaptureSource.class.getDeclaredMethod(
                    "openImage", long.class, CapturedContent.class);
            openImage.setAccessible(true);
            openImage.invoke(source, 8L, thumbnail);
            ShadowLooper.shadowMainLooper().idle();

            Object session = field(controller, "session").get(controller);
            assertNotNull(session);
            CapturedContent shown = (CapturedContent) field(session, "payload").get(session);
            assertTrue(shown.isWeChatThumbnail());
            assertTrue(controller.isActive());
            assertFalse(bitmap.isRecycled());
        } finally {
            source.stop();
            controller.destroy();
            if (!bitmap.isRecycled()) bitmap.recycle();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void releasedPressRejectsAndRecyclesLateWeChatThumbnail() throws Exception {
        ShadowLog.clear();
        DragShareController controller = controller();
        down(controller, 100L);
        PressCaptureSource source = new PressCaptureSource(
                RuntimeEnvironment.getApplication(), null, controller, DragShareSettings.defaults());
        field(source, "pointerDown").setBoolean(source, false);
        field(source, "downTime").setLong(source, 100L);
        Bitmap bitmap = Bitmap.createBitmap(3, 4, Bitmap.Config.ARGB_8888);
        CapturedContent thumbnail = CapturedContent.nodeThumbnailImage(
                bitmap, "com.tencent.mm", new Rect(10, 20, 300, 500));

        try {
            java.lang.reflect.Method openImage = PressCaptureSource.class.getDeclaredMethod(
                    "openImage", long.class, CapturedContent.class);
            openImage.setAccessible(true);
            openImage.invoke(source, 8L, thumbnail);
            ShadowLooper.shadowMainLooper().idle();

            assertNull(field(controller, "session").get(controller));
            assertTrue(bitmap.isRecycled());
            assertTrue(operationMessages().contains("stage=image_decision outcome=pointer_released"));
        } finally {
            source.stop();
            controller.destroy();
            if (!bitmap.isRecycled()) bitmap.recycle();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void actualPortalImageReplacesAnActiveThumbnailPreview() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        registerShareProvider(context);
        DragShareController controller = controller();
        down(controller, 100L);
        Bitmap thumbnailBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        CapturedContent thumbnail = CapturedContent.nodeThumbnailImage(
                thumbnailBitmap, "com.tencent.mm", new Rect(0, 0, 2, 2));
        Object thumbnailSession = nativeSession(controller, 100L, thumbnail);
        PressCaptureSource source = new PressCaptureSource(
                context, null, controller, DragShareSettings.defaults());
        field(source, "pointerDown").setBoolean(source, true);
        field(source, "downTime").setLong(source, 100L);
        ((PressTriggerDetector) field(source, "detector").get(source)).start(20f, 30f, 100L, 500L);
        Bitmap portalBitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888);
        CapturedContent portal = CapturedContent.portalMediaImage(
                portalBitmap, "com.tencent.mm", new Rect(0, 0, 3, 3));

        try {
            assertTrue(source.acceptWeChatPortalContent(portal));
            ShadowLooper.shadowMainLooper().idle();

            assertTrue((boolean) field(thumbnailSession, "cancelled").get(thumbnailSession));
            Object activeSession = field(controller, "session").get(controller);
            assertNotNull(activeSession);
            CapturedContent shown = (CapturedContent) field(activeSession, "payload").get(activeSession);
            assertTrue(shown.imageOrigin == CapturedContent.ImageOrigin.PORTAL_MEDIA);
            assertTrue(shown.bitmap == portalBitmap);
        } finally {
            source.stop();
            controller.destroy();
            if (!thumbnailBitmap.isRecycled()) thumbnailBitmap.recycle();
            if (!portalBitmap.isRecycled()) portalBitmap.recycle();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void actualPortalImageStillReplacesRecognizedWeChatOcrText() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        registerShareProvider(context);
        DragShareController controller = controller();
        down(controller, 100L);
        PressCaptureSource source = new PressCaptureSource(
                context, null, controller, DragShareSettings.defaults());
        Bitmap screenshot = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888);
        Bitmap portalImage = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        CapturedContent region = CapturedContent.pressOcrRegion(
                screenshot, "com.tencent.mm", new Rect(10, 20, 300, 500));
        Object pending = pendingWeChat(region, 5L, true, "识别出的当前文字");
        field(source, "pointerDown").setBoolean(source, true);
        field(source, "downTime").setLong(source, 100L);
        field(source, "confirmedAt").setLong(source, 600L);
        field(source, "generation").setLong(source, 5L);
        setCandidate(source, pending);
        ((PressTriggerDetector) field(source, "detector").get(source))
                .start(20f, 30f, 100L, 500L);
        CapturedContent actualPortalImage = CapturedContent.portalMediaImage(
                portalImage, "com.tencent.mm", new Rect(10, 20, 200, 220));

        try {
            assertTrue(source.acceptWeChatPortalContent(actualPortalImage));
            ShadowLooper.shadowMainLooper().idle();

            Object session = field(controller, "session").get(controller);
            CapturedContent payload = (CapturedContent) field(session, "payload").get(session);
            assertEquals(CapturedContent.Kind.IMAGE, payload.kind);
            assertTrue(payload.bitmap == portalImage);
            assertTrue(screenshot.isRecycled());
            assertFalse(portalImage.isRecycled());
        } finally {
            source.stop();
            controller.destroy();
            if (!screenshot.isRecycled()) screenshot.recycle();
            if (!portalImage.isRecycled()) portalImage.recycle();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void completedWeChatOcrTextOpensSegmentationWindowOnRelease() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        BigBangWindowProvider provider = new BigBangWindowProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareController controller = controller();
        down(controller, 100L);
        PressCaptureSource source = new PressCaptureSource(
                context, null, controller, DragShareSettings.defaults());
        Bitmap screenshot = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888);
        CapturedContent region = CapturedContent.pressOcrRegion(
                screenshot, "com.tencent.mm", new Rect(10, 20, 300, 500));
        Object pending = pendingWeChat(region, 4L, false, "识别出的当前文字");
        field(source, "pointerDown").setBoolean(source, false);
        field(source, "released").setBoolean(source, true);
        field(source, "releaseMode").set(source, PressReleaseMode.Mode.DRAG_PREVIEW);
        field(source, "downTime").setLong(source, 100L);
        field(source, "confirmedAt").setLong(source, 600L);
        field(source, "generation").setLong(source, 4L);
        setCandidate(source, pending);

        try {
            flowOf(source).finishPress();
            ShadowLooper.shadowMainLooper().idle();

            assertNotNull(provider.bigBangRequest);
            assertEquals("识别出的当前文字", provider.bigBangRequest.getString("text"));
            assertTrue(provider.bigBangRequest.getBoolean("drag_preview"));
            assertTrue(screenshot.isRecycled());
            assertNull(candidateOf(source));
        } finally {
            source.stop();
            controller.destroy();
            if (!screenshot.isRecycled()) screenshot.recycle();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void directImageDragTakeoverStartsSessionWhileHolding() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        DragShareController controller = controller();
        down(controller, 100L);
        PressCaptureSource source = new PressCaptureSource(
                context, null, controller, DragShareSettings.defaults());
        Bitmap portalBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        CapturedContent portalMediaImage = CapturedContent.portalMediaImage(
                portalBitmap, "com.tencent.mm", new Rect(0, 0, 4, 4));
        PressTriggerDetector detector =
                (PressTriggerDetector) field(source, "detector").get(source);
        detector.start(20f, 30f, 100L, 500L);
        field(source, "pointerDown").setBoolean(source, true);
        field(source, "downTime").setLong(source, 100L);
        field(source, "directDragArmed").setBoolean(source, true);
        field(source, "twoFingerModeForGesture").setBoolean(source, false);

        try {
            assertTrue(detector.fire(700L));
            java.lang.reflect.Method openImage = PressCaptureSource.class.getDeclaredMethod(
                    "openImage", long.class, CapturedContent.class);
            openImage.setAccessible(true);
            openImage.invoke(source, 4L, portalMediaImage);
            ShadowLooper.shadowMainLooper().idle();

            Object session = field(controller, "session").get(controller);
            assertNotNull("confirmed hold moving into drag starts the shared session", session);
            CapturedContent payload =
                    (CapturedContent) field(session, "payload").get(session);
            assertTrue(payload.bitmap == portalBitmap);
            assertFalse(portalBitmap.isRecycled());
        } finally {
            source.stop();
            controller.destroy();
            if (!portalBitmap.isRecycled()) portalBitmap.recycle();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void accessibilityImageScreenshotIsNotPresentedAsAWeChatOriginal() throws Exception {
        DragShareLog.configure(DragShareSettings.defaults());
        ShadowLog.clear();
        DragShareController controller = controller();
        down(controller, 100L);
        PressCaptureSource source = new PressCaptureSource(
                RuntimeEnvironment.getApplication(), null, controller, DragShareSettings.defaults());
        Bitmap screenshot = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888);
        CapturedContent candidate = CapturedContent.screenCaptureImage(
                screenshot, "com.tencent.mm", new Rect(10, 20, 300, 500));

        try {
            java.lang.reflect.Method openImage = PressCaptureSource.class.getDeclaredMethod(
                    "openImage", long.class, CapturedContent.class);
            openImage.setAccessible(true);
            openImage.invoke(source, 8L, candidate);
            ShadowLooper.shadowMainLooper().idle();

            assertNull(field(controller, "session").get(controller));
            assertTrue(screenshot.isRecycled());
            assertTrue(operationMessages().contains(
                    "stage=image_drag outcome=rejected_image_candidate_screen_capture"));
            assertFalse(operationMessages().contains("com.tencent.mm"));
        } finally {
            source.stop();
            controller.destroy();
            if (!screenshot.isRecycled()) screenshot.recycle();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void unclassifiedWeChatImageIsNotPresentedAsAnOriginal() throws Exception {
        DragShareController controller = controller();
        down(controller, 100L);
        PressCaptureSource source = new PressCaptureSource(
                RuntimeEnvironment.getApplication(), null, controller, DragShareSettings.defaults());
        Bitmap bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888);
        CapturedContent candidate = CapturedContent.image(
                bitmap, "com.tencent.mm", new Rect(10, 20, 300, 500), true);

        try {
            java.lang.reflect.Method openImage = PressCaptureSource.class.getDeclaredMethod(
                    "openImage", long.class, CapturedContent.class);
            openImage.setAccessible(true);
            openImage.invoke(source, 8L, candidate);
            ShadowLooper.shadowMainLooper().idle();

            assertNull(field(controller, "session").get(controller));
            assertTrue(bitmap.isRecycled());
        } finally {
            source.stop();
            controller.destroy();
            if (!bitmap.isRecycled()) bitmap.recycle();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void portalMediaAndExplicitWeChatThumbnailHaveDistinctTrustedOrigins() {
        Bitmap portalImage = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        Bitmap rootImage = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        Bitmap unknownImage = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        Bitmap thumbnailImage = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        Bitmap otherThumbnailImage = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        try {
            CapturedContent portal = CapturedContent.portalMediaImage(
                    portalImage, "com.tencent.mm", new Rect(0, 0, 2, 2));
            CapturedContent root = CapturedContent.screenCaptureImage(
                    rootImage, "com.tencent.mm", new Rect(0, 0, 2, 2));
            CapturedContent thumbnail = CapturedContent.nodeThumbnailImage(
                    thumbnailImage, "com.tencent.mm", new Rect(0, 0, 2, 2));

            assertEquals(CapturedContent.ImageOrigin.PORTAL_MEDIA, portal.imageOrigin);
            assertEquals(CapturedContent.ImageOrigin.SCREEN_CAPTURE, root.imageOrigin);
            assertEquals(CapturedContent.ImageOrigin.NODE_THUMBNAIL, thumbnail.imageOrigin);
            assertTrue(thumbnail.isWeChatThumbnail());
            assertFalse(CapturedContent.nodeThumbnailImage(
                    otherThumbnailImage, "other.app", new Rect()).isWeChatThumbnail());
            assertEquals(CapturedContent.ImageOrigin.UNKNOWN,
                    CapturedContent.image(unknownImage, "com.tencent.mm", new Rect(), true).imageOrigin);
        } finally {
            if (!portalImage.isRecycled()) portalImage.recycle();
            if (!rootImage.isRecycled()) rootImage.recycle();
            if (!unknownImage.isRecycled()) unknownImage.recycle();
            if (!thumbnailImage.isRecycled()) thumbnailImage.recycle();
            if (!otherThumbnailImage.isRecycled()) otherThumbnailImage.recycle();
        }
    }

    @Test public void portalImageCallbackFromOldDownTimeIsRejectedByPressSource() throws Exception {
        DragShareController controller = controller();
        down(controller, 100L);
        controller.handoffToPress(100L);
        controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, 1000L, "root");
        ShadowLooper.shadowMainLooper().idle();
        PressCaptureSource source = new PressCaptureSource(
                RuntimeEnvironment.getApplication(), null, controller, DragShareSettings.defaults());
        field(source, "pointerDown").setBoolean(source, true);
        field(source, "downTime").setLong(source, 100L);
        ((PressTriggerDetector) field(source, "detector").get(source)).start(20f, 30f, 100L, 500L);
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        CapturedContent content = CapturedContent.image(
                bitmap, "com.tencent.mm", new Rect(0, 0, 2, 2), true);

        try {
            assertFalse(source.acceptWeChatPortalContent(content));
            assertTrue(controller.canContinuePress(1000L));
        } finally {
            source.stop();
            controller.destroy();
            if (!bitmap.isRecycled()) bitmap.recycle();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    private static DragShareController controller() {
        return new DragShareController(RuntimeEnvironment.getApplication(), OverlayWindowPolicy.portal());
    }

    private static void down(DragShareController controller, long time) {
        controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, time, "root");
        ShadowLooper.shadowMainLooper().idle();
    }

    // Establish the passive native preview state without attaching real overlay windows.
    private static Object nativeSession(DragShareController controller, long downTime) throws Exception {
        return nativeSession(controller, downTime,
                CapturedContent.text("test", "test.app", null));
    }

    private static Object nativeSession(DragShareController controller, long downTime,
                                        CapturedContent payload) throws Exception {
        Class<?> type = Class.forName(DragShareController.class.getName() + "$Session");
        var constructor = type.getDeclaredConstructor(CapturedContent.class, long.class, String.class);
        constructor.setAccessible(true);
        Object session = constructor.newInstance(payload, downTime, OperationTrace.id("press", downTime));
        field(controller, "session").set(controller, session);
        field(controller, "active").set(controller, true);
        return session;
    }

    private static Object pendingWeChat(CapturedContent content, long request, boolean textWindowAccepted,
                                        String text) throws Exception {
        Class<?> type = Class.forName(WeChatPressFlow.class.getName() + "$Candidate");
        var constructor = type.getDeclaredConstructor(CapturedContent.class, long.class, String.class);
        constructor.setAccessible(true);
        Object pending = constructor.newInstance(content, request, OperationTrace.id("press", request));
        field(pending, "ocrComplete").setBoolean(pending, true);
        field(pending, "text").set(pending, text);
        field(pending, "textDelivered").setBoolean(pending, textWindowAccepted);
        field(pending, "textWindowAccepted").setBoolean(pending, textWindowAccepted);
        return pending;
    }

    private static WeChatPressFlow flowOf(PressCaptureSource source) throws Exception {
        return (WeChatPressFlow) field(source, "weChatPress").get(source);
    }

    private static Object candidateOf(PressCaptureSource source) throws Exception {
        return field(flowOf(source), "candidate").get(flowOf(source));
    }

    private static void setCandidate(PressCaptureSource source, Object pending) throws Exception {
        field(flowOf(source), "candidate").set(flowOf(source), pending);
    }

    private static void registerShareProvider(Context context) {
        ShareImageProvider provider = new ShareImageProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = ImageStagingClient.AUTHORITY;
        provider.attachInfo(context, info);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
    }

    private static Field field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static String operationMessages() {
        StringBuilder messages = new StringBuilder();
        for (ShadowLog.LogItem item : ShadowLog.getLogsForTag("DragShare/Operation")) {
            messages.append(item.msg).append('\n');
        }
        return messages.toString();
    }

    /** Accepts BigBang window opens so release-path tests can assert the delivered payload. */
    private static final class BigBangWindowProvider extends android.content.ContentProvider {
        Bundle bigBangRequest;

        BigBangWindowProvider(Context context) {
            ProviderInfo info = new ProviderInfo();
            info.authority = ImageStagingClient.AUTHORITY;
            attachInfo(context, info);
        }

        @Override public boolean onCreate() { return true; }
        @Override public android.database.Cursor query(android.net.Uri uri, String[] projection,
                String selection, String[] args, String order) { return null; }
        @Override public String getType(android.net.Uri uri) { return null; }
        @Override public android.net.Uri insert(android.net.Uri uri, android.content.ContentValues values) {
            return null;
        }
        @Override public int delete(android.net.Uri uri, String selection, String[] args) { return 0; }
        @Override public int update(android.net.Uri uri, android.content.ContentValues values,
                String selection, String[] args) { return 0; }

        @Override public Bundle call(String method, String arg, Bundle extras) {
            if (BigBangOverlayLauncher.METHOD_OPEN.equals(method)) {
                bigBangRequest = new Bundle(extras);
                Bundle result = new Bundle();
                result.putBoolean("accepted", true);
                return result;
            }
            return Bundle.EMPTY;
        }
    }

}
