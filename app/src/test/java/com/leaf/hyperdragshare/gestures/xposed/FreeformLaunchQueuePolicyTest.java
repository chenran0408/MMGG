package com.leaf.hyperdragshare.gestures.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FreeformLaunchQueuePolicyTest {
    @Test
    public void replacementInvalidatesOldCallbacksAndStartsFreshRetryBudget() {
        FreeformLaunchQueuePolicy policy = new FreeformLaunchQueuePolicy();
        long first = policy.begin(41);
        assertEquals(1, policy.nextRetry(first, 41));

        long replacement = policy.begin(42);
        assertFalse(policy.isCurrent(first, 41));
        assertEquals(-1, policy.nextRetry(first, 41));
        assertEquals(1, policy.nextRetry(replacement, 42));
    }

    @Test
    public void clearInvalidatesAlreadyPostedCallback() {
        FreeformLaunchQueuePolicy policy = new FreeformLaunchQueuePolicy();
        long generation = policy.begin(41);
        policy.clear(generation);

        assertFalse(policy.isCurrent(generation, 41));
        assertEquals(-1, policy.nextRetry(generation, 41));
    }

    @Test
    public void consumeInvalidatesCallbackBeforePayloadIsStarted() {
        FreeformLaunchQueuePolicy policy = new FreeformLaunchQueuePolicy();
        long generation = policy.begin(41);

        assertTrue(policy.consume(generation, 41));
        assertFalse(policy.isCurrent(generation, 41));
        assertFalse(policy.consume(generation, 41));
    }
}
