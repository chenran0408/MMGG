package com.leaf.hyperdragshare.gestures.xposed;

import android.graphics.Insets;
import android.graphics.Rect;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class FreeformLaunchGeometryPolicyTest {
    @Test
    public void portraitUsesInsetSafeBoundsForSizeAndPlacement() {
        GestureConfig config = GestureConfig.defaults();
        config.portraitProportionalSizeEnabled = false;

        FreeformLaunchGeometryPolicy.Result result =
                FreeformLaunchGeometryPolicy.calculate(config,
                        new Rect(0, 0, 1000, 2000), Insets.NONE,
                        12, 1f, null);

        assertRect(new Rect(12, 12, 988, 1988), result.safeBounds);
        assertRect(result.safeBounds, result.placementBounds);
        int width = Math.round((result.safeBounds.right - result.safeBounds.left)
                * config.widthPercent / 100f);
        int height = Math.round((result.safeBounds.bottom - result.safeBounds.top)
                * config.heightPercent / 100f);
        int left = result.safeBounds.left
                + Math.round(((result.safeBounds.right - result.safeBounds.left) - width)
                * config.positionX / 100f);
        int top = result.safeBounds.top
                + Math.round(((result.safeBounds.bottom - result.safeBounds.top) - height)
                * config.positionY / 100f);
        assertRect(new Rect(left, top, left + width, top + height), result.visualBounds);
        assertRect(result.visualBounds, result.logicalBounds);
    }

    @Test
    public void landscapeUsesDisplayForSizeButEdgeInsetPlacement() {
        GestureConfig config = GestureConfig.defaults();
        config.landscapeWidthPercent = 50;
        config.landscapeHeightPercent = 50;
        config.landscapePositionX = 0;
        config.landscapePositionY = 0;
        config.landscapeProportionalSizeEnabled = false;

        FreeformLaunchGeometryPolicy.Result result =
                FreeformLaunchGeometryPolicy.calculate(config,
                        new Rect(0, 0, 2400, 1080), Insets.NONE,
                        12, 0.5f, null);

        assertRect(new Rect(12, 12, 2388, 1068), result.safeBounds);
        assertRect(new Rect(12, 12, 2388, 1068), result.placementBounds);
        assertNull(result.visualBounds);
        assertNull(result.logicalBounds);
    }

    @Test
    public void nativeScalingWithoutNativeBoundsPreservesNativeGeometry() {
        GestureConfig config = GestureConfig.defaults();
        config.portraitProportionalSizeEnabled = true;

        FreeformLaunchGeometryPolicy.Result result =
                FreeformLaunchGeometryPolicy.calculate(config,
                        new Rect(0, 0, 1000, 2000), Insets.NONE,
                        12, 0.7f, null);

        assertNull(result.visualBounds);
        assertNull(result.logicalBounds);
        assertRect(new Rect(12, 12, 988, 1988), result.safeBounds);
    }

    @Test
    public void visualBoundsScalesLogicalDimensionsAroundSameOrigin() {
        assertRect(new Rect(100, 200, 450, 800),
                FreeformLaunchGeometryPolicy.visualBounds(
                        new Rect(100, 200, 600, 1000), 0.7f));
    }

    private static void assertRect(Rect expected, Rect actual) {
        assertEquals(expected.left, actual.left);
        assertEquals(expected.top, actual.top);
        assertEquals(expected.right, actual.right);
        assertEquals(expected.bottom, actual.bottom);
    }
}
