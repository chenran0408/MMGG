package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;

import android.view.MotionEvent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class GestureTraceRecorderTest {
    @Test public void recordsMoveCountDistanceAndTerminalCoordinates() {
        List<String> summaries = new ArrayList<>();
        GestureTraceRecorder recorder = new GestureTraceRecorder(summaries::add);

        recorder.record(MotionEvent.ACTION_DOWN, 10f, 20f, 100L, "root");
        recorder.record(MotionEvent.ACTION_MOVE, 13f, 24f, 110L, "root");
        recorder.record(MotionEvent.ACTION_MOVE, 16f, 28f, 120L, "root");
        assertEquals(120L, recorder.durationMs(220L));

        recorder.finish("root", 220L, true, true, false);

        assertEquals(1, summaries.size());
        assertEquals("gesture summary source=root duration_ms=120 moves=2 max_distance_px=10"
                + " start=10,20 end=16,28 overlay_active=true menu=false", summaries.get(0));
    }

    @Test public void discardedSummaryClearsTraceAndSourceMismatchDoesNot() {
        List<String> summaries = new ArrayList<>();
        GestureTraceRecorder recorder = new GestureTraceRecorder(summaries::add);

        recorder.record(MotionEvent.ACTION_DOWN, 1f, 2f, 30L, "root");
        recorder.record(MotionEvent.ACTION_MOVE, 4f, 6f, 40L, "root");
        recorder.finish("portal", 50L, true, true, true);
        assertEquals(0, summaries.size());
        assertEquals(20L, recorder.durationMs(50L));

        recorder.finish("root", 50L, false, false, false);
        assertEquals(0, summaries.size());
        assertEquals(-1L, recorder.durationMs(60L));
    }
}
