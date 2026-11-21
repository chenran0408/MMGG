package com.leaf.hyperdragshare.gestures.xposed;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;



import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The API 102 compatibility adapter uses Java reflection locally. Verify both
 * available vendor methods and the missing-method fallback paths.
 */
@RunWith(RobolectricTestRunner.class)
public final class MiniMediaControlsTest {

    @Test public void taskIdUsesAvailableMethodAndMissingValuesFallBack() {
        Object info = new Object() {
            public int getTaskId() { return 7; }
            public Object getScaledBounds() { return new Object(); }
        };
        assertEquals(7, MiniMediaControls.miniMediaTaskId(info));
        assertEquals(-1, MiniMediaControls.miniMediaTaskId(new Object()));
        assertEquals(-1, MiniMediaControls.miniMediaTaskId(null));
        assertNull(MiniMediaControls.miniMediaBounds(info));
        assertNull(MiniMediaControls.miniMediaBounds(null));
    }

    @Test public void packageNameFallsBackToEmptyWhenVendorReflectionIsUnavailable() {
        Object info = new Object() {
            public Object getTaskInfo() { return new Object(); }
        };
        assertEquals("", MiniMediaControls.miniMediaPackage(info));
        assertEquals("", MiniMediaControls.miniMediaPackage(null));
    }

    @Test public void visibilityFallsBackToVisibleWhenVendorReflectionIsUnavailable() {
        assertTrue(MiniMediaControls.isMiniMediaTaskVisible(new Object()));
        assertTrue(MiniMediaControls.isMiniMediaTaskVisible(null));
    }

    @Test public void booleanCallUsesAvailableMethodAndFallsBackOnMissingMethod() {
        Object info = new Object() {
            public boolean isMiniState() { return true; }
        };
        assertTrue(MiniMediaControls.booleanCall(null, "isMiniState", true));
        assertTrue(MiniMediaControls.booleanCall(info, "isMiniState", false));
        assertFalse(MiniMediaControls.booleanCall(new Object(), "isMiniState", false));
    }

    @Test public void gatedTaskStatesNeverCreateTheController() {
        MiniMediaControls controls = new MiniMediaControls(
                RuntimeEnvironment.getApplication(), null, null, null);

        controls.updateMiniMedia(null, enabledConfig());
        controls.updateMiniMedia(new Object() {
            public int getTaskId() { return 7; }
            public boolean isMiniState() { return false; }
        }, enabledConfig());
        controls.clearMiniMediaForTask(7);
        controls.refreshMiniMediaConfig(enabledConfig());
    }

    @Test public void miniMenuMountsOnlyWhenRequestedAndRejectsStaleButtons() {
        Context context = RuntimeEnvironment.getApplication();
        AtomicReference<ViewGroup> attached = new AtomicReference<>();
        AtomicInteger releases = new AtomicInteger();
        AtomicInteger actionTask = new AtomicInteger(-1);
        MiniFreeformQuickMenuController menu = new MiniFreeformQuickMenuController(context,
                actionTask::set, (target, root) -> {
                    attached.set((ViewGroup) root);
                    return releases::incrementAndGet;
                }, context.getResources());
        menu.update(7, new Rect(10, 150, 260, 250));
        assertNull("Task updates must not mount the menu", attached.get());
        assertTrue(menu.show(target(7), () -> false, () -> {
            actionTask.set(7);
            return false;
        }, false));
        ViewGroup buttons = menuButtons(attached.get());
        assertEquals(2, buttons.getChildCount());
        View stale = buttons.getChildAt(1);
        menu.update(7, new Rect(300, 150, 550, 250));
        assertTrue(menu.contains(310, 160));
        assertFalse(menu.contains(20, 160));
        Runnable outsideOldMenu = menu.outsideDismissal(20, 160);
        assertTrue(menu.show(target(9), () -> false, () -> {
            actionTask.set(9);
            return false;
        }, true));
        outsideOldMenu.run();
        assertTrue(menu.isShowingFor(9));
        stale.performClick();
        assertEquals(-1, actionTask.get());
        View audio = menuButtons(attached.get()).getChildAt(0);
        audio.performClick();
        assertEquals(9, actionTask.get());
        assertTrue("A failed mute keeps the current menu available", menu.isShowingFor(9));
        assertEquals("静音", audio.getContentDescription());
        menuButtons(attached.get()).getChildAt(1).performClick();
        assertEquals(9, actionTask.get());
        assertEquals(2, releases.get());
    }

