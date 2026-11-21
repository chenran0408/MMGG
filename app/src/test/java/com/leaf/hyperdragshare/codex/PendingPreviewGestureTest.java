package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;

import android.view.MotionEvent;

import org.junit.Test;

public final class PendingPreviewGestureTest {
    private static final int LEFT = 100;
    private static final int TOP = 100;
    private static final int RIGHT = 300;
    private static final int BOTTOM = 300;

    @Test public void insideTapKeepsPreviewAndDoesNotStartDrag() {
        PendingPreviewGesture gesture = new PendingPreviewGesture();
        assertEquals(PendingPreviewGesture.Outcome.NONE,
                gesture.onEvent(MotionEvent.ACTION_DOWN, 150, 150,
                        LEFT, TOP, RIGHT, BOTTOM, 8));
        assertEquals(PendingPreviewGesture.Outcome.NONE,
                gesture.onEvent(MotionEvent.ACTION_UP, 150, 150,
                        LEFT, TOP, RIGHT, BOTTOM, 8));
    }

    @Test public void insideMovePastSlopStartsExactlyOnce() {
        PendingPreviewGesture gesture = new PendingPreviewGesture();
        gesture.onEvent(MotionEvent.ACTION_DOWN, 150, 150,
                LEFT, TOP, RIGHT, BOTTOM, 8);
        assertEquals(PendingPreviewGesture.Outcome.START_DRAG,
                gesture.onEvent(MotionEvent.ACTION_MOVE, 160, 150,
                        LEFT, TOP, RIGHT, BOTTOM, 8));
        assertEquals(PendingPreviewGesture.Outcome.NONE,
                gesture.onEvent(MotionEvent.ACTION_MOVE, 180, 150,
                        LEFT, TOP, RIGHT, BOTTOM, 8));
    }

    @Test public void outsideTapCancelsOnlyOnCompleteTap() {
        PendingPreviewGesture gesture = new PendingPreviewGesture();
        gesture.onEvent(MotionEvent.ACTION_DOWN, 20, 20,
                LEFT, TOP, RIGHT, BOTTOM, 8);
        assertEquals(PendingPreviewGesture.Outcome.CANCEL_PREVIEW,
                gesture.onEvent(MotionEvent.ACTION_UP, 22, 21,
                        LEFT, TOP, RIGHT, BOTTOM, 8));
    }

    @Test public void outsideMoveDoesNotCancelOnUp() {
        PendingPreviewGesture gesture = new PendingPreviewGesture();
        gesture.onEvent(MotionEvent.ACTION_DOWN, 20, 20,
                LEFT, TOP, RIGHT, BOTTOM, 8);
        gesture.onEvent(MotionEvent.ACTION_MOVE, 40, 20, LEFT, TOP, RIGHT, BOTTOM, 8);
        assertEquals(PendingPreviewGesture.Outcome.NONE,
                gesture.onEvent(MotionEvent.ACTION_UP, 40, 20,
                        LEFT, TOP, RIGHT, BOTTOM, 8));
    }

    @Test public void cancelLeavesPreviewForExplicitCleanup() {
        PendingPreviewGesture gesture = new PendingPreviewGesture();
        gesture.onEvent(MotionEvent.ACTION_DOWN, 150, 150,
                LEFT, TOP, RIGHT, BOTTOM, 8);
        assertEquals(PendingPreviewGesture.Outcome.NONE,
                gesture.onEvent(MotionEvent.ACTION_CANCEL, 150, 150,
                        LEFT, TOP, RIGHT, BOTTOM, 8));
    }

    @Test public void additionalPointerInvalidatesSinglePointerPreviewGesture() {
        PendingPreviewGesture gesture = new PendingPreviewGesture();
        gesture.onEvent(MotionEvent.ACTION_DOWN, 150, 150, LEFT, TOP, RIGHT, BOTTOM, 8);
        gesture.onEvent(MotionEvent.ACTION_POINTER_DOWN, 150, 150,
                LEFT, TOP, RIGHT, BOTTOM, 8);
        assertEquals(PendingPreviewGesture.Outcome.NONE,
                gesture.onEvent(MotionEvent.ACTION_MOVE, 180, 150,
                        LEFT, TOP, RIGHT, BOTTOM, 8));
        assertEquals(PendingPreviewGesture.Outcome.NONE,
                gesture.onEvent(MotionEvent.ACTION_UP, 20, 20,
                        LEFT, TOP, RIGHT, BOTTOM, 8));
    }
}
