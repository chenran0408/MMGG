package com.leaf.hyperdragshare.gestures.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ConfigContractTest {
    @Test
    public void hotZoneBottomGapDefaultsToLegacyPositionAndHasBoundedDpRange() {
        assertEquals("hot_bottom_gap_dp", ConfigContract.KEY_HOT_BOTTOM_GAP_DP);
        assertEquals(0, ConfigContract.DEFAULT_HOT_BOTTOM_GAP_DP);
        assertEquals(0, ConfigContract.MIN_HOT_BOTTOM_GAP_DP);
        assertEquals(200, ConfigContract.MAX_HOT_BOTTOM_GAP_DP);
    }

    @Test
    public void doublesFanAnimationAdjustmentCeilings() {
        assertEquals(130, ConfigContract.MAX_FAN_REVEAL_AMOUNT);
        assertEquals(90, ConfigContract.MAX_FAN_ROTATION_DEGREES);
        assertEquals(40, ConfigContract.MAX_FAN_SELECTION_SCALE_PERCENT);
    }

    @Test
    public void keepsHoneycombDiscSizingWithinTheScreen() {
        assertEquals(50, ConfigContract.MIN_HONEYCOMB_DISC_SIZE_PERCENT);
        assertEquals(66, ConfigContract.DEFAULT_HONEYCOMB_DISC_SIZE_PERCENT);
        assertEquals(100, ConfigContract.MAX_HONEYCOMB_DISC_SIZE_PERCENT);
        assertEquals(false, ConfigContract.DEFAULT_HONEYCOMB_SHOW_SELECTED_NAME);
    }

    @Test
    public void orientationDefaultsMatchTheReferencePhone() {
        assertEquals(true, ConfigContract.DEFAULT_BOTTOM_PORTRAIT_ENABLED);
        assertEquals(true, ConfigContract.DEFAULT_BOTTOM_LANDSCAPE_ENABLED);
        assertEquals(true, ConfigContract.DEFAULT_BOTTOM_LEFT_ENABLED);
        assertEquals(true, ConfigContract.DEFAULT_BOTTOM_RIGHT_ENABLED);
        assertEquals(true, ConfigContract.DEFAULT_BOTTOM_PORTRAIT_SECOND_STAGE_ENABLED);
        assertEquals(false, ConfigContract.DEFAULT_BOTTOM_LANDSCAPE_SECOND_STAGE_ENABLED);
        assertEquals(false, ConfigContract.DEFAULT_HONEYCOMB_LANDSCAPE_ENABLED);
        assertEquals(false, ConfigContract.DEFAULT_FAN_FIXED_SEVEN_ROWS);
    }

    @Test
    public void landscapeWindowAndFixedHoneycombDefaultsMatchTheDesign() {
        assertEquals(24, ConfigContract.DEFAULT_LANDSCAPE_WIDTH_PERCENT);
        assertEquals(20, ConfigContract.MIN_LANDSCAPE_WIDTH_PERCENT);
        assertEquals(90, ConfigContract.MAX_LANDSCAPE_WIDTH_PERCENT);
        assertEquals(85, ConfigContract.DEFAULT_LANDSCAPE_HEIGHT_PERCENT);
        assertEquals(20, ConfigContract.DEFAULT_LANDSCAPE_POSITION_X);
        assertEquals(50, ConfigContract.DEFAULT_LANDSCAPE_POSITION_Y);
        assertEquals(50, ConfigContract.DEFAULT_HONEYCOMB_FIXED_X_PERCENT);
        assertEquals(62, ConfigContract.DEFAULT_HONEYCOMB_FIXED_Y_PERCENT);
        assertEquals(false, ConfigContract.DEFAULT_BOTTOM_HONEYCOMB_FREEFORM);
        assertEquals(false, ConfigContract.DEFAULT_BOTTOM_FULLSCREEN);
    }

    @Test
    public void interactionDefaultsMatchTheReferencePhone() {
        assertEquals(20, ConfigContract.DEFAULT_TRIGGER_PERCENT);
        assertEquals(30, ConfigContract.DEFAULT_SELECTION_RADIUS_PERCENT);
        assertEquals(34, ConfigContract.DEFAULT_ICON_SIZE_DP);
        assertEquals(85, ConfigContract.DEFAULT_WIDTH_PERCENT);
        assertEquals(65, ConfigContract.DEFAULT_HEIGHT_PERCENT);
        assertEquals(180, ConfigContract.DEFAULT_OUTSIDE_TAP_WINDOW_MS);
        assertEquals(true, ConfigContract.DEFAULT_OUTSIDE_ENABLED);
        assertEquals(true, ConfigContract.DEFAULT_CUSTOM_WINDOW_PORTRAIT_ENABLED);
        assertEquals(true, ConfigContract.DEFAULT_CUSTOM_WINDOW_LANDSCAPE_ENABLED);
        assertEquals(true, ConfigContract.DEFAULT_PORTRAIT_PROPORTIONAL_SIZE_ENABLED);
        assertEquals(true, ConfigContract.DEFAULT_LANDSCAPE_PROPORTIONAL_SIZE_ENABLED);
        assertEquals(50, ConfigContract.MIN_NATIVE_WINDOW_SCALE_PERCENT);
        assertEquals(100, ConfigContract.DEFAULT_NATIVE_WINDOW_SCALE_PERCENT);
        assertEquals(150, ConfigContract.MAX_NATIVE_WINDOW_SCALE_PERCENT);
        assertEquals(true, ConfigContract.DEFAULT_OUTSIDE_PORTRAIT_ENABLED);
        assertEquals(true, ConfigContract.DEFAULT_OUTSIDE_LANDSCAPE_ENABLED);
        assertEquals(2,
                ConfigContract.DEFAULT_OUTSIDE_DOUBLE_ACTION);
        assertEquals(true, ConfigContract.DEFAULT_HONEYCOMB_ENABLED);
        assertEquals(true, ConfigContract.DEFAULT_HONEYCOMB_CENTERED_SYSTEM_ANIMATION);
        assertEquals(1,
                ConfigContract.DEFAULT_HONEYCOMB_MODE);
    }

    @Test
    public void fanSelectionDistanceCanStartAtOnePercent() {
        assertEquals(1, ConfigContract.MIN_SELECTION_RADIUS_PERCENT);
        assertEquals(150, ConfigContract.MAX_SELECTION_RADIUS_PERCENT);
    }

    @Test
    public void edgePinIsASeparateOutsideAction() {
        assertEquals(4, ConfigContract.ACTION_EDGE_PIN);
    }

    @Test
    public void circularIconsRemainTheCompatibilityDefault() {
        assertEquals(true, ConfigContract.DEFAULT_FORCE_CIRCULAR_ICONS);
    }
}
