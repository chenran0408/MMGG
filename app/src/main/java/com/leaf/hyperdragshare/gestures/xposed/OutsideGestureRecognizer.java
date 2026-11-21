package com.leaf.hyperdragshare.gestures.xposed;

import android.graphics.Rect;
import android.os.Handler;
import android.view.ViewConfiguration;

final class OutsideGestureRecognizer {

    interface Listener {
        void onOutsideAction(boolean doubleTap);
        default void onOutsideLongPress() { }
    }

    private final Handler handler;
    private final Listener listener;
    private final float touchSlopSquared;
    private final float edgeTapSlopSquared;
    private final float systemBackDecisionDistance;
    private final float edgeTapRetentionDistance;
    private final float doubleTapSlopSquared;
    private long doubleTapTimeout;
    private final long tapTimeout;
    private final long longPressTimeout;

    private boolean active;
    private boolean secondTap;
    private boolean systemEdgeCandidate;
    private boolean edgeFromLeft;
    private float downX;
    private float downY;
    private long downTime;
    private long lastAcceptedDownEventTime = Long.MIN_VALUE;
    private boolean pendingTap;
    private boolean inputMethodVisible;
    private float pendingX;
    private float pendingY;
    private long pendingTime;
    private final Runnable singleTapRunnable = this::dispatchPendingSingle;
    private final Runnable longPressRunnable = this::dispatchLongPress;

    OutsideGestureRecognizer(Handler handler, ViewConfiguration configuration,
                             float density, Listener listener) {
        this.handler = handler;
        this.listener = listener;
        float touchSlop = configuration.getScaledTouchSlop();
        float doubleTapSlop = configuration.getScaledDoubleTapSlop();
        this.touchSlopSquared = touchSlop * touchSlop;
        this.systemBackDecisionDistance = Math.max(touchSlop * 1.5f, 12f * density);
        this.edgeTapRetentionDistance = Math.max(touchSlop * 2f, 18f * density);
        this.edgeTapSlopSquared = edgeTapRetentionDistance * edgeTapRetentionDistance;
        this.doubleTapSlopSquared = doubleTapSlop * doubleTapSlop;
        this.doubleTapTimeout = com.leaf.hyperdragshare.gestures.config.ConfigContract
                .DEFAULT_OUTSIDE_TAP_WINDOW_MS;
        this.tapTimeout = ViewConfiguration.getLongPressTimeout();
        this.longPressTimeout = ViewConfiguration.getLongPressTimeout();
    }

    void setDoubleTapTimeout(long timeoutMs) {
        long resolved = Math.max(com.leaf.hyperdragshare.gestures.config.ConfigContract
                        .MIN_OUTSIDE_TAP_WINDOW_MS,
                Math.min(com.leaf.hyperdragshare.gestures.config.ConfigContract
                                .MAX_OUTSIDE_TAP_WINDOW_MS, timeoutMs));
        if (doubleTapTimeout == resolved) return;
        if (pendingTap) dispatchPendingSingle();
        doubleTapTimeout = resolved;
    }

    void setInputMethodVisible(boolean visible) {
        if (inputMethodVisible == visible) return;
        inputMethodVisible = visible;
        // A pending outside tap may otherwise fire after the keyboard appears and
        // dismiss the freeform window while the user is typing. Also discard a
        // partially observed tap across the IME transition.
        clearAll();
    }

