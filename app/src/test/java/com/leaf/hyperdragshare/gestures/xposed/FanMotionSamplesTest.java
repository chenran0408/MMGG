package com.leaf.hyperdragshare.gestures.xposed;

import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.leaf.hyperdragshare.gestures.xposed.FanRuntime.State.*;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public class FanMotionSamplesTest {
    private static final float DOWN_X = 60f;
    private static final float DOWN_Y = 100f;

    @Test public void aDiagonalFollowedBySteepMotionIsClaimedOnBothEdges() {
        for (GestureGeometry.Corner corner : GestureGeometry.Corner.values()) {
            int sign = corner == GestureGeometry.Corner.LEFT ? 1 : -1;
            assertTrajectory(corner, false, ARMED, CLAIMED,
                    DOWN_X + 9 * sign, 91, DOWN_X + 9 * sign, 50);
        }
    }

    @Test public void aVerticalSwipeThatAlreadyYieldedToHomeCannotBecomeAFan() {
        assertTrajectory(GestureGeometry.Corner.LEFT, true, ARMED, CANCELLED,
                DOWN_X, 86, DOWN_X + 20, 75);
    }

    @Test public void earlyDirectionClaimStillNeedsAnEligibleInputPathAndActivationDistance() {
        assertTrajectory(GestureGeometry.Corner.LEFT, true, ARMED, CLAIMED,
                DOWN_X + 3, 97, DOWN_X, 89);
        assertTrajectory(GestureGeometry.Corner.LEFT, false, ARMED, CANCELLED,
                DOWN_X + 3, 97, DOWN_X, 89);
    }

    @Test public void crossingActivationDistanceBeforeRetractingOpensTheFan() {
        for (FanRuntime.State initial : new FanRuntime.State[]{ARMED, CLAIMED}) {
            assertTrajectory(GestureGeometry.Corner.LEFT, false, initial, ACTIVE,
                    DOWN_X + 60, 40, DOWN_X + 18, 82);
        }
    }

    private static void assertTrajectory(GestureGeometry.Corner corner, boolean allowEarlyClaim,
                                         FanRuntime.State initial, FanRuntime.State expected,
                                         float firstX, float firstY, float lastX, float lastY) {
        MotionEvent first = MotionEvent.obtain(0, 4, MotionEvent.ACTION_MOVE, firstX, firstY, 0);
        MotionEvent last = MotionEvent.obtain(0, 12, MotionEvent.ACTION_MOVE, lastX, lastY, 0);
        try {
            GestureArbitrator arbiter = new GestureArbitrator();
            FanRuntime.State split = move(initial, arbiter, corner, allowEarlyClaim, first);
            if (split == ARMED || split == CLAIMED) {
                split = move(split, arbiter, corner, allowEarlyClaim, last);
            }
            assertEquals("separate MOVE events", expected, split);

            first.addBatch(last.getEventTime(), lastX, lastY, 1f, 1f, 0);
            assertEquals(1, first.getHistorySize());
            assertEquals("one MOVE containing the same samples", split,
                    move(initial, new GestureArbitrator(), corner, allowEarlyClaim, first));
        } finally {
            first.recycle();
            last.recycle();
        }
    }

    private static FanRuntime.State move(FanRuntime.State state, GestureArbitrator arbiter,
                                         GestureGeometry.Corner corner, boolean allowEarlyClaim,
                                         MotionEvent event) {
        return FanRuntime.advanceCornerMove(state, event, arbiter, corner, DOWN_X, DOWN_Y,
                10f, allowEarlyClaim, 2f, 70f);
    }
}
