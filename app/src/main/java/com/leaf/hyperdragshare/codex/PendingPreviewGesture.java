package com.leaf.hyperdragshare.codex;

import android.view.MotionEvent;

/** Tracks one Root-owned touch stream while an image preview is waiting for a drag. */
final class PendingPreviewGesture {
    enum Outcome { NONE, START_DRAG, CANCEL_PREVIEW }

    private boolean down;
    private boolean startedInside;
    private boolean movedBeyondSlop;
    private float startX;
    private float startY;

    Outcome onEvent(int action, float x, float y, int left, int top, int right, int bottom,
                    int touchSlop) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                down = true;
                startX = x;
                startY = y;
                startedInside = contains(left, top, right, bottom, x, y);
                movedBeyondSlop = false;
                return Outcome.NONE;
            case MotionEvent.ACTION_MOVE:
                if (!down) return Outcome.NONE;
                if (distanceFromStart(x, y) > touchSlop) {
                    if (!movedBeyondSlop && startedInside) {
                        movedBeyondSlop = true;
                        return Outcome.START_DRAG;
                    }
                    movedBeyondSlop = true;
                }
                return Outcome.NONE;
            case MotionEvent.ACTION_UP:
                boolean outsideUp = !contains(left, top, right, bottom, x, y);
                boolean cancel = down && !startedInside && outsideUp && !movedBeyondSlop;
                reset();
                return cancel ? Outcome.CANCEL_PREVIEW : Outcome.NONE;
            case MotionEvent.ACTION_CANCEL:
                reset();
                return Outcome.NONE;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                reset();
                return Outcome.NONE;
            default:
                return Outcome.NONE;
        }
    }

    float startX() { return startX; }
    float startY() { return startY; }

    void reset() {
        down = false;
        startedInside = false;
        movedBeyondSlop = false;
    }

    private double distanceFromStart(float x, float y) {
        return Math.hypot(x - startX, y - startY);
    }

    private static boolean contains(int left, int top, int right, int bottom, float x, float y) {
        return x >= left && x < right && y >= top && y < bottom;
    }
}
