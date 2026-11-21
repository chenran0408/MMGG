package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.MimeTypeMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowMimeTypeMap;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, shadows = {
        DragShareActionLifecycleTest.ImageStagingShadow.class,
        DragShareActionLifecycleTest.ClipboardWriterShadow.class,
        DragShareActionLifecycleTest.DragShareToastShadow.class
}, instrumentedPackages = "com.leaf.hyperdragshare.codex")
@LooperMode(LooperMode.Mode.PAUSED)
public final class DragShareActionLifecycleTest {
    private static final byte[] ORIGINAL_GIF = Base64.getDecoder().decode(
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");
    private DragShareController controller;

    @Before public void resetShadows() {
        ShadowLog.clear();
        ImageStagingShadow.callbacks.clear();
        ImageStagingShadow.revokedUris.clear();
        ClipboardWriterShadow.requests.clear();
        DragShareToastShadow.messages.clear();
        controller = new DragShareController(
                RuntimeEnvironment.getApplication(), OverlayWindowPolicy.portal());
    }

    @Test public void failedStageBeforeDropReportsFailureInsteadOfWaiting() throws Exception {
        Object session = startImageGesture(CapturedContent.image(bitmap(), "test.app", null, true), 105L);
        onlyStagingCallback().onFailure(new IOException("stage failed"));
        idleMain();
        set(controller, "selectedTarget", ShareTarget.copyImageToClipboard(null));
        invoke(controller, "finishGestureOnMain", new Class<?>[]{boolean.class}, true);

        assertNull(get(session, "pendingTarget"));
        assertTrue((boolean) get(session, "cancelled"));
        assertTrue(DragShareToastShadow.messages.contains("图片准备失败，请重新拖拽"));
        assertFalse(DragShareToastShadow.messages.contains("正在准备图片"));
        controller.destroy();
        idleMain();
    }

    @Test public void failedStageAfterDropStillReportsWhileAnotherGestureIsActive() throws Exception {
        Object old = startImageGesture(CapturedContent.image(bitmap(), "old.app", null, true), 106L);
        ImageStagingClient.Callback callback = onlyStagingCallback();
        set(controller, "selectedTarget", ShareTarget.copyImageToClipboard(null));
        invoke(controller, "finishGestureOnMain", new Class<?>[]{boolean.class}, true);
        Object current = startImageGesture(CapturedContent.image(bitmap(), "new.app", null, true), 107L);
        callback.onFailure(new IOException("stage failed"));
        idleMain();

        assertNull(get(old, "pendingTarget"));
        assertTrue(DragShareToastShadow.messages.contains("图片准备失败，请重新拖拽"));
        assertSame(current, get(controller, "session"));
        assertTrue(controller.isActive());
        controller.destroy();
        idleMain();
    }

    @Test public void finishWaitsForStageAndRepeatedCallbackExecutesOnce() throws Exception {
        Bitmap bitmap = bitmap();
        CapturedContent payload = CapturedContent.image(bitmap, "test.app", null, true);
        Object session = startImageGesture(payload, 101L);
        ImageStagingClient.Callback callback = onlyStagingCallback();
        ShareTarget target = ShareTarget.copyImageToClipboard(null);
        set(controller, "selectedTarget", target);

        invoke(controller, "finishGestureOnMain", new Class<?>[]{boolean.class}, true);

        assertFalse(controller.isActive());
        assertSame(target, get(session, "pendingTarget"));
        assertFalse((boolean) get(session, "cancelled"));
        assertTrue(ClipboardWriterShadow.requests.isEmpty());

        Uri staged = Uri.parse("content://com.leaf.hyperdragshare.codex.share/images/old");
        callback.onStaged(staged);
        callback.onStaged(staged);
        idleMain();

        assertEquals(1, ClipboardWriterShadow.requests.size());
        CopyImageRequest request = ClipboardWriterShadow.requests.get(0);
        assertEquals(staged, request.uri);
        assertSame(payload, get(session, "payload"));
        assertNull(get(session, "pendingTarget"));
        assertTrue((boolean) get(session, "cancelled"));
        assertFalse(bitmap.isRecycled());
        request.callback.onComplete(null);
        assertTrue(ImageStagingShadow.revokedUris.isEmpty());
        controller.destroy();
        idleMain();
    }

    private StashDragImageCacheTest.FakeDownloadsProvider installSaveDestination(String extension, String mime) {
        Context app = RuntimeEnvironment.getApplication();
        ShadowMimeTypeMap types = Shadow.extract(MimeTypeMap.getSingleton());
        types.addExtensionMimeTypeMapping(extension, mime);
        StashDragImageCacheTest.FakeDownloadsProvider media = new StashDragImageCacheTest.FakeDownloadsProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = MediaStore.AUTHORITY;
        media.attachInfo(app, info);
        ShadowContentResolver.registerProviderInternal(info.authority, media);
        return media;
    }

    @Test public void fastSaveWaitsForOriginalAndPublishesItsExactGifOnce() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        var media = installSaveDestination("gif", "image/gif");
        File staged = new File(app.getCacheDir(), "captured.gif");
        Files.write(staged.toPath(), ORIGINAL_GIF);
        try {
            CapturedContent payload = CapturedContent.referencedMediaImage(EncodedMedia.decode(ORIGINAL_GIF),
                    bitmap(), "test.app", null);
            Object session = startImageGesture(payload, 111L);
            ImageStagingClient.Callback callback = onlyStagingCallback();
            ShareTarget save = ShareTarget.saveToLocal(null);
            set(controller, "selectedTarget", save);
            invoke(controller, "finishGestureOnMain", new Class<?>[]{boolean.class}, true);
            assertSame(save, get(session, "pendingTarget"));
            assertEquals(0, media.insertCount);

            callback.onStaged(Uri.fromFile(staged));
            callback.onStaged(Uri.fromFile(staged));
            idleMain();
            assertTrue(media.published.await(3, TimeUnit.SECONDS));
            assertNull(get(session, "pendingTarget"));
            assertEquals(1, media.insertCount);
            assertEquals("image/gif", media.inserted.getAsString(MediaStore.MediaColumns.MIME_TYPE));
            assertTrue(media.inserted.getAsString(MediaStore.MediaColumns.DISPLAY_NAME).endsWith(".gif"));
            org.junit.Assert.assertArrayEquals(ORIGINAL_GIF, Files.readAllBytes(media.row(1).file.toPath()));
        } finally {
            controller.destroy();
            idleMain();
            media.clearRows();
            Files.deleteIfExists(staged.toPath());
        }
    }

