package com.leaf.hyperdragshare.gestures.xposed;

import com.leaf.hyperdragshare.gestures.config.ConfigContract;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GestureGeometryTest {
    @Test
    public void bottomGapZeroMatchesLegacyCornerBaselineAndInsetsDoNotShiftIt() {
        GestureGeometry.HotZoneBounds left = GestureGeometry.hotZoneBounds(
                1200, 2400, 24, 28, 24, 12, 12, 0, false);
        GestureGeometry.HotZoneBounds right = GestureGeometry.hotZoneBounds(
                1200, 2400, 24, 28, 24, 12, 12, 0, true);
        assertEquals(2112, left.top);
        assertEquals(2400, left.bottom);
        assertEquals(24, left.left);
        assertEquals(28, 1200 - right.right);

        for (int y : new int[]{2111, 2112, 2399, 2400}) {
            boolean expected = y >= 2112 && y <= 2400;
            assertEquals(expected, left.contains(24, y));
        }
    }

    @Test
    public void bottomGapPreservesHeightAndClipsSafelyOnSmallDisplays() {
        GestureGeometry.HotZoneBounds separated = GestureGeometry.hotZoneBounds(
                600, 1000, 0, 0, 80, 10, 10, 36, false);
        assertEquals(964, separated.bottom);
        assertEquals(100, separated.bottom - separated.top);
        assertFalse(separated.contains(30, 965));
        assertTrue(separated.contains(30, 963));

        GestureGeometry.HotZoneBounds clipped = GestureGeometry.hotZoneBounds(
                120, 90, 0, 0, 80, 20, 20, 500, true);
        assertEquals(96, clipped.left);
        assertEquals(120, clipped.right);
        assertEquals(80, clipped.top);
        assertEquals(90, clipped.bottom);
    }

    @Test
    public void detectsOnlyBottomCorners() {
        assertEquals(GestureGeometry.Corner.LEFT,
                GestureGeometry.cornerAt(20, 2580, 1200, 2608, 144, 183));
        assertEquals(GestureGeometry.Corner.RIGHT,
                GestureGeometry.cornerAt(1180, 2580, 1200, 2608, 144, 183));
        assertNull(GestureGeometry.cornerAt(600, 2580, 1200, 2608, 144, 183));
        assertNull(GestureGeometry.cornerAt(20, 2100, 1200, 2608, 144, 183));
    }

    @Test
    public void twentyPercentHeightIncludesBottomHandleAndStopsAtConfiguredTop() {
        float hotHeight = 2608 * 0.20f;
        assertEquals(GestureGeometry.Corner.LEFT,
                GestureGeometry.cornerAt(20, 2607, 1200, 2608, 144, hotHeight));
        assertEquals(GestureGeometry.Corner.LEFT,
                GestureGeometry.cornerAt(20, 2608 - hotHeight, 1200, 2608, 144, hotHeight));
        assertNull(GestureGeometry.cornerAt(
                20, 2608 - hotHeight - 1, 1200, 2608, 144, hotHeight));
    }

    @Test
    public void measuresDistanceFromOriginalDownPoint() {
        assertEquals(5f, GestureGeometry.distance(10, 10, 13, 14), 0.001f);
    }

    @Test
    public void mirrorsSelectionAcrossCorners() {
        GestureGeometry.Point point = GestureGeometry.iconCenter(
                GestureGeometry.Corner.LEFT, 2, 6, 1200, 2608, 696);
        int left = GestureGeometry.selection(GestureGeometry.Corner.LEFT,
                point.x, point.y, 1200, 2608, 6, 696, 46, 6);
        int right = GestureGeometry.selection(GestureGeometry.Corner.RIGHT,
                1200 - point.x, point.y, 1200, 2608, 6, 696, 46, 6);
        assertEquals(left, right);
        assertEquals(2, left);
    }

    @Test
    public void opensOnlyWhenPointerActuallyHitsIcon() {
        GestureGeometry.Point point = GestureGeometry.iconCenter(
                GestureGeometry.Corner.LEFT, 3, 6, 1200, 2608, 696);
        assertEquals(3, GestureGeometry.selection(GestureGeometry.Corner.LEFT,
                point.x + 20, point.y, 1200, 2608, 6, 696, 46, 6));
        assertEquals(-1, GestureGeometry.selection(GestureGeometry.Corner.LEFT,
                point.x + 80, point.y, 1200, 2608, 6, 696, 46, 6));
        assertEquals(-1, GestureGeometry.selection(GestureGeometry.Corner.LEFT,
                300, 2450, 1200, 2608, 6, 696, 46, 6));
    }

    @Test
    public void classifiesOutsideUsingActualWindowBounds() {
        assertEquals(GestureGeometry.OutsideRegion.INSIDE,
                GestureGeometry.outsideRegion(500, 1000, 200, 500, 900, 1800));
        assertEquals(GestureGeometry.OutsideRegion.OUTSIDE,
                GestureGeometry.outsideRegion(500, 300, 200, 500, 900, 1800));
        assertEquals(GestureGeometry.OutsideRegion.OUTSIDE,
                GestureGeometry.outsideRegion(500, 2000, 200, 500, 900, 1800));
        assertEquals(GestureGeometry.OutsideRegion.OUTSIDE,
                GestureGeometry.outsideRegion(100, 1000, 200, 500, 900, 1800));
    }

    @Test
    public void treatsOnlyActualImeRectangleAsPartOfFreeform() {
        assertTrue(GestureGeometry.insideEither(500, 1000,
                200, 500, 900, 1800,
                0, 1900, 1200, 2608, true));
        assertTrue(GestureGeometry.insideEither(500, 2300,
                200, 500, 900, 1800,
                0, 1900, 1200, 2608, true));
        assertFalse(GestureGeometry.insideEither(500, 1850,
                200, 500, 900, 1800,
                0, 1900, 1200, 2608, true));
        assertFalse(GestureGeometry.insideEither(500, 2300,
                200, 500, 900, 1800,
                0, 1900, 1200, 2608, false));
    }

    @Test
    public void growsRadiusBeforeShrinkingBelowMinimumIconSize() {
        float radius = GestureGeometry.effectiveRadius(8, 140, 28, 6);
        float diameter = GestureGeometry.effectiveIconDiameter(8, radius, 46, 28, 6);
        assertTrue(radius > 140);
        assertTrue(diameter >= 28);
        assertTrue(diameter <= 46);
    }

    @Test
    public void crowdedFanUsesUpToThreeSelectableRows() {
        assertEquals(1, GestureGeometry.fanRowCount(8));
        assertEquals(2, GestureGeometry.fanRowCount(9));
        assertEquals(3, GestureGeometry.fanRowCount(24));
        for (int index = 0; index < 24; index++) {
            GestureGeometry.Point point = GestureGeometry.iconCenter(
                    GestureGeometry.Corner.LEFT, index, 24, 1200, 2608, 696);
            assertEquals(index, GestureGeometry.selection(GestureGeometry.Corner.LEFT,
                    point.x, point.y, 1200, 2608, 24, 696, 42, 3));
        }
    }

    @Test
    public void fanAllocatesMoreIconsToTheRoomierOuterRows() {
        assertArrayEquals(new int[] {6, 7}, GestureGeometry.fanRowCounts(13));
        assertArrayEquals(new int[] {7, 7}, GestureGeometry.fanRowCounts(14));
        assertArrayEquals(new int[] {7, 8}, GestureGeometry.fanRowCounts(15));
        assertArrayEquals(new int[] {5, 6, 6}, GestureGeometry.fanRowCounts(17));
        assertArrayEquals(new int[] {8, 8, 8}, GestureGeometry.fanRowCounts(24));
        for (int count = 9; count <= 24; count++) {
            int total = 0;
            int previous = 0;
            for (int rowCount : GestureGeometry.fanRowCounts(count)) {
                assertTrue(rowCount >= previous);
                assertTrue(rowCount - previous <= 1 || previous == 0);
                previous = rowCount;
                total += rowCount;
            }
            assertEquals(count, total);
        }
    }

    @Test
    public void fixedSevenModeFillsEachRowBeforeStartingTheNext() {
        assertArrayEquals(new int[] {7}, GestureGeometry.fanRowCounts(7, true));
        assertArrayEquals(new int[] {7, 1}, GestureGeometry.fanRowCounts(8, true));
        assertArrayEquals(new int[] {7, 6}, GestureGeometry.fanRowCounts(13, true));
        assertArrayEquals(new int[] {7, 7}, GestureGeometry.fanRowCounts(14, true));
        assertArrayEquals(new int[] {7, 7, 1},
                GestureGeometry.fanRowCounts(15, true));
        assertArrayEquals(new int[] {7, 7, 7},
                GestureGeometry.fanRowCounts(21, true));
        assertArrayEquals(new int[] {7, 7, 7, 1},
                GestureGeometry.fanRowCounts(22, true));
        assertArrayEquals(new int[] {7, 7, 7, 3},
                GestureGeometry.fanRowCounts(24, true));
        assertEquals(4, GestureGeometry.fanRowCount(24, true));
    }

    @Test
    public void fixedSevenModeUsesTheSameCentersForDrawingAndHitTesting() {
        float radius = GestureGeometry.effectiveRadius(24, 580f, 28f, 6f, true);
        float diameter = GestureGeometry.effectiveIconDiameter(
                24, radius, 46f, 28f, 6f, true);
        for (int index = 0; index < 24; index++) {
            GestureGeometry.Point point = GestureGeometry.iconCenter(
                    GestureGeometry.Corner.RIGHT, index, 24, 1200, 2608,
                    radius, true);
            assertEquals(index, GestureGeometry.selection(
                    GestureGeometry.Corner.RIGHT, point.x, point.y,
                    1200, 2608, 24, radius, diameter, 3f, true));
        }
    }

    @Test
    public void customModeFillsInnerThenStartsMiddleAtTheLowestSlot() {
        int mode = ConfigContract.FAN_LAYOUT_CUSTOM;
        float radius = GestureGeometry.effectiveRadius(
                5, 580f, 28f, 6f, mode, 6, 5, 4);
        float diameter = GestureGeometry.effectiveIconDiameter(
                5, radius, 46f, 28f, 6f, mode, 6, 5, 4);
        GestureGeometry.Point innerFirst = GestureGeometry.iconCenter(
                GestureGeometry.Corner.LEFT, 0, 5, 1200, 2608, radius,
                mode, 6, 5, 4);
        GestureGeometry.Point middleFirst = GestureGeometry.iconCenter(
                GestureGeometry.Corner.LEFT, 4, 5, 1200, 2608, radius,
                mode, 6, 5, 4);
        float expectedInnerRadius = radius * 0.68f;
        assertEquals(expectedInnerRadius * (float) Math.cos(Math.toRadians(12)),
                innerFirst.x, 0.01f);
        assertEquals(2608f - expectedInnerRadius * (float) Math.sin(Math.toRadians(12)),
                innerFirst.y, 0.01f);
        float expectedMiddleRadius = radius * 0.88f;
        assertEquals(expectedMiddleRadius * (float) Math.cos(Math.toRadians(15.5)),
                middleFirst.x, 0.01f);
        assertEquals(2608f - expectedMiddleRadius * (float) Math.sin(Math.toRadians(15.5)),
                middleFirst.y, 0.01f);
        assertEquals(4, GestureGeometry.selection(GestureGeometry.Corner.LEFT,
                middleFirst.x, middleFirst.y, 1200, 2608, 5, radius, diameter, 3f,
                mode, 6, 5, 4));
    }

    @Test
    public void customModeSupportsZeroMiddleAndOuterRows() {
        int mode = ConfigContract.FAN_LAYOUT_CUSTOM;
        float radius = 580f;
        for (int index = 0; index < 5; index++) {
            GestureGeometry.Point point = GestureGeometry.iconCenter(
                    GestureGeometry.Corner.LEFT, index, 5, 1200, 2608, radius,
                    mode, 0, 0, 5);
            assertEquals(index, GestureGeometry.selection(
                    GestureGeometry.Corner.LEFT, point.x, point.y,
                    1200, 2608, 5, radius, 46f, 3f,
                    mode, 0, 0, 5));
        }
        GestureGeometry.Point firstMiddle = GestureGeometry.iconCenter(
                GestureGeometry.Corner.LEFT, 5, 7, 1200, 2608, radius,
                mode, 0, 2, 5);
        assertEquals(radius * 0.88f * (float) Math.cos(Math.toRadians(15.5)),
                firstMiddle.x, 0.01f);
    }

    @Test
    public void customModeMirrorsEveryVisualCenterAndHitTarget() {
        int mode = ConfigContract.FAN_LAYOUT_CUSTOM;
        float radius = GestureGeometry.effectiveRadius(
                18, 580f, 28f, 6f, mode, 7, 6, 5);
        float diameter = GestureGeometry.effectiveIconDiameter(
                18, radius, 46f, 28f, 6f, mode, 7, 6, 5);
        for (int index = 0; index < 18; index++) {
            GestureGeometry.Point left = GestureGeometry.iconCenter(
                    GestureGeometry.Corner.LEFT, index, 18, 1200, 2608, radius,
                    mode, 7, 6, 5);
            GestureGeometry.Point right = GestureGeometry.iconCenter(
                    GestureGeometry.Corner.RIGHT, index, 18, 1200, 2608, radius,
                    mode, 7, 6, 5);
            assertEquals(1200f, left.x + right.x, 0.01f);
            assertEquals(left.y, right.y, 0.01f);
            assertEquals(index, GestureGeometry.selection(GestureGeometry.Corner.LEFT,
                    left.x, left.y, 1200, 2608, 18, radius, diameter, 3f,
                    mode, 7, 6, 5));
            assertEquals(index, GestureGeometry.selection(GestureGeometry.Corner.RIGHT,
                    right.x, right.y, 1200, 2608, 18, radius, diameter, 3f,
                    mode, 7, 6, 5));
        }
    }

    @Test
    public void honeycombSafetyBoundaryStartsOutsideTheOutermostFanIcon() {
        int mode = ConfigContract.FAN_LAYOUT_CUSTOM;
        float radius = GestureGeometry.effectiveRadius(
                18, 580f, 28f, 6f, mode, 7, 6, 5);
        float diameter = GestureGeometry.effectiveIconDiameter(
                18, radius, 46f, 28f, 6f, mode, 7, 6, 5);
        float safetyMargin = 30f;
        float boundary = GestureGeometry.fanOutermostEdgeDistance(
                GestureGeometry.Corner.LEFT, 18, 1200, 2608, radius,
                diameter, safetyMargin, mode, 7, 6, 5);
        float outermostCenter = 0f;
        for (int index = 0; index < 18; index++) {
            GestureGeometry.Point point = GestureGeometry.iconCenter(
                    GestureGeometry.Corner.LEFT, index, 18, 1200, 2608,
                    radius, mode, 7, 6, 5);
            outermostCenter = Math.max(outermostCenter,
                    GestureGeometry.distance(0f, 2608f, point.x, point.y));
        }
        assertEquals(outermostCenter + diameter / 2f + safetyMargin,
                boundary, 0.01f);
        assertFalse(GestureGeometry.beyondFanEdge(GestureGeometry.Corner.LEFT,
                boundary - 1f, 2608f, 1200, 2608, boundary));
        assertTrue(GestureGeometry.beyondFanEdge(GestureGeometry.Corner.LEFT,
                boundary, 2608f, 1200, 2608, boundary));
        assertFalse(GestureGeometry.beyondFanEdge(GestureGeometry.Corner.RIGHT,
                1200f - boundary + 1f, 2608f, 1200, 2608, boundary));
        assertTrue(GestureGeometry.beyondFanEdge(GestureGeometry.Corner.RIGHT,
                1200f - boundary, 2608f, 1200, 2608, boundary));
    }

    @Test
    public void reservesBothEdgesAtEveryHeight() {
        assertTrue(GestureGeometry.inSystemEdgeReserve(20, 1200, 60, 70));
        assertTrue(GestureGeometry.inSystemEdgeReserve(1150, 1200, 60, 70));
        assertFalse(GestureGeometry.inSystemEdgeReserve(600, 1200, 60, 70));
    }

    @Test
    public void systemEdgeTapKeepsNaturalJitterButYieldsToIntentionalBackSwipe() {
        assertFalse(GestureGeometry.shouldYieldSystemBackEdge(
                true, 20, 1000, 42, 1024, 36, 54));
        assertFalse(GestureGeometry.shouldYieldSystemBackEdge(
                false, 1180, 1000, 1158, 1024, 36, 54));
        assertTrue(GestureGeometry.shouldYieldSystemBackEdge(
                true, 20, 1000, 70, 1008, 36, 54));
        assertTrue(GestureGeometry.shouldYieldSystemBackEdge(
                false, 1180, 1000, 1130, 992, 36, 54));
    }

    @Test
    public void systemEdgeTapYieldsAfterLargeNonBackMovement() {
        assertTrue(GestureGeometry.shouldYieldSystemBackEdge(
                true, 20, 1000, 35, 1060, 36, 54));
        assertTrue(GestureGeometry.shouldYieldSystemBackEdge(
                false, 1180, 1000, 1190, 1060, 36, 54));
    }

    @Test
    public void bottomCornersStayFlushWhileSystemEdgeUsesUpperBand() {
        assertEquals(GestureGeometry.Corner.LEFT,
                GestureGeometry.cornerAt(30, 2500, 1200, 2600, 96, 440));
        assertEquals(GestureGeometry.Corner.RIGHT,
                GestureGeometry.cornerAt(1170, 2500, 1200, 2600, 96, 440));
        assertEquals(GestureGeometry.Corner.LEFT,
                GestureGeometry.sideAt(20, 1200, 1200, 60, 60,
                        520, 2136, 96));
        assertNull(GestureGeometry.sideAt(20, 2500, 1200, 60, 60,
                520, 2136, 96));
        assertNull(GestureGeometry.sideAt(20, 400, 1200, 60, 60,
                520, 2136, 96));
    }

    @Test
    public void fanLayoutMatchesLegacySelectionForAllTargetCounts() {
        int[] counts = {1, 3, 8, 24, 36};
        for (GestureGeometry.Corner corner : GestureGeometry.Corner.values()) {
            for (int count : counts) {
                GestureGeometry.FanLayout layout = GestureGeometry.fanLayout(
                        corner, count, 1200, 2600, 560f, 0, 7, 6, 5);
                org.junit.Assert.assertEquals(count, layout.itemCount);
                for (int index = 0; index < count; index++) {
                    float x = layout.centerX[index];
                    float y = layout.centerY[index];
                    int legacy = GestureGeometry.selection(corner, x, y, 1200, 2600,
                            count, 560f, 1f, 0f, 0, 7, 6, 5);
                    org.junit.Assert.assertEquals(index, legacy);
                    int cached = layout.selection(x, y, 1f, 0f);
                    org.junit.Assert.assertEquals(index, cached);
                }
            }
        }
    }
}
