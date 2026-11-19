package com.leaf.hyperdragshare.gestures.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Handler;
import android.os.Looper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public final class FullscreenLaunchAfterCloseTest {
    private final FullscreenLaunchAfterClose launcher =
            new FullscreenLaunchAfterClose(new Handler(Looper.getMainLooper()));
    private final List<String> launches = new ArrayList<>();

    @Test public void waitsOnlyForTheOutgoingTaskAndLaunchesOnce() {
        launcher.launch(42, task -> {
            assertEquals(42, task);
            return true;
        }, () -> launches.add("target"));
        launcher.onTaskVanished(17);
        ShadowLooper.shadowMainLooper().idle();
        assertTrue(launches.isEmpty());

        launcher.onTaskVanished(42);
        launcher.onTaskVanished(42);
        ShadowLooper.shadowMainLooper().idle();
        assertEquals(List.of("target"), launches);
        waitForClose();
        assertEquals(List.of("target"), launches);
    }

    @Test public void latestSelectionReusesAnAlreadyRunningClose() {
        launcher.launch(42, task -> true, () -> launches.add("old"));
        launcher.launch(-1, task -> {
            throw new AssertionError("The outgoing task is already closing");
        }, () -> launches.add("latest"));
        assertTrue(launches.isEmpty());

        launcher.onTaskVanished(42);
        ShadowLooper.shadowMainLooper().idle();
        assertEquals(List.of("latest"), launches);
        waitForClose();
        assertEquals(List.of("latest"), launches);
    }

    @Test public void missingCallbackKeepsTheExistingBoundedWait() {
        launcher.launch(42, task -> true, () -> launches.add("target"));
        ShadowLooper.shadowMainLooper().idleFor(
                Duration.ofMillis(FullscreenLaunchAfterClose.CLOSE_TIMEOUT_MS - 1));
        assertTrue(launches.isEmpty());

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(1));
        assertEquals(List.of("target"), launches);
        launcher.onTaskVanished(42);
        ShadowLooper.shadowMainLooper().idle();
        assertEquals(List.of("target"), launches);
    }

    @Test public void noOutgoingTaskOrFailedCloseStartsImmediately() {
        launcher.launch(-1, task -> {
            throw new AssertionError("There is no outgoing task");
        }, () -> launches.add("no task"));
        launcher.launch(42, task -> false, () -> launches.add("close failed"));
        assertEquals(List.of("no task", "close failed"), launches);
        waitForClose();
        assertEquals(List.of("no task", "close failed"), launches);
    }

    private static void waitForClose() {
        ShadowLooper.shadowMainLooper().idleFor(
                Duration.ofMillis(FullscreenLaunchAfterClose.CLOSE_TIMEOUT_MS));
    }
}
