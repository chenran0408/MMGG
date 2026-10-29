package com.leaf.hyperdragshare.gestures.xposed;

import android.graphics.Rect;

/**
 * Pure eligibility decisions used by the SystemUI fan runtime.
 *
 * These decisions intentionally preserve the current runtime semantics while
 * making configuration and IME behavior testable without constructing a
 * SystemUI hook instance.
 */
final class FanRuntimePolicy {

    private FanRuntimePolicy() {
    }

    static boolean canStartBottom(
            boolean runtimeReady,
            boolean imeVisible,
            int targetCount,
            boolean bottomEnabled,
            boolean bottomLeftEnabled,
            boolean bottomRightEnabled) {
        return runtimeReady
                && !imeVisible
                && targetCount >= 3
                && bottomEnabled
                && (bottomLeftEnabled || bottomRightEnabled);
    }

    static boolean canStartSide(boolean runtimeReady, boolean hasSideTargets) {
        return runtimeReady && hasSideTargets;
    }

    /** Preserve the existing distinction between an invisible IME and unknown bounds. */
    static boolean shouldCaptureOutsideWithIme(boolean imeVisible, Rect imeBounds) {
        return !imeVisible || imeBounds != null;
    }
}
