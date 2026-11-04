package com.leaf.hyperdragshare.gestures.xposed;

import android.content.Intent;

/**
 * Owns the pending after-suspend freeform request: either a fan target or an
 * external intent, plus its generation token. Payload mutations, generation
 * advances and retry bookkeeping happen atomically under one lock.
 */
final class QueuedLaunchQueue {
    static final class Request {
        final RuntimeTarget target;
        final String externalPackage;
        final Intent externalIntent;
        final GestureConfig config;
        final Runnable armedCallback;
        final int taskId;
        final long generation;

        private Request(RuntimeTarget target, String externalPackage, Intent externalIntent,
                        GestureConfig config, Runnable armedCallback, int taskId, long generation) {
            this.target = target;
            this.externalPackage = externalPackage;
            this.externalIntent = externalIntent;
            this.config = config;
            this.armedCallback = armedCallback;
            this.taskId = taskId;
            this.generation = generation;
        }
    }

    private final FreeformLaunchQueuePolicy policy = new FreeformLaunchQueuePolicy();

    private RuntimeTarget target;
    private GestureConfig targetConfig;
    private Runnable targetCallback;
    private String externalPackage;
    private Intent externalIntent;
    private GestureConfig externalConfig;
    private Runnable externalCallback;
    private int taskId = -1;
    private long generation;

    synchronized long beginTarget(RuntimeTarget newTarget, GestureConfig config,
                                  Runnable onLaunchArmed, int newTaskId) {
        target = newTarget;
        targetConfig = config;
        targetCallback = onLaunchArmed;
        externalPackage = null;
        externalIntent = null;
        externalConfig = null;
        externalCallback = null;
        taskId = newTaskId;
        generation = policy.begin(newTaskId);
        return generation;
    }

    synchronized long beginExternal(String packageName, Intent intent, GestureConfig config,
                                    Runnable onLaunchArmed, int newTaskId) {
        target = null;
        targetConfig = null;
        targetCallback = null;
        externalPackage = packageName;
        externalIntent = intent;
        externalConfig = config;
        externalCallback = onLaunchArmed;
        taskId = newTaskId;
        generation = policy.begin(newTaskId);
        return generation;
    }

    synchronized Request current(long expectedGeneration, int expectedTaskId) {
        if (expectedGeneration != generation || expectedTaskId != taskId || taskId < 0)
            return null;
        return snapshot(expectedGeneration);
    }

    synchronized Request peek() {
        if (taskId < 0 || (target == null && externalPackage == null))
            return null;
        return snapshot(generation);
    }

    synchronized Request consumeTarget(long expectedGeneration, int expectedTaskId) {
        Request pending = current(expectedGeneration, expectedTaskId);
        if (pending == null || pending.target == null || !policy.consume(expectedGeneration, expectedTaskId))
            return null;
        clearPayload();
        generation++;
        return pending;
    }

    synchronized Request consumeExternal(long expectedGeneration, int expectedTaskId) {
        Request pending = current(expectedGeneration, expectedTaskId);
        if (pending == null || pending.externalPackage == null
                || !policy.consume(expectedGeneration, expectedTaskId))
            return null;
        clearPayload();
        generation++;
        return pending;
    }

    synchronized boolean clear(long expectedGeneration) {
        if (expectedGeneration != generation)
            return false;
        policy.clear(expectedGeneration);
        clearPayload();
        generation++;
        return true;
    }

    synchronized int nextRetry(long expectedGeneration, int expectedTaskId) {
        return policy.nextRetry(expectedGeneration, expectedTaskId);
    }

    synchronized boolean isCurrent(long expectedGeneration, int expectedTaskId) {
        return policy.isCurrent(expectedGeneration, expectedTaskId);
    }

    private Request snapshot(long requestGeneration) {
        return new Request(target, externalPackage, externalIntent,
                target != null ? targetConfig : externalConfig,
                target != null ? targetCallback : externalCallback,
                taskId, requestGeneration);
    }

    private void clearPayload() {
        target = null;
        targetConfig = null;
        targetCallback = null;
        externalPackage = null;
        externalIntent = null;
        externalConfig = null;
        externalCallback = null;
        taskId = -1;
    }
}
