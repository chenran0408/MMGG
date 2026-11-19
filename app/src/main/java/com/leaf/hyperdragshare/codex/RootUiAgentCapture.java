package com.leaf.hyperdragshare.codex;

import android.graphics.Rect;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Uses native Views only when an explicit press cannot read an accessibility root. */
final class RootUiAgentCapture {
    private static PressWindowSnapshot selectedWindow;
    private static Rect selectedBounds;

    private RootUiAgentCapture() {}

    static void reset() { selectedWindow = null; selectedBounds = null; }

    static JSONObject capture(JSONObject request) throws Exception {
        JSONObject absent = new JSONObject().put("found", false).put("reason", "window_not_ready");
        if (!request.optBoolean("press_lookup") || RootNodeCaptureMain.keyguardLocked()) return absent;
        PressWindowSnapshot.ParseResult parsed = RootNodeCaptureMain.readPressWindow();
        PressWindowSnapshot before = parsed.snapshot;
        if (before == null) return absent.put("reason", parsed.reason);
        String source = before.packageName;
        if (source.equals("com.android.systemui") || source.equals(BuildConfig.APPLICATION_ID)
                || source.equals("com.miui.contentextension")) return absent.put("reason", "excluded_app");
        JSONArray blocked = request.getJSONArray("blocked");
        for (int i = 0; i < blocked.length(); i++) {
            if (source.equals(blocked.getString(i))) return absent.put("reason", "excluded_app");
        }
        float x = (float) request.getDouble("x"), y = (float) request.getDouble("y");
        if (!before.permits(new Rect((int)x, (int)y, (int)x + 1, (int)y + 1))) return absent;
        List<AccessibilityNodeSnapshot> nodes;
        try {
            nodes = SystemUiAgent.readViews(source, before.windowName.substring(before.windowName.indexOf('/') + 1),
                    request.getInt("user_id"), x, y, request.optBoolean("allow_text", true));
        } catch (ReflectiveOperationException | java.io.IOException error) {
            System.err.println("Native View capture unavailable: " + error);
            return absent.put("reason", "uiagent_unavailable");
        }
        JSONObject result = select(request, source, nodes);
        if (!result.optBoolean("found")) return result;
        Rect bounds = new Rect(result.getInt("left"), result.getInt("top"),
                result.getInt("right"), result.getInt("bottom"));
        PressWindowSnapshot after = RootNodeCaptureMain.readPressWindow().snapshot;
        if (after == null || !before.sameWindow(after) || !after.permits(bounds)
                || RootNodeCaptureMain.keyguardLocked()) return absent.put("reason", "window_changed");
        selectedWindow = after;
        selectedBounds = bounds;
        System.err.println("Native View capture kind=" + result.getString("kind") + " nodes=" + nodes.size());
        return result.put("window_id", -1);
    }

    static JSONObject select(JSONObject request, String source, List<AccessibilityNodeSnapshot> nodes)
            throws Exception {
        float x = (float) request.getDouble("x"), y = (float) request.getDouble("y");
        JSONObject absent = new JSONObject().put("found", false).put("reason", "no_text");
        if (RootNodeCaptureMain.holdControlAtPoint(nodes, x, y, source)) {
            return absent.put("reason", "excluded_hold_control");
        }
        List<Rect> passwords = new ArrayList<>();
        for (AccessibilityNodeSnapshot node : nodes) if (node.password) passwords.add(node.bounds);
        AccessibilityNodeClassifier classifier = new AccessibilityNodeClassifier(
                (float) request.getDouble("density"), request.getInt("width"), request.getInt("height"));
        AccessibilityCandidateSelector.Selection selection = RootNodeCaptureMain.selectContent(classifier,
                nodes, passwords, x, y, source, request.optBoolean("allow_text", true),
                request.optBoolean("allow_images", false));
        return selection == null ? absent : RootNodeCaptureMain.contentResult(selection, source, nodes.size());
    }

    static boolean hasSelection() { return selectedWindow != null; }

    static Rect currentBounds(String source, Rect requested) throws Exception {
        if (selectedWindow == null || !source.equals(selectedWindow.packageName)
                || !selectedBounds.equals(requested) || RootNodeCaptureMain.keyguardLocked()) return null;
        PressWindowSnapshot current = RootNodeCaptureMain.readPressWindow().snapshot;
        return matchesSelection(source, requested, current) ? new Rect(requested) : null;
    }

    static boolean matchesSelection(String source, Rect requested, PressWindowSnapshot current) {
        return current != null && selectedWindow != null && source.equals(selectedWindow.packageName)
                && selectedBounds.equals(requested) && selectedWindow.sameWindow(current)
                && current.permits(requested);
    }

    static boolean isCurrentSource(String source) throws Exception {
        return selectedBounds != null && currentBounds(source, selectedBounds) != null;
    }
}
