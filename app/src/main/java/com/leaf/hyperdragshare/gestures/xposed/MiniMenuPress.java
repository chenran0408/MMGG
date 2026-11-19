package com.leaf.hyperdragshare.gestures.xposed;

import android.os.Handler;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import java.util.function.BooleanSupplier;

/** A single hold on the ROM's already resolved mini task. */
final class MiniMenuPress {
    private final Handler handler;
    private final int slop;
    private Runnable pending;
    private int taskId = -1;
    private int suppressedTap = -1;
    private boolean opened;
    private float x, y;

    MiniMenuPress(Handler handler, int slop) {
        this.handler = handler;
        this.slop = slop;
    }

    synchronized void accept(MotionEvent event, int task, BooleanSupplier show) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancel();
            suppressedTap = -1;
            if (task < 0) return;
            taskId = task;
            x = event.getRawX();
            y = event.getRawY();
            pending = () -> fire(task, show);
            handler.postDelayed(pending, ViewConfiguration.getLongPressTimeout());
        } else if (opened && task == taskId && action == MotionEvent.ACTION_MOVE) {
            // Keep the opening hold owned by this menu until its real terminal event.
        } else if (task != taskId || event.getPointerCount() != 1
                || (action == MotionEvent.ACTION_MOVE
                && Math.hypot(event.getRawX() - x, event.getRawY() - y) > slop)
                || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (opened && action == MotionEvent.ACTION_UP && task == taskId) suppressedTap = task;
            cancel();
        }
    }

    private synchronized void fire(int task, BooleanSupplier show) {
        if (pending == null || task != taskId) return;
        pending = null;
        opened = show.getAsBoolean();
    }

    synchronized boolean consumeSingleTap(int task) {
        if (task < 0 || (task != suppressedTap && !(opened && task == taskId))) return false;
        suppressedTap = -1;
        return true;
    }

    synchronized boolean ownsOpeningMove(MotionEvent event, int task) {
        return opened && taskId == task && event.getActionMasked() == MotionEvent.ACTION_MOVE;
    }

    synchronized void cancelForTask(int task) {
        if (taskId == task) cancel();
        if (suppressedTap == task) suppressedTap = -1;
    }

    private void cancel() {
        if (pending != null) handler.removeCallbacks(pending);
        pending = null;
        taskId = -1;
        opened = false;
    }
}
