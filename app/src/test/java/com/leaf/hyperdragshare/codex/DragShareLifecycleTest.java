package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DragShareLifecycleTest {
    @Test
    public void beginningANewSessionInvalidatesOldAsyncCallbacks() {
        DragShareLifecycle lifecycle = new DragShareLifecycle();

        DragShareLifecycle.SessionToken oldSession = lifecycle.beginSession();
        assertTrue(lifecycle.isCurrent(oldSession));
        long oldVersion = lifecycle.version();
        DragShareLifecycle.SessionToken newSession = lifecycle.beginSession();

        assertFalse(lifecycle.isCurrent(oldSession));
        assertTrue(lifecycle.isCurrent(newSession));
        assertFalse(lifecycle.isVersionCurrent(oldVersion));
    }

    @Test
    public void delayedHostCancelCannotCrossIntoANewSession() {
        DragShareLifecycle lifecycle = new DragShareLifecycle();
        lifecycle.beginSession();
        long oldVersion = lifecycle.version();

        lifecycle.beginSession();

        assertFalse(lifecycle.isVersionCurrent(oldVersion));
        assertTrue(lifecycle.isVersionCurrent(lifecycle.version()));
    }

    @Test
    public void supersededPreviewExitCannotRemoveTheReplacementWindow() {
        DragShareLifecycle lifecycle = new DragShareLifecycle();
        DragShareLifecycle.PreviewExitToken oldExit = lifecycle.beginPreviewExit();
        DragShareLifecycle.PreviewExitToken newExit = lifecycle.beginPreviewExit();

        assertFalse(lifecycle.isCurrent(oldExit));
        assertTrue(lifecycle.isCurrent(newExit));

        lifecycle.cancelPreviewExit();
        assertFalse(lifecycle.isCurrent(newExit));
    }

    @Test
    public void destroyInvalidatesSessionsAndPendingExits() {
        DragShareLifecycle lifecycle = new DragShareLifecycle();
        DragShareLifecycle.SessionToken session = lifecycle.beginSession();
        DragShareLifecycle.PreviewExitToken exit = lifecycle.beginPreviewExit();

        lifecycle.destroy();

        assertFalse(lifecycle.isCurrent(session));
        assertFalse(lifecycle.isCurrent(exit));
        assertFalse(lifecycle.isVersionCurrent(lifecycle.version()));
        assertFalse(lifecycle.isCurrent(lifecycle.beginSession()));
    }
}
