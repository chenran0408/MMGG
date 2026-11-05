package com.leaf.hyperdragshare.gestures.xposed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import org.junit.Test;

public final class FanRuntimeIntentFlagsTest {
    @Test public void externalIntentCannotForceNewOrClearTask() {
        int source = Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED;

        int sanitized = FanRuntime.sanitizeExternalIntentFlags(source);

        assertTrue((sanitized & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue((sanitized & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0);
        assertTrue((sanitized & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
        assertTrue((sanitized & Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) != 0);
        assertFalse((sanitized & Intent.FLAG_ACTIVITY_MULTIPLE_TASK) != 0);
        assertFalse((sanitized & Intent.FLAG_ACTIVITY_NEW_DOCUMENT) != 0);
        assertFalse((sanitized & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0);
    }
}
