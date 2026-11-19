package com.leaf.hyperdragshare.gestures.xposed;

import android.os.Handler;
import android.os.Looper;

import java.util.function.IntPredicate;

/** Continues a fullscreen launch when its outgoing task vanishes or the close wait expires. */
final class FullscreenLaunchAfterClose {
    static final long CLOSE_TIMEOUT_MS = 220L;

    private final Handler handler;
    private volatile Request pending;

    FullscreenLaunchAfterClose(Handler handler) {
        this.handler = handler;
    }

    void launch(int taskId, IntPredicate closeTask, Runnable launch) {
        if (Looper.myLooper() != handler.getLooper()) {
            handler.post(() -> launch(taskId, closeTask, launch));
            return;
        }
        Request previous = pending;
        if (previous != null) {
            if (taskId < 0 || previous.taskId == taskId) {
                // Closing cleared the tracked task before the Shell callback arrived.
                previous.launch = launch;
                return;
            }
            handler.removeCallbacks(previous);
        } else if (taskId < 0) {
            launch.run();
            return;
        }

        Request request = new Request(taskId, launch);
        pending = request;
        if (closeTask.test(taskId)) {
            handler.postDelayed(request, CLOSE_TIMEOUT_MS);
        } else {
            request.run();
        }
    }

    void onTaskVanished(int taskId) {
        Request request = pending;
        if (request != null && request.taskId == taskId) handler.post(request);
    }

    private final class Request implements Runnable {
        final int taskId;
        Runnable launch;

        Request(int taskId, Runnable launch) {
            this.taskId = taskId;
            this.launch = launch;
        }

        @Override public void run() {
            if (pending != this) return;
            pending = null;
            handler.removeCallbacks(this);
            launch.run();
        }
    }
}
