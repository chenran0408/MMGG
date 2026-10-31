package com.leaf.hyperdragshare.gestures.xposed;

import android.content.Context;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public final class MiniFreeformMediaHostTest {
    @Test public void placementStaysInsideDisplayAndOutsideMiniHitbox() {
        Rect display = new Rect(0, 0, 1000, 800);
        Rect mini = new Rect(300, 300, 700, 600);

        MiniFreeformMediaHost.Placement placement =
                MiniFreeformMediaHost.placement(display, mini, 180, 60);

        assertNotNull(placement);
        Rect overlay = placement.bounds();
        assertTrue(display.contains(overlay));
        assertFalse(Rect.intersects(mini, overlay));
        assertEquals(mini.bottom + 1, overlay.top);
    }

    @Test public void placementUsesTopOrSideWhenBottomHasNoRoom() {
        Rect display = new Rect(0, 0, 500, 500);
        Rect bottomMini = new Rect(50, 260, 450, 480);
        MiniFreeformMediaHost.Placement above =
                MiniFreeformMediaHost.placement(display, bottomMini, 100, 50);
        assertNotNull(above);
        assertFalse(Rect.intersects(bottomMini, above.bounds()));
        assertTrue(above.top + above.height <= bottomMini.top);

        Rect fullHeightMini = new Rect(180, 10, 320, 490);
        MiniFreeformMediaHost.Placement side =
                MiniFreeformMediaHost.placement(display, fullHeightMini, 80, 40);
        assertNotNull(side);
        assertFalse(Rect.intersects(fullHeightMini, side.bounds()));
        assertTrue(side.left >= fullHeightMini.right || side.left + side.width <= fullHeightMini.left);
    }

    @Test public void hostUsesSmallOverlayParamsAndRemoveIsIdempotent() {
        Context context = RuntimeEnvironment.getApplication();
        FakeWindowAccess access = new FakeWindowAccess(new Rect(0, 0, 1000, 800));
        MiniFreeformMediaHost host = new MiniFreeformMediaHost(context, access);
        FixedView controls = new FixedView(context, 180, 60);

        host.show(12, new Rect(300, 300, 700, 600), controls);
        flushMain();

        assertEquals(1, access.addCount);
        assertEquals(TYPE_APPLICATION_OVERLAY, access.params.type);
        assertTrue((access.params.flags & FLAG_NOT_FOCUSABLE) != 0);
        assertEquals(180, access.params.width);
        assertEquals(60, access.params.height);
        assertFalse(Rect.intersects(new Rect(300, 300, 700, 600), access.paramsBounds()));

        host.hide();
        host.hide();
        flushMain();
        assertEquals(1, access.removeCount);
        assertNull(access.attached);
    }

    @Test public void taskChangeDuringUpdateRemovesOldOverlay() {
        Context context = RuntimeEnvironment.getApplication();
        FakeWindowAccess access = new FakeWindowAccess(new Rect(0, 0, 500, 500));
        MiniFreeformMediaHost host = new MiniFreeformMediaHost(context, access);
        FixedView controls = new FixedView(context, 100, 40);

        host.show(1, new Rect(50, 50, 300, 300), controls);
        flushMain();
        host.update(2, new Rect(50, 50, 300, 300));
        flushMain();

        assertEquals(1, access.removeCount);
        assertNull(access.attached);
    }

    private static void flushMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static final class FixedView extends View {
        private final int width;
        private final int height;

        FixedView(Context context, int width, int height) {
            super(context);
            this.width = width;
            this.height = height;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(width, height);
        }
    }

    private static final class FakeWindowAccess implements MiniFreeformMediaHost.WindowAccess {
        private final Rect display;
        View attached;
        WindowManager.LayoutParams params;
        int addCount;
        int updateCount;
        int removeCount;

        FakeWindowAccess(Rect display) { this.display = new Rect(display); }

        @Override public Rect displayBounds() { return new Rect(display); }

        @Override public void addView(View view, WindowManager.LayoutParams params) {
            attached = view;
            this.params = params;
            addCount++;
        }

        @Override public void updateViewLayout(View view, WindowManager.LayoutParams params) {
            assertEquals(attached, view);
            this.params = params;
            updateCount++;
        }

        @Override public void removeViewImmediate(View view) {
            assertEquals(attached, view);
            attached = null;
            removeCount++;
        }

        Rect paramsBounds() {
            return new Rect(params.x, params.y,
                    params.x + params.width, params.y + params.height);
        }
    }
}
