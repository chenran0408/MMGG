package com.leaf.hyperdragshare.codex;

import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class DragMenuDirectionTest {
    @Test public void nearHandMovesToDraggedSideBeforeTargetsFinishLoading() throws Exception {
        DragShareController controller = new DragShareController(RuntimeEnvironment.getApplication());
        var update = DragShareController.class.getDeclaredMethod("updateGestureMenuPosition", float.class, float.class);
        update.setAccessible(true);
        field("configuredSimpleMenuPosition").setInt(controller, DragShareSettings.SIMPLE_MENU_POSITION_NEAR_HAND);
        for (int style = 0; style < 4; style++) {
            Bundle values = new Bundle();
            values.putInt("ui_style", style);
            field("settings").set(controller, DragShareSettings.fromBundle(values));
            field("menuDragOriginX").setFloat(controller, 100f);
            field("simpleMenuPosition").setInt(controller, DragShareSettings.SIMPLE_MENU_POSITION_LEFT);
            update.invoke(controller, 300f, 400f);
            assertEquals("right drag while still in the left screen half, style=" + style,
                    DragShareSettings.SIMPLE_MENU_POSITION_RIGHT, field("simpleMenuPosition").getInt(controller));
            update.invoke(controller, 98f, 400f);
            assertEquals("jitter near origin retains the selected side",
                    DragShareSettings.SIMPLE_MENU_POSITION_RIGHT, field("simpleMenuPosition").getInt(controller));
            field("menuDragOriginX").setFloat(controller, 900f);
            update.invoke(controller, 700f, 400f);
            assertEquals("left drag while still in the right screen half",
                    DragShareSettings.SIMPLE_MENU_POSITION_LEFT, field("simpleMenuPosition").getInt(controller));
        }
        controller.destroy();
    }

    @Test public void positionPolicyUsesFixedSidesNearHandAndFollowEdgeAcrossStyles() {
        for (int style = 0; style < 4; style++) {
            Bundle values = new Bundle();
            values.putInt("ui_style", style);
            for (int edge = DragShareSettings.SIMPLE_MENU_POSITION_TOP;
                    edge <= DragShareSettings.SIMPLE_MENU_POSITION_RIGHT; edge++) {
                values.putInt("simple_menu_position", edge);
                int configured = DragShareSettings.fromBundle(values).simpleMenuPosition;
                assertEquals(edge, GestureMath.initialMenuPosition(configured, 200f, 1000));
                assertFalse(GestureMath.followsDragEdge(configured));
            }
            values.putInt("simple_menu_position", DragShareSettings.SIMPLE_MENU_POSITION_NEAR_HAND);
            int nearHand = DragShareSettings.fromBundle(values).simpleMenuPosition;
            assertEquals(DragShareSettings.SIMPLE_MENU_POSITION_LEFT,
                    GestureMath.initialMenuPosition(nearHand, 100f, 1000));
            assertEquals(DragShareSettings.SIMPLE_MENU_POSITION_RIGHT,
                    GestureMath.initialMenuPosition(nearHand, 900f, 1000));
            values.putInt("simple_menu_position", DragShareSettings.SIMPLE_MENU_POSITION_DRAG_EDGE);
            int follow = DragShareSettings.fromBundle(values).simpleMenuPosition;
            assertEquals(DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM,
                    GestureMath.initialMenuPosition(follow, 500f, 1000));
            assertTrue(GestureMath.followsDragEdge(follow));
        }
    }

    @Test public void displayRefreshPreservesSelectedEdgeForEveryStyle() throws Exception {
        DragShareController controller = new DragShareController(RuntimeEnvironment.getApplication());
        Field direction = field("simpleMenuPosition");
        Field settings = field("settings");
        var refresh = DragShareController.class.getDeclaredMethod("refreshDisplayGeometry");
        refresh.setAccessible(true);
        for (int style = 0; style < 4; style++) {
            Bundle values = new Bundle();
            values.putInt("ui_style", style);
            settings.set(controller, DragShareSettings.fromBundle(values));
            for (int edge = 0; edge < 4; edge++) {
                direction.setInt(controller, edge);
                refresh.invoke(controller);
                assertEquals("style=" + style + ", edge=" + edge,
                        edge, direction.getInt(controller));
            }
        }
    }

    @Test public void bottomAreaDoesNotForceAnOpenMenuClosed() throws Exception {
        DragShareController controller = new DragShareController(RuntimeEnvironment.getApplication());
        Bundle values = new Bundle();
        values.putBoolean("close_menu_when_pointer_leaves", false);
        field("settings").set(controller, DragShareSettings.fromBundle(values));
        field("active").setBoolean(controller, true);
        field("screenWidth").setInt(controller, 1000);
        field("screenHeight").setInt(controller, 2000);
        field("bottomInset").setInt(controller, 0);
        field("configuredSimpleMenuPosition").setInt(controller, DragShareSettings.SIMPLE_MENU_POSITION_TOP);
        field("simpleMenuPosition").setInt(controller, DragShareSettings.SIMPLE_MENU_POSITION_TOP);
        field("menuShown").setBoolean(controller, true);
        var move = DragShareController.class.getDeclaredMethod("handlePointerOnMain", float.class, float.class);
        move.setAccessible(true);
        try {
            for (float x : new float[]{100f, 500f, 900f}) {
                field("menuShown").setBoolean(controller, true);
                move.invoke(controller, x, 1990f);
                assertTrue("bottom-area x=" + x + " must not force the menu closed",
                        field("menuShown").getBoolean(controller));
            }
        } finally {
            controller.destroy();
        }
    }

    @Test public void closeWhenLeavesStillClosesMenuAfterPointerExitsItsZone() throws Exception {
        DragShareController controller = new DragShareController(RuntimeEnvironment.getApplication());
        Bundle values = new Bundle();
        values.putBoolean("close_menu_when_pointer_leaves", true);
        field("settings").set(controller, DragShareSettings.fromBundle(values));
        field("active").setBoolean(controller, true);
        field("screenWidth").setInt(controller, 1000);
        field("screenHeight").setInt(controller, 2000);
        field("bottomInset").setInt(controller, 0);
        field("configuredSimpleMenuPosition").setInt(controller, DragShareSettings.SIMPLE_MENU_POSITION_TOP);
        field("simpleMenuPosition").setInt(controller, DragShareSettings.SIMPLE_MENU_POSITION_TOP);
        field("menuShown").setBoolean(controller, true);
        field("menuLeft").setInt(controller, 300);
        field("menuTop").setInt(controller, 300);
        field("menuWidth").setInt(controller, 400);
        field("menuHeight").setInt(controller, 200);
        var move = DragShareController.class.getDeclaredMethod("handlePointerOnMain", float.class, float.class);
        move.setAccessible(true);
        try {
            move.invoke(controller, 900f, 1000f);
            assertFalse("the regular close-on-leave setting must still close the menu",
                    field("menuShown").getBoolean(controller));
        } finally {
            controller.destroy();
        }
    }

    private static Field field(String name) throws Exception {
        Field field = DragShareController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
