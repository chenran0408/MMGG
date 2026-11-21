package com.leaf.hyperdragshare.codex;

import android.view.MotionEvent;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Collects the bounded gesture summary without making input or state decisions. */
final class GestureTraceRecorder {
    private static final String TAG = "DragShare/UI";

    private final Consumer<String> summaryLogger;
    private String source;
    private long downTime = -1L;
    private float startX;
    private float startY;
    private float lastX;
    private float lastY;
    private float maxDistance;
    private int moves;

    GestureTraceRecorder(Consumer<String> summaryLogger) {
        this.summaryLogger = summaryLogger;
    }

    void samplePointerReceived(
            int action,
            float x,
            float y,
            long eventTime,
            String source,
            BooleanSupplier activeSupplier) {
        DragShareLog.dSampled(TAG, "pointer:" + source + ":" + action, 250L,
                () -> "pointer received source=" + source
                        + " action=" + MotionEvent.actionToString(action)
                        + " point=" + Math.round(x) + "," + Math.round(y)
                        + " active=" + activeSupplier.getAsBoolean()
                        + " time=" + eventTime);
    }

    void record(int action, float x, float y, long eventTime, String source) {
        if (action == MotionEvent.ACTION_DOWN) {
            this.source = source;
            downTime = eventTime;
            startX = x;
            startY = y;
            lastX = x;
            lastY = y;
            maxDistance = 0f;
            moves = 0;
            return;
        }
        if (downTime < 0L || !source.equals(this.source)) return;
        lastX = x;
        lastY = y;
        if (action != MotionEvent.ACTION_MOVE) return;
        moves++;
        maxDistance = Math.max(maxDistance,
                (float) Math.hypot(x - startX, y - startY));
    }

    long durationMs(long eventTime) {
        return downTime < 0L ? -1L : Math.max(0L, eventTime - downTime);
    }

    void finish(
            String source,
            long eventTime,
            boolean worthKeeping,
            boolean overlayActive,
            boolean menuShown) {
        if (downTime < 0L || !source.equals(this.source)) return;
        long durationMs = durationMs(eventTime);
        if (worthKeeping) {
            summaryLogger.accept("gesture summary source=" + source
                    + " duration_ms=" + durationMs
                    + " moves=" + moves
                    + " max_distance_px=" + Math.round(maxDistance)
                    + " start=" + Math.round(startX) + "," + Math.round(startY)
                    + " end=" + Math.round(lastX) + "," + Math.round(lastY)
                    + " overlay_active=" + overlayActive
                    + " menu=" + menuShown);
        }
        clear();
    }

    private void clear() {
        source = null;
        downTime = -1L;
        moves = 0;
        maxDistance = 0f;
    }
}
