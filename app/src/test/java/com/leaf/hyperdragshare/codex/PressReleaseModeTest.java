package com.leaf.hyperdragshare.codex;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class PressReleaseModeTest {
    @Test public void singleFingerPressRoutesToDragPreview() {
        assertEquals(PressReleaseMode.Mode.DRAG_PREVIEW, PressReleaseMode.resolve(false));
    }

    @Test public void twoFingerPressRoutesToNormalSegmentation() {
        assertEquals(PressReleaseMode.Mode.NORMAL, PressReleaseMode.resolve(true));
    }
}
