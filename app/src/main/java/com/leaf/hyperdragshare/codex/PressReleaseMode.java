package com.leaf.hyperdragshare.codex;

/** Duration-based action chosen when a press is released. */
public final class PressReleaseMode {
    public enum Mode {
        NONE,
        NORMAL,
        DRAG_PREVIEW
    }

    private PressReleaseMode() {}

    public static Mode classify(long durationMillis, long thresholdMillis) {
        if (durationMillis < 0L || thresholdMillis <= 0L || durationMillis < thresholdMillis) {
            return Mode.NONE;
        }
        return durationMillis - thresholdMillis >= thresholdMillis
                ? Mode.DRAG_PREVIEW
                : Mode.NORMAL;
    }
}
