package com.leaf.hyperdragshare.codex;

import android.content.ClipData;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;

/** Optional diagnostics only: never used to authorize, accept, or finish an operation. */
final class OperationTrace {
    static final String EXTRA = "mmgg_operation_trace";
    private static final String TAG = "DragShare/Operation";
    private static final int MAX_MEMORY_EVENTS = 160;
    private static final Deque<String> MEMORY_EVENTS = new ArrayDeque<>();

    private OperationTrace() {}

    static String id(String kind, long request) {
        return request < 0 ? "-" : token(kind) + "-" + DragShareLog.processInstance()
                + "-" + Long.toString(request, 36);
    }

    static String fresh(String kind) {
        return token(kind) + "-" + UUID.randomUUID();
    }

    static String read(Bundle input) {
        try { return input == null ? "-" : token(input.getString(EXTRA)); }
        catch (RuntimeException ignored) { return "-"; }
    }

    static void put(Bundle output, String operation) {
        String value = token(operation);
        try {
            if (output != null && !"-".equals(value)) output.putString(EXTRA, value);
        } catch (RuntimeException ignored) { /* Optional metadata cannot reject the request. */ }
    }

    static String read(ClipData clip) {
        try {
            PersistableBundle extras = clip == null ? null : clip.getDescription().getExtras();
            return extras == null ? "-" : token(extras.getString(EXTRA));
        } catch (RuntimeException ignored) { return "-"; }
    }

    static void put(ClipData clip, String operation) {
        String value = token(operation);
        if (clip == null || "-".equals(value)) return;
        try {
            PersistableBundle original = clip.getDescription().getExtras();
            PersistableBundle extras = original == null ? new PersistableBundle() : new PersistableBundle(original);
            extras.putString(EXTRA, value);
            clip.getDescription().setExtras(extras);
        } catch (RuntimeException ignored) { /* Keep the original drag when metadata cannot be attached. */ }
    }

    static void event(String operation, String stage, String outcome) {
        event(operation, stage, outcome, null);
    }

    static void event(String operation, String stage, String outcome, Uri image) {
        // No state machine, callback, URI grants, or data ownership lives in diagnostics.
        try {
            String message = "proc="
                    + DragShareLog.processInstance() + " op=" + token(operation)
                    + " stage=" + token(stage) + " outcome=" + token(outcome)
                    + " asset=" + asset(image);
            remember(timestamp() + " I DragShare/Operation: " + message);
            DragShareLog.i(TAG, message);
        } catch (RuntimeException ignored) { /* Logging must not change the operation. */ }
    }

    static void imageAccess(Uri image, String outcome, int callerUid) {
        try {
            String message = "proc=" + DragShareLog.processInstance()
                    + " op=- stage=image_access outcome=" + token(outcome)
                    + " asset=" + asset(image) + " callerUid=" + callerUid
                    + " read=unknown delivery=unknown";
            remember(timestamp() + " I DragShare/Operation: " + message);
            DragShareLog.i(TAG, message);
        } catch (RuntimeException ignored) { /* The provider's original access rules still decide. */ }
    }

    /** Returns the latest bounded, privacy-filtered events even when file logging is unavailable. */
    static synchronized String summary() {
        if (MEMORY_EVENTS.isEmpty()) return "";
        StringBuilder result = new StringBuilder("Controlled operation trace (memory, bounded):\n");
        for (String line : MEMORY_EVENTS) result.append(line).append('\n');
        return result.toString();
    }

    static synchronized void clearForTests() {
        MEMORY_EVENTS.clear();
    }

    private static synchronized void remember(String line) {
        if (line == null || line.length() > 512) return;
        MEMORY_EVENTS.addLast(line);
        while (MEMORY_EVENTS.size() > MAX_MEMORY_EVENTS) MEMORY_EVENTS.removeFirst();
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
    }

    /** A one-way fingerprint of a random resource address, never the URI/capability or image bytes. */
    static String asset(Uri image) {
        if (image == null) return "-";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(image.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                result.append(Character.forDigit((digest[i] >>> 4) & 15, 16));
                result.append(Character.forDigit(digest[i] & 15, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException | RuntimeException ignored) { return "-"; }
    }

    private static String token(String value) {
        return value != null && value.length() <= 128 && value.matches("[A-Za-z0-9_.:-]+") ? value : "-";
    }
}
