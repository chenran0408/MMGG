package com.leaf.hyperdragshare.codex;

import android.view.MotionEvent;

/** Transfers input authority at a gesture boundary on the main queue. */
final class PortalInputHandoff {
    private volatile long fallbackDownTime = -1L;

    boolean shouldListen(boolean rootReady) {
        return !rootReady || fallbackActive();
    }

    boolean fallbackActive() {
        return fallbackDownTime >= 0L;
    }

    boolean acceptFallback(int action, long downTime, boolean rootReady) {
        if (action != MotionEvent.ACTION_DOWN) return fallbackDownTime == downTime;
        if (rootReady || downTime <= fallbackDownTime) return false;
        fallbackDownTime = downTime;
        return true;
    }

    boolean missedFallbackEnd(long nextDownTime, boolean rootReady) {
        return rootReady && fallbackActive() && nextDownTime > fallbackDownTime;
    }

    void finishFallback() {
        fallbackDownTime = -1L;
    }
}
