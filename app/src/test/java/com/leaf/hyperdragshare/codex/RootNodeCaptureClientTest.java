package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.json.JSONObject;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowMimeTypeMap;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class RootNodeCaptureClientTest {
    private static final String OCCUPIED = "{\"ready\":false,\"error\":\"private detail\","
            + "\"error_code\":\"uiautomation_already_registered\"}\n";
    private static final String PERMISSION = "{\"ready\":false,\"error\":\"private detail\","
            + "\"error_code\":\"uiautomation_permission_denied\"}\n";
    private static final String READY = "{\"ready\":true}\n";
    private static final Rect IMAGE_BOUNDS = new Rect(11, 21, 61, 71);

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void originalOnlyResponseKeepsGifBytesWithoutRequestingScreenshot() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ShadowMimeTypeMap map = Shadow.extract(MimeTypeMap.getSingleton());
        map.addExtensionMimeTypeMapping("gif", "image/gif");
        ShareImageProvider provider = new ShareImageProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = ImageStagingClient.AUTHORITY;
        provider.attachInfo(context, info);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        byte[] gif = java.util.Base64.getDecoder().decode(
                "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");
        FakeProcess process = new FakeProcess(READY + originalReply(gif));

        try (RootNodeCaptureClient.Session session = RootNodeCaptureClient.open(context,
                RootConnectionDiagnostics.newAttempt(), command -> process)) {
            Bundle image = session.captureImage("example.chat", 14, IMAGE_BOUNDS);

            assertNotNull(image);
            assertEquals(IMAGE_BOUNDS, image.getParcelable("bounds", Rect.class));
            assertEquals("image/gif", image.getString("media_mime_type"));
            Uri uri = image.getParcelable("media_uri", Uri.class);
            assertNotNull(uri);
            try (var input = context.getContentResolver().openInputStream(uri)) {
                assertArrayEquals(gif, input.readAllBytes());
            }
            Bitmap preview = image.getParcelable("bitmap", Bitmap.class);
            assertNotNull(preview);
            preview.recycle();

            String[] requests = process.stdin.toString(StandardCharsets.UTF_8).split("\n");
            assertEquals(1, requests.length);
            assertTrue(new JSONObject(requests[0]).getBoolean("prefer_original"));
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void invalidOriginalFallsBackToOneVerifiedScreenshotRequest() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String fallback = imageReply().put("image_base64",
                "iVBORw0KGgoAAAANSUhEUgAAAAIAAAAGCAYAAADpJ08yAAAADElEQVR4nGNgIAcAAAA2AAG2dLktAAAAAElFTkSuQmCC")
                .toString() + "\n";
        FakeProcess process = new FakeProcess(READY + originalReply(new byte[]{1, 2, 3}) + fallback);

        try (RootNodeCaptureClient.Session session = RootNodeCaptureClient.open(context,
                RootConnectionDiagnostics.newAttempt(), command -> process)) {
            Bundle image = session.captureImage("example.chat", 14, IMAGE_BOUNDS);

            assertNotNull(image);
            Bitmap preview = image.getParcelable("bitmap", Bitmap.class);
            assertNotNull(preview);
            assertFalse(image.containsKey("media_uri"));
            preview.recycle();
            String[] requests = process.stdin.toString(StandardCharsets.UTF_8).split("\n");
            assertEquals(2, requests.length);
            JSONObject retry = new JSONObject(requests[1]);
            assertFalse(retry.getBoolean("prefer_original"));
            assertEquals("example.chat", retry.getString("package"));
            assertEquals(14, retry.getInt("window_id"));
            assertEquals(IMAGE_BOUNDS.left, retry.getInt("left"));
            assertEquals(IMAGE_BOUNDS.bottom, retry.getInt("bottom"));
        }
    }

    private static JSONObject imageReply() throws Exception {
        return new JSONObject().put("same_window", true)
                .put("left", IMAGE_BOUNDS.left).put("top", IMAGE_BOUNDS.top)
                .put("right", IMAGE_BOUNDS.right).put("bottom", IMAGE_BOUNDS.bottom);
    }

    private static String originalReply(byte[] bytes) throws Exception {
        JSONObject chunk = new JSONObject().put("media_chunk", Base64.encodeToString(bytes, Base64.NO_WRAP));
        return chunk + "\n" + imageReply().put("media_encoded", true) + "\n";
    }

    @Test public void recordingEventBeforeReadyDoesNotConsumeStartupReply() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String event = "{\"recordings\":[[\"example.chat\",0,3]],\"observed_at\":120}\n";
        try (RootNodeCaptureClient.Session session = RootNodeCaptureClient.open(context,
                RootConnectionDiagnostics.newAttempt(), command -> new FakeProcess(event + READY))) {
            assertTrue(session.startedMicRecording("example.chat", 100, 0));
            assertFalse(session.startedMicRecording("example.chat", 121, 0));
            assertFalse(session.startedMicRecording("example.chat", 100, 10));
        }
    }

    @Test public void startupFailuresUseFixedMessagesAndRecordCategories() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String attempt = RootConnectionDiagnostics.newAttempt();

        assertStartupFailure(context, attempt, OCCUPIED, "其他自动化会话占用");
        assertStartupFailure(context, attempt, PERMISSION, "Root");
        assertStartupFailure(context, attempt,
                "{\"ready\":false,\"error_code\":\"uiautomation_connect_failed\"}\n",
                "无法连接");
        assertStartupFailure(context, attempt,
                "{\"ready\":false,\"error\":\"secret://value\",\"error_code\":\"other\"}\n",
                "无法连接");

        String summary = RootConnectionDiagnostics.summary();
        assertTrue(summary.contains("attempt=" + attempt + " proc="));
        assertTrue(summary.contains("outcome=already_registered"));
        assertTrue(summary.contains("outcome=permission_denied"));
        assertTrue(summary.contains("outcome=startup_rejected"));
        assertFalse(summary.contains("private detail"));
        assertFalse(summary.contains("secret://"));
    }

    @Test public void helperStartFailureIsDistinctAndARecoveryCanOpenAndCloseTwice() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String launchFailureAttempt = RootConnectionDiagnostics.newAttempt();
        try {
            RootNodeCaptureClient.open(context, launchFailureAttempt, command -> {
                throw new IOException("private helper path");
            });
            fail("Expected helper start failure");
        } catch (IOException expected) {
            assertEquals("private helper path", expected.getMessage());
        }

        AtomicInteger starts = new AtomicInteger();
        String failedAttempt = RootConnectionDiagnostics.newAttempt();
        try {
            RootNodeCaptureClient.open(context, failedAttempt,
                    command -> { starts.incrementAndGet(); return new FakeProcess(OCCUPIED); });
            fail("Expected the occupied helper to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("其他自动化会话占用"));
        }
        String recoveredAttempt = RootConnectionDiagnostics.newAttempt();
        RootNodeCaptureClient.Session recovered = RootNodeCaptureClient.open(context, recoveredAttempt,
                command -> { starts.incrementAndGet(); return new FakeProcess(READY); });
        recovered.close();
        recovered.close();

        assertEquals(2, starts.get());
        String summary = RootConnectionDiagnostics.summary();
        assertTrue(summary.contains("outcome=helper_start_failed"));
        assertTrue(summary.contains("outcome=connect_success"));
    }

    @Test public void emptyHelperExitIsConnectionFailureAndSessionReleaseIsRecordedOnce() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String attempt = RootConnectionDiagnostics.newAttempt();
        try {
            RootNodeCaptureClient.open(context, attempt, command -> new FakeProcess(""));
            fail("Expected helper connection failure");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Root node process exited"));
        }

        String successAttempt = RootConnectionDiagnostics.newAttempt();
        FakeProcess process = new FakeProcess(READY);
        RootNodeCaptureClient.Session session = RootNodeCaptureClient.open(context, successAttempt,
                command -> process);
        session.close();
        session.close();

        String[] rows = RootConnectionDiagnostics.summary().split("\\r?\\n");
        long releases = java.util.Arrays.stream(rows)
                .filter(row -> row.contains("attempt=" + successAttempt + " "))
                .filter(row -> row.endsWith("outcome=session_release"))
                .count();
        assertEquals(1L, releases);
        assertEquals(1, process.destroyCalls);
        assertTrue(RootConnectionDiagnostics.summary().contains("outcome=connect_failed"));
    }

    @Test public void helperConnectionStillWorksWhenOptionalLoggingIsDisabled() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        DragShareSettings original = DragShareSettings.readLocal(context);
        DragShareLog.configure(original.toBuilder()
                .setLogLevel(DragShareSettings.LOG_LEVEL_DISABLED).build());
        String attempt = RootConnectionDiagnostics.newAttempt();
        FakeProcess process = new FakeProcess(READY);
        try {
            RootNodeCaptureClient.Session session = RootNodeCaptureClient.open(context, attempt,
                    command -> process);
            session.close();
            assertEquals(1, process.destroyCalls);
            assertTrue(RootConnectionDiagnostics.summary().contains(
                    "attempt=" + attempt + " proc="));
            assertTrue(RootConnectionDiagnostics.summary().contains("outcome=connect_success"));
        } finally {
            DragShareLog.configure(original);
        }
    }

    @Test public void portalRootProbeOutcomesAreIncludedInTheSafeConnectionSummary() {
        String attempt = RootConnectionDiagnostics.newAttempt();
        long now = System.currentTimeMillis();
        RootConnectionDiagnostics.importPortalEvent(attempt, "portal-process", "portal_root_probe_started", -1L, now);
        RootConnectionDiagnostics.importPortalEvent(attempt, "portal-process", "portal_root_unknown", -1L, now + 1L);
        String summary = RootConnectionDiagnostics.summary();
        assertTrue(summary.contains("attempt=" + attempt + " proc=portal-process outcome=portal_root_probe_started"));
        assertTrue(summary.contains("attempt=" + attempt + " proc=portal-process outcome=portal_root_unknown"));
        assertFalse(summary.contains("authorization="));
        assertFalse(summary.contains("su -c"));
    }

    private static void assertStartupFailure(Context context, String attempt,
                                             String response, String expectedMessage) throws Exception {
        try {
            RootNodeCaptureClient.open(context, attempt, command -> new FakeProcess(response));
            fail("Expected controlled startup failure");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains(expectedMessage));
            assertFalse(expected.getMessage().contains("private detail"));
            assertFalse(expected.getMessage().contains("secret://"));
        }
    }

    private static final class FakeProcess extends Process {
        private final ByteArrayInputStream stdout;
        private final ByteArrayInputStream stderr = new ByteArrayInputStream(new byte[0]);
        private final ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        private boolean destroyed;
        int destroyCalls;

        FakeProcess(String response) {
            stdout = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
        }

        @Override public OutputStream getOutputStream() { return stdin; }
        @Override public ByteArrayInputStream getInputStream() { return stdout; }
        @Override public ByteArrayInputStream getErrorStream() { return stderr; }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { destroyCalls++; destroyed = true; }
        @Override public boolean isAlive() { return !destroyed; }
    }
}