    @Test public void preparedPreviewSavesTheExistingPngWithoutReencoding() throws Exception {
        checkPreviewPngReuse(true);
    }

    @Test public void fastPreviewSaveUsesThePendingPngWithoutReencoding() throws Exception {
        checkPreviewPngReuse(false);
    }

    private void checkPreviewPngReuse(boolean preparedBeforeDrop) throws Exception {
        byte[] stagedPng = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAMklEQVR4AQEnANj/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACcAAQoCwnQAAAAASUVORK5CYII=");
        var media = installSaveDestination("png", "image/png");
        File file = File.createTempFile("staged-", ".png", RuntimeEnvironment.getApplication().getCacheDir());
        Files.write(file.toPath(), stagedPng);
        try {
            Object session = startImageGesture(CapturedContent.image(bitmap(), "test.app", null, true), 113L);
            ImageStagingClient.Callback callback = onlyStagingCallback();
            if (preparedBeforeDrop) {
                callback.onStaged(Uri.fromFile(file));
                idleMain();
            }
            ShareTarget save = ShareTarget.saveToLocal(null);
            set(controller, "selectedTarget", save);
            invoke(controller, "finishGestureOnMain", new Class<?>[]{boolean.class}, true);
            if (!preparedBeforeDrop) {
                assertSame(save, get(session, "pendingTarget"));
                assertEquals(0, media.insertCount);
                callback.onStaged(Uri.fromFile(file));
                idleMain();
            }
            assertTrue(media.published.await(3, TimeUnit.SECONDS));
            assertEquals(1, media.insertCount);
            assertEquals("image/png", media.inserted.getAsString(MediaStore.MediaColumns.MIME_TYPE));
            org.junit.Assert.assertArrayEquals(stagedPng, Files.readAllBytes(media.row(1).file.toPath()));
        } finally {
            controller.destroy();
            idleMain();
            media.clearRows();
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test public void previewSaveAfterStageFailurePreservesPixels() throws Exception {
        var media = installSaveDestination("png", "image/png");
        Bitmap preview = bitmap();
        preview.setPixel(0, 0, android.graphics.Color.MAGENTA);
        try {
            Object session = startImageGesture(CapturedContent.image(preview, "test.app", null, true), 114L);
            set(controller, "selectedTarget", ShareTarget.saveToLocal(null));
            invoke(controller, "finishGestureOnMain", new Class<?>[]{boolean.class}, true);
            onlyStagingCallback().onFailure(new IOException("staging unavailable"));
            idleMain();
            assertTrue(media.published.await(3, TimeUnit.SECONDS));
            assertEquals(1, media.insertCount);
            byte[] encoded = Files.readAllBytes(media.row(1).file.toPath());
            Bitmap saved = android.graphics.BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
            assertNotNull(saved);
            assertEquals(android.graphics.Color.MAGENTA, saved.getPixel(0, 0));
            saved.recycle();
        } finally {
            controller.destroy();
            idleMain();
            media.clearRows();
        }
    }

    @Test public void failedOriginalStageAfterLocalDropDoesNotSaveThePreview() throws Exception {
        CapturedContent original = CapturedContent.referencedMediaImage(EncodedMedia.decode(ORIGINAL_GIF),
                bitmap(), "test.app", null);
        Object session = startImageGesture(original, 112L);
        set(controller, "selectedTarget", ShareTarget.saveToLocal(null));
        invoke(controller, "finishGestureOnMain", new Class<?>[]{boolean.class}, true);
        onlyStagingCallback().onFailure(new IOException("write failed"));
        idleMain();
        assertNull(get(session, "pendingTarget"));
        assertTrue(DragShareToastShadow.messages.contains("图片准备失败，请重新拖拽"));
        assertFalse(DragShareToastShadow.messages.contains("已保存到本地"));
        controller.destroy();
        idleMain();
    }

    @Test public void oldPendingImageUsesItsCapturedDataAfterNextGestureStarts() throws Exception {
        Bitmap oldBitmap = bitmap();
        CapturedContent oldPayload = CapturedContent.image(oldBitmap, "old.app", null, true);
        Object oldSession = startImageGesture(oldPayload, 201L);
        String oldOperation = (String) get(oldSession, "operationId");
        assertEquals(OperationTrace.id("press", 201L), oldOperation);
        ImageStagingClient.Callback oldCallback = onlyStagingCallback();
        ShareTarget oldTarget = ShareTarget.copyImageToClipboard(null);
        set(controller, "selectedTarget", oldTarget);
        invoke(controller, "finishGestureOnMain", new Class<?>[]{boolean.class}, true);
        assertSame(oldTarget, get(oldSession, "pendingTarget"));

        Bitmap newBitmap = bitmap();
        CapturedContent newPayload = CapturedContent.image(newBitmap, "new.app", null, true);
        Object newSession = startImageGesture(newPayload, 202L);
        String newOperation = (String) get(newSession, "operationId");
        assertFalse(newSession == oldSession);
        View newPreview = (View) get(controller, "previewView");
        Uri oldUri = Uri.parse("content://com.leaf.hyperdragshare.codex.share/images/captured-old");
        oldCallback.onStaged(oldUri);
        idleMain();

        assertEquals(1, ClipboardWriterShadow.requests.size());
        CopyImageRequest request = ClipboardWriterShadow.requests.get(0);
        assertEquals(oldUri, request.uri);
        assertSame(oldPayload, get(oldSession, "payload"));
        assertNull(get(oldSession, "pendingTarget"));
        assertTrue((boolean) get(oldSession, "cancelled"));
        assertFalse((boolean) get(newSession, "cancelled"));
        assertSame(newSession, get(controller, "session"));
        assertTrue(controller.isActive());
        assertSame(newPreview, get(controller, "previewView"));
        assertFalse(oldBitmap.isRecycled());
        assertFalse(newBitmap.isRecycled());
        request.callback.onComplete(new IOException("clipboard denied"));
        idleMain();
        String clipboardEvents = operationEvents("stage=clipboard");
        assertTrue(clipboardEvents.contains("op=" + oldOperation + " stage=clipboard outcome=failed_IOException"));
        assertFalse(clipboardEvents.contains("op=" + newOperation + " stage=clipboard outcome=failed_IOException"));
        assertFalse(clipboardEvents.contains(oldUri.toString()));
        assertFalse((boolean) get(newSession, "cancelled"));
        assertSame(newSession, get(controller, "session"));
        assertSame(newPreview, get(controller, "previewView"));
        assertTrue(controller.isActive());
        assertTrue(ImageStagingShadow.revokedUris.isEmpty());
        controller.destroy();
        idleMain();
    }

    @Test public void acceptedActionCanCompleteAfterControllerWindowDestroyed() throws Exception {
        Bitmap image = bitmap();
        Object session = startImageGesture(CapturedContent.image(image, "old.app", null, true), 211L);
        ImageStagingClient.Callback stage = onlyStagingCallback();
        set(controller, "selectedTarget", ShareTarget.copyImageToClipboard(null));
        invoke(controller, "finishGestureOnMain", new Class<?>[]{boolean.class}, true);
        Uri uri = Uri.parse("content://com.leaf.hyperdragshare.codex.share/shared/accepted-image");
        stage.onStaged(uri);
        idleMain();
        assertEquals(1, ClipboardWriterShadow.requests.size());
        controller.destroy();
        idleMain();
        ClipboardWriterShadow.requests.get(0).callback.onComplete(null);
        idleMain();
        assertTrue(operationEvents("stage=clipboard").contains("op=" + get(session, "operationId")
                + " stage=clipboard outcome=completed"));
        assertFalse(image.isRecycled());
        assertTrue(ImageStagingShadow.revokedUris.isEmpty());
        assertFalse(controller.isActive());
    }

    @Test public void cancelledOrDestroyedSessionIgnoresLateStagingResult() throws Exception {
        CapturedContent cancelledPayload = CapturedContent.image(
                bitmap(), "cancelled.app", null, true);
        Object cancelledSession = startImageGesture(cancelledPayload, 301L);
        ImageStagingClient.Callback cancelledCallback = onlyStagingCallback();
        invoke(controller, "cancelGestureOnMain", new Class<?>[0]);
        cancelledCallback.onStaged(Uri.parse(
                "content://com.leaf.hyperdragshare.codex.share/images/private-cancelled-token"));
        idleMain();
        assertTrue((boolean) get(cancelledSession, "cancelled"));
        assertTrue(ClipboardWriterShadow.requests.isEmpty());
        String lateEvents = operationEvents("stage=stage outcome=late_ignored");
        assertTrue(lateEvents.contains("op=" + get(cancelledSession, "operationId")));
        assertFalse(lateEvents.contains("private-cancelled-token"));

        CapturedContent destroyedPayload = CapturedContent.image(
                bitmap(), "destroyed.app", null, true);
        Object destroyedSession = startImageGesture(destroyedPayload, 302L);
        ImageStagingClient.Callback destroyedCallback =
                ImageStagingShadow.callbacks.get(ImageStagingShadow.callbacks.size() - 1);
        controller.destroy();
        idleMain();
        destroyedCallback.onStaged(Uri.parse(
                "content://com.leaf.hyperdragshare.codex.share/images/destroyed"));
        idleMain();

        assertTrue((boolean) get(destroyedSession, "cancelled"));
        assertTrue(ClipboardWriterShadow.requests.isEmpty());
    }

    @Test public void chosenImageStillReachesReceiverIfWindowClosesWhileStaging() throws Exception {
        Object accepted = startImageGesture(CapturedContent.image(bitmap(), "source", null, true), 311L);
        ImageStagingClient.Callback stage = onlyStagingCallback();
        set(controller, "selectedTarget", ShareTarget.copyImageToClipboard(null));
        invoke(controller, "finishGestureOnMain", new Class<?>[]{boolean.class}, true);
        controller.destroy();
        idleMain();
        assertFalse((boolean) get(accepted, "cancelled"));

        Uri source = Uri.parse("content://com.leaf.hyperdragshare.codex.share/shared/queued");
        stage.onStaged(source);
        stage.onStaged(source);
        idleMain();
        assertEquals(1, ClipboardWriterShadow.requests.size());
        assertEquals(source, ClipboardWriterShadow.requests.get(0).uri);
    }

    private Object startImageGesture(CapturedContent payload, long downTime) throws Exception {
        set(controller, "rootDownTime", downTime);
        invoke(controller, "showOnMain",
                new Class<?>[]{CapturedContent.class, float.class, float.class,
                        DragShareSettings.class, long.class},
                payload, 120f, 240f, DragShareSettings.defaults(), downTime);
        ShadowLooper.shadowMainLooper().idle();
        Object session = get(controller, "session");
        assertNotNull(session);
        assertTrue(controller.isActive());
        String operation = (String) get(session, "operationId");
        String showEvents = operationEvents("op=" + operation);
        assertTrue(showEvents.contains("stage=show outcome=accepted"));
        assertTrue(showEvents.contains("stage=preview outcome=attached"));
        return session;
    }

    private static ImageStagingClient.Callback onlyStagingCallback() {
        assertEquals(1, ImageStagingShadow.callbacks.size());
        return ImageStagingShadow.callbacks.get(0);
    }

    private static Bitmap bitmap() {
        return Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888);
    }

    private static void idleMain() {
        ShadowLooper.shadowMainLooper().idle();
    }

    private static String operationEvents(String contains) {
        StringBuilder result = new StringBuilder();
        for (ShadowLog.LogItem item : ShadowLog.getLogsForTag("DragShare/Operation")) {
            if (item.msg.contains(contains)) result.append(item.msg).append('\n');
        }
        return result.toString();
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static void invoke(Object object, String name, Class<?>[] parameterTypes,
                               Object... args) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        method.invoke(object, args);
    }

    @Implements(value = ImageStagingClient.class, isInAndroidSdk = false)
    public static final class ImageStagingShadow {
        static final List<ImageStagingClient.Callback> callbacks = new ArrayList<>();
        static final List<Uri> revokedUris = new ArrayList<>();

        @Implementation
        protected static java.util.concurrent.Future<?> stage(Context context, CapturedContent payload,
                                                              ImageStagingClient.Callback callback) {
            callbacks.add(callback);
            return null;
        }

        @Implementation
        protected static void revokeReadAccess(Context context, Uri uri, String packageName) {
            revokedUris.add(uri);
        }
    }

    @Implements(value = ClipboardWriter.class, isInAndroidSdk = false)
    public static final class ClipboardWriterShadow {
        static final List<CopyImageRequest> requests = new ArrayList<>();

        @Implementation
        protected static void copyImage(Context context, Uri image, ClipboardWriter.Callback callback) {
            requests.add(new CopyImageRequest(image, callback));
        }
    }

    @Implements(value = DragShareToast.class, isInAndroidSdk = false)
    public static final class DragShareToastShadow {
        static final List<String> messages = new ArrayList<>();
        @Implementation protected void show(String message) { messages.add(message); }
        @Implementation protected void show(String message, int duration) { messages.add(message); }
    }

    private static final class CopyImageRequest {
        final Uri uri;
        final ClipboardWriter.Callback callback;

        CopyImageRequest(Uri uri, ClipboardWriter.Callback callback) {
            this.uri = uri;
            this.callback = callback;
        }
    }
}
