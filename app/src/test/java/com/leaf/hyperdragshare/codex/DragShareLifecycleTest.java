package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DragShareLifecycleTest {
    @Test public void newSessionInvalidatesOldCallbacks() {
        DragShareLifecycle lifecycle = new DragShareLifecycle();
        DragShareLifecycle.SessionToken oldToken = lifecycle.beginSession();
        DragShareLifecycle.SessionToken newToken = lifecycle.beginSession();
        assertFalse(lifecycle.isCurrent(oldToken));
        assertTrue(lifecycle.isCurrent(newToken));
    }

    @Test public void replacementExitInvalidatesOldFinish() {
        DragShareLifecycle lifecycle = new DragShareLifecycle();
        DragShareLifecycle.PreviewExitToken oldToken = lifecycle.beginPreviewExit();
        DragShareLifecycle.PreviewExitToken newToken = lifecycle.beginPreviewExit();
        assertFalse(lifecycle.isCurrent(oldToken));
        assertTrue(lifecycle.isCurrent(newToken));
        lifecycle.cancelPreviewExit();
        assertFalse(lifecycle.isCurrent(newToken));
    }

    @Test public void destroyInvalidatesBothKindsOfCallback() {
        DragShareLifecycle lifecycle = new DragShareLifecycle();
        DragShareLifecycle.SessionToken session = lifecycle.beginSession();
        DragShareLifecycle.PreviewExitToken exit = lifecycle.beginPreviewExit();
        lifecycle.destroy();
        assertFalse(lifecycle.isCurrent(session));
        assertFalse(lifecycle.isCurrent(exit));
    }
}
