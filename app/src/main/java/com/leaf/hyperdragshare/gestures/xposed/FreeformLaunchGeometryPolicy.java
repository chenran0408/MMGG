package com.leaf.hyperdragshare.gestures.xposed;

import android.graphics.Insets;
import android.graphics.Rect;

import com.leaf.hyperdragshare.gestures.config.WindowSizePolicy;

final class FreeformLaunchGeometryPolicy {
    static final class Result {
        final Rect safeBounds;
        final Rect placementBounds;
        final Rect visualBounds;
        final Rect logicalBounds;

        private Result(Rect safeBounds, Rect placementBounds,
                       Rect visualBounds, Rect logicalBounds) {
            this.safeBounds = copy(safeBounds);
            this.placementBounds = copy(placementBounds);
            this.visualBounds = copy(visualBounds);
            this.logicalBounds = copy(logicalBounds);
        }
    }

    private FreeformLaunchGeometryPolicy() { }

    static Result calculate(GestureConfig config, Rect display, Insets insets,
                            int edgePx, float scale, Rect nativeLaunchBounds) {
        int insetLeft = insets == null ? 0 : insets.left;
        int insetTop = insets == null ? 0 : insets.top;
        int insetRight = insets == null ? 0 : insets.right;
        int insetBottom = insets == null ? 0 : insets.bottom;
        Rect safe = new Rect(display.left + insetLeft + edgePx,
                display.top + insetTop + edgePx,
                display.right - insetRight - edgePx,
                display.bottom - insetBottom - edgePx);
        if (width(safe) <= 0 || height(safe) <= 0) safe = new Rect(display);
        boolean landscape = width(display) > height(display);

        Rect sizeReference = landscape ? display : safe;
        Rect placement = landscape
                ? new Rect(display.left + edgePx, display.top + edgePx,
                display.right - edgePx, display.bottom - edgePx)
                : new Rect(safe);
        int visualWidth;
        int visualHeight;
        int logicalWidth;
        int logicalHeight;
        if (config.nativeWindowScalingEnabled(landscape)) {
            if (nativeLaunchBounds == null || nativeLaunchBounds.isEmpty()) {
                return new Result(safe, placement, null, null);
            }
            int[] logicalSize = WindowSizePolicy.scaleNativeLogicalPixels(
                    width(nativeLaunchBounds), height(nativeLaunchBounds),
                    width(placement), height(placement), scale,
                    config.nativeWindowScalePercent(landscape));
            logicalWidth = logicalSize[0];
            logicalHeight = logicalSize[1];
            visualWidth = Math.max(1, Math.round(logicalWidth * scale));
            visualHeight = Math.max(1, Math.round(logicalHeight * scale));
        } else {
            visualWidth = Math.max(1,
                    Math.round(width(sizeReference)
                            * config.windowWidthPercent(landscape) / 100f));
            visualHeight = Math.max(1,
                    Math.round(height(sizeReference)
                            * config.windowHeightPercent(landscape) / 100f));
            logicalWidth = Math.max(1, Math.round(visualWidth / scale));
            logicalHeight = Math.max(1, Math.round(visualHeight / scale));
        }
        int visualLeft = placement.left
                + Math.round((width(placement) - visualWidth)
                * config.windowPositionX(landscape) / 100f);
        int visualTop = placement.top
                + Math.round((height(placement) - visualHeight)
                * config.windowPositionY(landscape) / 100f);
        Rect visual = new Rect(visualLeft, visualTop,
                visualLeft + visualWidth, visualTop + visualHeight);
        Rect logical = new Rect(visualLeft, visualTop,
                visualLeft + logicalWidth, visualTop + logicalHeight);
        return new Result(safe, placement, visual, logical);
    }

    static Rect visualBounds(Rect logicalBounds, float scale) {
        return new Rect(logicalBounds.left, logicalBounds.top,
                logicalBounds.left + Math.round(width(logicalBounds) * scale),
                logicalBounds.top + Math.round(height(logicalBounds) * scale));
    }

    private static Rect copy(Rect value) {
        return value == null ? null : new Rect(value);
    }

    private static int width(Rect value) {
        return value.right - value.left;
    }

    private static int height(Rect value) {
        return value.bottom - value.top;
    }
}