    boolean onDown(float x, float y, Rect bounds, int displayWidth,
                   float leftReserve, float rightReserve, long eventTime) {
        // Side-edge touches are visible both to the global input monitor and to the
        // transparent outside window. They carry the same event time. If the second
        // callback is delivered after the first UP, treating it as a new DOWN turns one
        // physical tap into a double tap. Accept each physical stream only once.
        if (eventTime == lastAcceptedDownEventTime) {
            Log.i("Ignored duplicate outside DOWN eventTime=" + eventTime);
            return false;
        }
        lastAcceptedDownEventTime = eventTime;
        handler.removeCallbacks(longPressRunnable);
        active = false;
        secondTap = false;
        systemEdgeCandidate = false;
        edgeFromLeft = false;
        if (bounds == null) return false;

        GestureGeometry.OutsideRegion region = GestureGeometry.outsideRegion(
                x, y, bounds.left, bounds.top, bounds.right, bounds.bottom);
        if (region != GestureGeometry.OutsideRegion.OUTSIDE) return false;

        systemEdgeCandidate = GestureGeometry.inSystemEdgeReserve(
                x, displayWidth, leftReserve, rightReserve);
        edgeFromLeft = systemEdgeCandidate && x <= leftReserve;

        if (pendingTap) {
            boolean inTime = eventTime - pendingTime <= doubleTapTimeout;
            boolean inRange = squaredDistance(x, y, pendingX, pendingY) <= doubleTapSlopSquared;
            if (inTime && inRange) {
                secondTap = true;
            } else {
                dispatchPendingSingle();
            }
        }

        active = true;
        downX = x;
        downY = y;
        downTime = eventTime;
        if (systemEdgeCandidate) {
            Log.i("Outside system-edge tap armed; movement still reserved for system x="
                    + Math.round(x));
        } else {
            handler.postDelayed(longPressRunnable, longPressTimeout);
        }
        return !systemEdgeCandidate;
    }

    void onMove(float x, float y) {
        if (!active) return;
        boolean movedOutsideTap = systemEdgeCandidate
                ? GestureGeometry.shouldYieldSystemBackEdge(edgeFromLeft, downX, downY, x, y,
                systemBackDecisionDistance, edgeTapRetentionDistance)
                : squaredDistance(x, y, downX, downY) > touchSlopSquared;
        if (movedOutsideTap) {
            handler.removeCallbacks(longPressRunnable);
            active = false;
            secondTap = false;
            if (systemEdgeCandidate) {
                Log.i("Outside system-edge movement yielded to system gesture dx="
                        + Math.round(x - downX) + " dy=" + Math.round(y - downY));
            }
            systemEdgeCandidate = false;
        }
    }

    void onUp(float x, float y, long eventTime, Runnable claimInput) {
        if (!finishTap(x, y, eventTime, claimInput)) return;
        if (inputMethodVisible) {
            clearPending();
            secondTap = false;
            return;
        }
        if (secondTap) {
            clearPending();
            secondTap = false;
            listener.onOutsideAction(true);
            return;
        }
        pendingTap = true;
        pendingX = x;
        pendingY = y;
        pendingTime = eventTime;
        handler.removeCallbacks(singleTapRunnable);
        handler.postDelayed(singleTapRunnable, doubleTapTimeout);
    }

    void onUpImmediate(float x, float y, long eventTime,
                       Runnable claimInput, Runnable action) {
        if (!finishTap(x, y, eventTime, claimInput)) return;
        clearPending();
        secondTap = false;
        if (action != null) action.run();
    }

    void onCancel() {
        handler.removeCallbacks(longPressRunnable);
        active = false;
        secondTap = false;
        systemEdgeCandidate = false;
        edgeFromLeft = false;
    }

    void clearAll() {
        onCancel();
        clearPending();
    }

    private void dispatchPendingSingle() {
        if (!pendingTap) return;
        clearPending();
        listener.onOutsideAction(false);
    }

    private void dispatchLongPress() {
        if (!active || inputMethodVisible || systemEdgeCandidate) return;
        active = false;
        secondTap = false;
        clearPending();
        Log.i("Outside long press recognized");
        listener.onOutsideLongPress();
    }

    private boolean finishTap(float x, float y, long eventTime, Runnable claimInput) {
        if (!active) return false;
        handler.removeCallbacks(longPressRunnable);
        float allowedMovementSquared = systemEdgeCandidate
                ? edgeTapSlopSquared : touchSlopSquared;
        boolean valid = eventTime - downTime <= tapTimeout
                && squaredDistance(x, y, downX, downY) <= allowedMovementSquared;
        active = false;
        if (!valid) {
            secondTap = false;
            systemEdgeCandidate = false;
            edgeFromLeft = false;
            return false;
        }
        if (systemEdgeCandidate && claimInput != null) claimInput.run();
        systemEdgeCandidate = false;
        edgeFromLeft = false;
        return true;
    }

    private void clearPending() {
        handler.removeCallbacks(singleTapRunnable);
        pendingTap = false;
        pendingTime = 0L;
    }

    private static float squaredDistance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return dx * dx + dy * dy;
    }
}
