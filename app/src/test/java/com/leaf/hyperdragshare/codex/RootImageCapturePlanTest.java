package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Rect;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class RootImageCapturePlanTest {
    @Test public void verifiedOriginalSkipsScreenshot() throws Exception {
        JSONObject reply = RootNodeCaptureMain.imageCaptureReply(new Rect(1, 2, 3, 4),
                RootNodeCaptureMain.OriginalImage.READY, () -> true, () -> {
                    throw new AssertionError("Original did not need a screenshot");
                });

        assertTrue(reply.getBoolean("same_window"));
        assertTrue(reply.getBoolean("media_encoded"));
        assertFalse(reply.has("image_base64"));
        assertEquals(4, reply.getInt("bottom"));
    }

    @Test public void changedWindowDoesNotDeliverOriginalOrScreenshot() throws Exception {
        JSONObject reply = RootNodeCaptureMain.imageCaptureReply(new Rect(1, 2, 3, 4),
                RootNodeCaptureMain.OriginalImage.WINDOW_CHANGED, () -> false, () -> {
                    throw new AssertionError("Changed window must not be captured");
                });

        assertFalse(reply.getBoolean("same_window"));
        assertFalse(reply.has("media_encoded"));
        assertFalse(reply.has("image_base64"));
    }

    @Test public void unavailableOriginalReturnsOneVerifiedCroppedScreenshot() throws Exception {
        AtomicInteger screenshots = new AtomicInteger();
        AtomicInteger checks = new AtomicInteger();
        Bitmap screen = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        JSONObject reply = RootNodeCaptureMain.imageCaptureReply(new Rect(1, 2, 7, 12),
                RootNodeCaptureMain.OriginalImage.UNAVAILABLE, () -> {
                    checks.incrementAndGet();
                    return true;
                }, () -> {
                    screenshots.incrementAndGet();
                    return screen;
                });

        assertTrue(reply.getBoolean("same_window"));
        assertTrue(reply.has("image_base64"));
        assertFalse(reply.optBoolean("media_encoded"));
        assertEquals(7, reply.getInt("right"));
        assertEquals(1, screenshots.get());
        assertEquals(1, checks.get());
        assertTrue(screen.isRecycled());
    }
}
