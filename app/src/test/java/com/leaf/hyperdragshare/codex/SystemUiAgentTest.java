package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.*;
import android.graphics.Rect;
import java.io.StringReader;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class SystemUiAgentTest {
    private static JSONObject request(boolean text, boolean images) throws Exception {
        return new JSONObject().put("x", 300).put("y", 550).put("density", 3)
                .put("width", 1440).put("height", 3200)
                .put("allow_text", text).put("allow_images", images);
    }

    private static List<AccessibilityNodeSnapshot> nodes(String xml) throws Exception {
        return SystemUiAgent.parseViews(new StringReader("<hierarchy><root>" + xml
                + "</root></hierarchy>"), "com.tencent.mm");
    }

    @Test public void rawViewMessageUsesExistingTextSelection() throws Exception {
        List<AccessibilityNodeSnapshot> parsed = nodes("<node class=\"android.widget.FrameLayout\" bounds=\"[0,0][1440,3200]\">"
                + "<node class=\"android.widget.TextView\" bounds=\"[200, 500][1200, 650]\" text=\"sample message\" long-clickable=\"true\"/>"
                + "</node>");
        assertEquals(2, parsed.size());
        assertEquals(new Rect(200, 500, 1200, 650), parsed.get(1).bounds);
        JSONObject selected = RootUiAgentCapture.select(request(true, true), "com.tencent.mm", parsed);
        assertTrue(selected.getBoolean("found"));
        assertEquals("text", selected.getString("kind"));
        assertEquals("sample message", selected.getString("text"));
        assertEquals(200, selected.getInt("left"));
        assertFalse(RootUiAgentCapture.select(request(false, true), "com.tencent.mm", parsed).getBoolean("found"));
    }

    @Test public void imageSelectionKeepsExactNodeBoundsAndTypeSwitch() throws Exception {
        List<AccessibilityNodeSnapshot> parsed = nodes("<node class=\"android.widget.ImageView\" bounds=\"[200, 500][600, 900]\" content-desc=\"图片\"/>");
        JSONObject selected = RootUiAgentCapture.select(request(true, true), "com.tencent.mm", parsed);
        assertEquals("image", selected.getString("kind"));
        assertEquals(600, selected.getInt("right"));
        assertFalse(RootUiAgentCapture.select(request(true, false), "com.tencent.mm", parsed).getBoolean("found"));
    }

    @Test public void rawBackgroundImageDoesNotReplaceSupplementedMessage() throws Exception {
        List<AccessibilityNodeSnapshot> parsed = nodes(
                "<node class=\"android.widget.ImageView\" bounds=\"[0,342][1440,2922]\"/>"
                + "<node class=\"android.widget.TextView\" bounds=\"[223,609][1231,2922]\" text=\"message\"/>");
        JSONObject selected = RootUiAgentCapture.select(request(true, true).put("x", 1000)
                .put("y", 855), "com.tencent.mm", parsed);

        assertTrue(selected.getBoolean("found"));
        assertEquals("text", selected.getString("kind"));
        assertEquals("message", selected.getString("text"));
        assertEquals(223, selected.getInt("left"));
        assertEquals(609, selected.getInt("top"));
    }

    @Test public void passwordParentProtectsDescendantsAndSiblingRemainsUsable() throws Exception {
        List<AccessibilityNodeSnapshot> parsed = nodes("<node password=\"true\" bounds=\"[200,500][600,700]\">"
                + "<node class=\"android.widget.TextView\" text=\"secret\" bounds=\"[200,500][600,700]\"/>"
                + "</node><node class=\"android.widget.TextView\" text=\"public\" bounds=\"[200,800][600,900]\"/>");
        assertTrue(parsed.get(1).password);
        assertNull(parsed.get(1).text);
        assertFalse(parsed.get(2).password);
        assertFalse(RootUiAgentCapture.select(request(true, true), "com.tencent.mm", parsed).getBoolean("found"));
        JSONObject below = request(true, true).put("y", 850);
        assertEquals("public", RootUiAgentCapture.select(below, "com.tencent.mm", parsed).getString("text"));
    }

    @Test public void nativeVoiceControlStillUsesCommonAvoidance() throws Exception {
        List<AccessibilityNodeSnapshot> parsed = nodes("<node class=\"android.widget.Button\" text=\"按住说话\" clickable=\"true\" bounds=\"[200,500][600,700]\"/>");
        assertEquals("excluded_hold_control", RootUiAgentCapture.select(
                request(true, true), "com.tencent.mm", parsed).getString("reason"));
    }

    @Test public void unlabeledContainerCannotBecomeAnImageOrOcrRegion() throws Exception {
        List<AccessibilityNodeSnapshot> parsed = nodes("<node class=\"android.view.ViewGroup\" bounds=\"[0,0][1440,3200]\"/>");
        assertFalse(RootUiAgentCapture.select(request(true, true), "com.tencent.mm", parsed).getBoolean("found"));
    }
}
