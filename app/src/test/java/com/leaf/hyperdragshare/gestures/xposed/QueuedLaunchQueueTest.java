package com.leaf.hyperdragshare.gestures.xposed;

import android.content.Intent;
import android.graphics.drawable.Drawable;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class QueuedLaunchQueueTest {
    private static RuntimeTarget target(String packageName) {
        return new RuntimeTarget(packageName, null, "label", (Drawable) null);
    }

    @Test public void queueingTargetReplacesExternalPayload() {
        QueuedLaunchQueue queue = new QueuedLaunchQueue();
        long first = queue.beginExternal("com.a", new Intent("action"), null, null, 7);
        long second = queue.beginTarget(target("com.b"), GestureConfig.defaults(), null, 9);

        assertNull(queue.current(first, 7));
        QueuedLaunchQueue.Request pending = queue.current(second, 9);
        assertNotNull(pending);
        assertEquals("com.b", pending.target.packageName);
        assertNull(pending.externalPackage);
    }

    @Test public void generationMismatchInvalidatesPendingRequest() {
        QueuedLaunchQueue queue = new QueuedLaunchQueue();
        long generation = queue.beginExternal("com.a", new Intent("action"), null, null, 3);
        queue.clear(generation);
        assertNull(queue.current(generation, 3));
    }

    @Test public void consumeExternalClearsPayloadAndAdvancesGeneration() {
        QueuedLaunchQueue queue = new QueuedLaunchQueue();
        long generation = queue.beginExternal("com.a", new Intent("action"), null, null, 5);
        QueuedLaunchQueue.Request pending = queue.consumeExternal(generation, 5);
        assertNotNull(pending);
        assertEquals("com.a", pending.externalPackage);
        assertNull(queue.current(generation, 5));
        assertNull(queue.consumeExternal(generation, 5));
    }

    @Test public void consumeTargetRequiresTargetPayload() {
        QueuedLaunchQueue queue = new QueuedLaunchQueue();
        long generation = queue.beginExternal("com.a", new Intent("action"), null, null, 5);
        assertNull(queue.consumeTarget(generation, 5));
        assertNotNull(queue.current(generation, 5));
    }

    @Test public void retryBookkeepingDelegatesToPolicy() {
        QueuedLaunchQueue queue = new QueuedLaunchQueue();
        long generation = queue.beginTarget(target("com.b"), GestureConfig.defaults(), null, 4);
        assertEquals(1, queue.nextRetry(generation, 4));
        assertEquals(2, queue.nextRetry(generation, 4));
        assertFalse(queue.isCurrent(generation + 1, 4));
    }

    @Test public void clearOnlyAppliesToCurrentGeneration() {
        QueuedLaunchQueue queue = new QueuedLaunchQueue();
        long first = queue.beginTarget(target("com.b"), GestureConfig.defaults(), null, 4);
        long second = queue.beginTarget(target("com.c"), GestureConfig.defaults(), null, 6);
        assertFalse(queue.clear(first));
        assertTrue(queue.clear(second));
        assertNull(queue.peek());
    }
}
