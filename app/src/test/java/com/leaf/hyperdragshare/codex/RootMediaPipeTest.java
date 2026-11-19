package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.os.Bundle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class RootMediaPipeTest {
    @Test public void encodedPngRetainsItsBytesMimeAndDecodablePreview() throws Exception {
        byte[] source = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAIAAAAGCAYAAADpJ08yAAAADElEQVR4nGNgIAcAAAA2AAG2dLktAAAAAElFTkSuQmCC");
        EncodedMedia media = EncodedMedia.decode(source);
        assertArrayEquals(source, media.bytes);
        assertEquals("image/png", media.mimeType);
        assertTrue(media.preview.getWidth() > 0);
        media.preview.recycle();
    }

    @Test public void encodedGifRetainsItsMediaTypeAndBytes() throws Exception {
        byte[] source = java.util.Base64.getDecoder().decode(
                "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");
        EncodedMedia media = EncodedMedia.decode(source);
        assertArrayEquals(source, media.bytes);
        assertEquals("image/gif", media.mimeType);
        if (media.preview != null) media.preview.recycle();
    }

    @Test public void encodedMediaFramesRoundTripWithoutPassingBytesInFinalJson() throws Exception {
        byte[] source = new byte[48 * 1024 + 7];
        for (int i = 0; i < source.length; i++) source[i] = (byte)(i * 31);
        ByteArrayOutputStream lines = new ByteArrayOutputStream();
        assertEquals(source.length, RootMediaPipe.write(new ByteArrayInputStream(source),
                new PrintStream(lines)));
        ByteArrayOutputStream restored = new ByteArrayOutputStream();
        for (String line : lines.toString(java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
            RootMediaPipe.append(restored, new JSONObject(line).getString("media_chunk"));
        }
        assertArrayEquals(source, restored.toByteArray());
    }

    @Test public void originalMediaRequiresTheExplicitMatchingTouchAndBounds() {
        Bundle request = new Bundle();
        request.putInt("uiAgentType", 0);
        request.putInt("catch_way", 1);
        request.putFloat(SystemUiAgent.TOUCH_X, 20);
        request.putFloat(SystemUiAgent.TOUCH_Y, 40);
        request.putBoolean(SystemUiAgent.CAPTURE_MEDIA, true);
        request.putParcelable(SystemUiAgent.MEDIA_BOUNDS, new Rect(10, 30, 80, 90));
        assertTrue(ContentCatcherMediaHooks.matchesMediaRequest(request, new Rect(10, 30, 80, 90)));
        assertFalse(ContentCatcherMediaHooks.matchesMediaRequest(request, new Rect(0, 0, 100, 100)));
        request.putFloat(SystemUiAgent.TOUCH_X, 100);
        assertFalse(ContentCatcherMediaHooks.matchesMediaRequest(request, new Rect(10, 30, 80, 90)));
        request.putFloat(SystemUiAgent.TOUCH_X, 20);
        request.putInt("catch_way", 3);
        assertFalse(ContentCatcherMediaHooks.matchesMediaRequest(request, new Rect(10, 30, 80, 90)));
    }

    @Test public void uiAgentReplyMustMatchSourceAndRequestGeneration() {
        Bundle reply = new Bundle();
        reply.putInt("code", 0);
        reply.putString("token", "com.example~10123");
        reply.putString(SystemUiAgent.REQUEST_ID, "press-2");
        assertTrue(SystemUiAgent.matches(reply, "com.example~10123", "press-2"));
        assertFalse(SystemUiAgent.matches(reply, "com.example~10123", "press-1"));
        assertFalse(SystemUiAgent.matches(reply, "com.other~10124", "press-2"));
        reply.putInt("code", 1);
        assertFalse(SystemUiAgent.matches(reply, "com.example~10123", "press-2"));
    }
}
