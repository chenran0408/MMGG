package com.leaf.hyperdragshare.gestures.xposed;

/**
 * Serializes the identity and retry budget of a launch waiting for the current
 * freeform task to enter mini state.
 *
 * <p>The bridge owns the payload and calls this policy while holding its task
 * lock. A cleared or consumed generation invalidates callbacks that were
 * already posted to the main handler.</p>
 */
final class FreeformLaunchQueuePolicy {
    static final int MAX_RETRIES = 6;

    private long generation;
    private int taskId = -1;
    private int retries;

    long begin(int taskId) {
        this.taskId = taskId;
        retries = 0;
        return ++generation;
    }

    boolean isCurrent(long callbackGeneration, int callbackTaskId) {
        return callbackGeneration == generation
                && callbackTaskId == taskId
                && taskId >= 0;
    }

    int nextRetry(long callbackGeneration, int callbackTaskId) {
        if (!isCurrent(callbackGeneration, callbackTaskId) || retries >= MAX_RETRIES) {
            return -1;
        }
        return ++retries;
    }

    boolean consume(long callbackGeneration, int callbackTaskId) {
        if (!isCurrent(callbackGeneration, callbackTaskId)) return false;
        taskId = -1;
        retries = 0;
        ++generation;
        return true;
    }

    void clear(long callbackGeneration) {
        if (callbackGeneration != generation) return;
        taskId = -1;
        retries = 0;
        ++generation;
    }
}
