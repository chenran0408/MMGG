package com.leaf.hyperdragshare.codex;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class PressReleaseModeTest {
    @Test public void classifiesBelowAndAtThreshold() {
        assertMode(PressReleaseMode.Mode.NONE, 0L, 500L);
        assertMode(PressReleaseMode.Mode.NONE, 499L, 500L);
        assertMode(PressReleaseMode.Mode.NORMAL, 500L, 500L);
    }

    @Test public void classifiesNormalUntilJustBelowTwiceThreshold() {
        assertMode(PressReleaseMode.Mode.NORMAL, 501L, 500L);
        assertMode(PressReleaseMode.Mode.NORMAL, 999L, 500L);
    }

    @Test public void classifiesAtAndAboveTwiceThresholdAsDragPreview() {
        assertMode(PressReleaseMode.Mode.DRAG_PREVIEW, 1000L, 500L);
        assertMode(PressReleaseMode.Mode.DRAG_PREVIEW, 1001L, 500L);
    }

    @Test public void rejectsNegativeDurationAndNonpositiveThreshold() {
        assertMode(PressReleaseMode.Mode.NONE, -1L, 500L);
        assertMode(PressReleaseMode.Mode.NONE, 0L, 0L);
        assertMode(PressReleaseMode.Mode.NONE, Long.MAX_VALUE, -1L);
    }

    @Test public void classifiesLongValuesWithoutDoublingThreshold() {
        long threshold = Long.MAX_VALUE / 2L + 1L;
        assertMode(PressReleaseMode.Mode.NORMAL, Long.MAX_VALUE - 1L, threshold);
        assertMode(PressReleaseMode.Mode.NORMAL, Long.MAX_VALUE, threshold);
        assertMode(PressReleaseMode.Mode.NORMAL, Long.MAX_VALUE, Long.MAX_VALUE);

        long thresholdWithRepresentableDouble = Long.MAX_VALUE / 3L;
        assertMode(PressReleaseMode.Mode.DRAG_PREVIEW, Long.MAX_VALUE,
                thresholdWithRepresentableDouble);
    }

    private static void assertMode(PressReleaseMode.Mode expected, long duration, long threshold) {
        assertEquals(expected, PressReleaseMode.classify(duration, threshold));
    }
}