    @Test public void tappingMenuBackgroundReleasesTheAttachedTouchSurfaceImmediately() {
        Activity activity = Robolectric.setupActivity(Activity.class);
        AtomicReference<ViewGroup> attached = new AtomicReference<>();
        AtomicInteger actions = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        var menu = new MiniFreeformQuickMenuController(activity, task -> actions.incrementAndGet(),
                (target, root) -> {
                    attached.set((ViewGroup) root);
                    activity.setContentView(root);
                    return () -> {
                        releases.incrementAndGet();
                        ((ViewGroup) root.getParent()).removeView(root);
                    };
                }, activity.getResources());
        assertTrue(menu.show(target(7), () -> false, () -> {
            actions.incrementAndGet();
            return true;
        }, false));
        ViewGroup root = attached.get();
        View staleMute = menuButtons(root).getChildAt(0);
        assertTrue(root.getChildAt(0).isAttachedToWindow());
        root.getChildAt(0).performClick();
        assertEquals(1, releases.get());
        assertFalse(menu.isShowingFor(7));
        assertFalse(root.isAttachedToWindow());
        staleMute.performClick();
        assertEquals(0, actions.get());
        activity.finish();
    }

    @Test public void unisolatedPackageGainDisablesOnlyTheMuteButton() {
        var app = RuntimeEnvironment.getApplication();
        var attached = new AtomicReference<ViewGroup>();
        var resized = new AtomicInteger(-1);
        var menu = new MiniFreeformQuickMenuController(app, resized::set, (target, root) -> {
            attached.set((ViewGroup) root);
            return () -> { };
        }, app.getResources());
        assertTrue(menu.show(target(7), () -> { throw new AssertionError("Shared gain must not be read"); },
                null, false));
        var buttons = menuButtons(attached.get());
        var mute = buttons.getChildAt(0);
        assertFalse(mute.isEnabled());
        assertEquals("无法单独静音此应用", mute.getContentDescription());
        buttons.getChildAt(1).performClick();
        assertEquals(7, resized.get());
    }

    @Test public void anotherTaskUpdateDoesNotRedirectTheOpenMenusMuteAction() {
        var controls = new MiniMediaControls(RuntimeEnvironment.getApplication(), null, null, null);
        var config = enabledConfig();
        controls.updateMiniMedia(new MiniTask(7, "audio.first"), config);
        controls.updateMiniMedia(new MiniTask(9, "audio.second"), config);
        Object player = new Object();
        float[] level = {.6f};
        MiniFreeformMediaController.Volume firstVolume = new MiniFreeformMediaController.Volume() {
            @Override public float read(String pkg) { return level[0]; }
            @Override public Object player(String pkg) { return player; }
            @Override public boolean write(String pkg, Object target, float value) {
                assertEquals("audio.first", pkg);
                level[0] = value;
                return true;
            }
        };
        assertTrue(controls.toggleMute(7, "audio.first", firstVolume));
        assertEquals(0f, level[0], 0f);
        controls.clearMiniMediaForTask(7);
        assertEquals(.6f, level[0], 0f);
    }

    public static final class MiniTask {
        private final int id;
        private final ActivityManager.RunningTaskInfo task =
                org.robolectric.util.ReflectionHelpers.newInstance(ActivityManager.RunningTaskInfo.class);

        MiniTask(int id, String pkg) {
            this.id = id;
            task.topActivity = new ComponentName(pkg, pkg + ".Main");
            org.robolectric.util.ReflectionHelpers.setField(task, "isVisible", true);
        }

        public int getTaskId() { return id; }
        public boolean isMiniState() { return true; }
        public Rect getScaledBounds() { return new Rect(10, 150, 250, 550); }
        public ActivityManager.RunningTaskInfo getTaskInfo() { return task; }
    }

    private static ViewGroup menuButtons(ViewGroup root) {
        return (ViewGroup) ((ViewGroup) root.getChildAt(0)).getChildAt(0);
    }

    private static MiniFreeformQuickMenuController.Target target(int id) {
        return new MiniFreeformQuickMenuController.Target(id, new Rect(0, 0, 480, 800),
                new Rect(10, 150, 250, 550), null, null, null);
    }
    private static GestureConfig enabledConfig() {
        GestureConfig config = GestureConfig.defaults();
        config.miniMediaControlsEnabled = true;
        return config;
    }
}
