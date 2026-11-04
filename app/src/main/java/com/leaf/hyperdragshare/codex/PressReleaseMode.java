package com.leaf.hyperdragshare.codex;

/** Press outcome chosen by gesture kind: two-finger segments, single finger drags. */
public final class PressReleaseMode {
    public enum Mode {
        NONE,
        NORMAL,
        DRAG_PREVIEW
    }

    private PressReleaseMode() {}

    public static Mode resolve(boolean twoFinger) {
        return twoFinger ? Mode.NORMAL : Mode.DRAG_PREVIEW;
    }
}
