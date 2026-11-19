package com.leaf.hyperdragshare.gestures.xposed;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class MiniMenuPressTest {
    private final MiniMenuPress press = new MiniMenuPress(new Handler(Looper.getMainLooper()), 10);
    private final AtomicInteger opens = new AtomicInteger();

    @Test public void opensAtDeadlineWhileDownAndConsumesOnlyThatTasksTap() {
        send(MotionEvent.ACTION_DOWN, 7, 20);
        waitFor(ViewConfiguration.getLongPressTimeout() - 1);
        assertEquals(0, opens.get());
        waitFor(1);
        assertEquals(1, opens.get());
        send(MotionEvent.ACTION_UP, 7, 20);
        assertFalse(press.consumeSingleTap(9));
        assertTrue(press.consumeSingleTap(7));
        assertFalse(press.consumeSingleTap(7));
    }

    @Test public void quickTapDragAndVanishingTaskDoNotOpen() {
        send(MotionEvent.ACTION_DOWN, 7, 20);
        send(MotionEvent.ACTION_UP, 7, 20);
        send(MotionEvent.ACTION_DOWN, 7, 20);
        send(MotionEvent.ACTION_MOVE, 7, 31);
        send(MotionEvent.ACTION_DOWN, 7, 20);
        press.cancelForTask(7);
        waitFor(ViewConfiguration.getLongPressTimeout());
        assertEquals(0, opens.get());
        assertFalse(press.consumeSingleTap(7));
    }

    private void send(int action, int taskId, float x) {
        MotionEvent event = MotionEvent.obtain(100, 110, action, x, 20, 0);
        try {
            press.accept(event, taskId, () -> opens.incrementAndGet() > 0);
        } finally {
            event.recycle();
        }
    }

    private static void waitFor(int millis) {
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(millis));
    }
}
