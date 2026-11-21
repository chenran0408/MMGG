package com.leaf.hyperdragshare.gestures.xposed;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GestureArbitratorTest {
    @Test
    public void earlyClaimAcceptsSmallDiagonalButLeavesSystemDirectionsPending() {
        GestureArbitrator arbitrator = new GestureArbitrator();
        assertEquals(GestureArbitrator.Decision.PENDING,
                arbitrator.earlyClaim(GestureGeometry.Corner.LEFT, 0, 100, 0, 96, 2));
        assertEquals(GestureArbitrator.Decision.PENDING,
                arbitrator.earlyClaim(GestureGeometry.Corner.LEFT, 0, 100, 4, 100, 2));
        assertEquals(GestureArbitrator.Decision.FAN,
                arbitrator.earlyClaim(GestureGeometry.Corner.LEFT, 0, 100, 2, 98, 2));
        arbitrator.reset();
        assertEquals(GestureArbitrator.Decision.FAN,
                arbitrator.earlyClaim(GestureGeometry.Corner.RIGHT, 100, 100, 98, 98, 2));
    }

    @Test
    public void earlyClaimCannotReviveCancelledOrMissingCornerGesture() {
        GestureArbitrator arbitrator = new GestureArbitrator();
        assertEquals(GestureArbitrator.Decision.PENDING,
                arbitrator.earlyClaim(null, 0, 100, 4, 96, 2));
        arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, -12, 92, 10);
        assertEquals(GestureArbitrator.Decision.CANCELLED,
                arbitrator.earlyClaim(GestureGeometry.Corner.LEFT, 0, 100, 4, 96, 2));
    }

    @Test
    public void waitsUntilDecisionDistance() {
        GestureArbitrator arbitrator = new GestureArbitrator();
        assertEquals(GestureArbitrator.Decision.PENDING,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, 2, 94, 10));
    }

    @Test
    public void slightInwardStartCanDevelopIntoDiagonalAtEitherCorner() {
        for (GestureGeometry.Corner corner : GestureGeometry.Corner.values()) {
            GestureArbitrator arbitrator = new GestureArbitrator();
            int inward = corner == GestureGeometry.Corner.LEFT ? 1 : -1;
            assertEquals(GestureArbitrator.Decision.PENDING,
                    arbitrator.update(corner, 60, 100, 60 + inward, 89, 10));
            assertEquals(GestureArbitrator.Decision.PENDING,
                    arbitrator.earlyClaim(corner, 60, 100, 60 + inward, 89, 2));
            assertEquals(GestureArbitrator.Decision.FAN,
                    arbitrator.update(corner, 60, 100, 60 + 6 * inward, 84, 10));
        }
    }

    @Test
    public void sustainedNearlyVerticalSwipeYieldsAndCannotBeReclaimed() {
        GestureArbitrator arbitrator = new GestureArbitrator();
        assertEquals(GestureArbitrator.Decision.PENDING,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, 1, 81, 10));
        assertEquals(GestureArbitrator.Decision.CANCELLED,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, 1, 80, 10));
        assertEquals(GestureArbitrator.Decision.CANCELLED,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, 18, 66, 10));
    }

    @Test
    public void nearlyHorizontalSwipeStillYieldsAtFirstDecisionDistance() {
        GestureArbitrator arbitrator = new GestureArbitrator();
        assertEquals(GestureArbitrator.Decision.CANCELLED,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, 14, 99, 10));
    }

    @Test
    public void acceptsInwardDiagonalAcrossTheFanSweep() {
        GestureArbitrator arbitrator = new GestureArbitrator();
        assertEquals(GestureArbitrator.Decision.FAN,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, 40, 96, 10));

        arbitrator.reset();
        assertEquals(GestureArbitrator.Decision.FAN,
                arbitrator.update(GestureGeometry.Corner.RIGHT, 100, 100, 60, 96, 10));

        arbitrator.reset();
        assertEquals(GestureArbitrator.Decision.FAN,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, 5, 80, 10));

    }

    @Test
    public void rejectsHorizontalOutwardAndDownwardMotion() {
        GestureArbitrator arbitrator = new GestureArbitrator();
        assertEquals(GestureArbitrator.Decision.CANCELLED,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, 20, 100, 10));

        arbitrator.reset();
        assertEquals(GestureArbitrator.Decision.CANCELLED,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, -12, 92, 10));

        arbitrator.reset();
        assertEquals(GestureArbitrator.Decision.CANCELLED,
                arbitrator.update(GestureGeometry.Corner.RIGHT, 100, 100, 112, 92, 10));

        arbitrator.reset();
        assertEquals(GestureArbitrator.Decision.CANCELLED,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, 8, 112, 10));

        arbitrator.reset();
        // Pure vertical swipes from the corner should stay with the system home gesture.
        assertEquals(GestureArbitrator.Decision.CANCELLED,
                arbitrator.update(GestureGeometry.Corner.RIGHT, 100, 100, 100, 80, 10));
    }

    @Test
    public void cancelledDecisionCannotTurnIntoFanLater() {
        GestureArbitrator arbitrator = new GestureArbitrator();
        assertEquals(GestureArbitrator.Decision.CANCELLED,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, -12, 92, 10));
        assertEquals(GestureArbitrator.Decision.CANCELLED,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, 22, 40, 10));
    }

    @Test
    public void resetAllowsNextGestureToBeClaimed() {
        GestureArbitrator arbitrator = new GestureArbitrator();
        assertEquals(GestureArbitrator.Decision.CANCELLED,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, -12, 92, 10));
        arbitrator.reset();
        assertEquals(GestureArbitrator.Decision.FAN,
                arbitrator.update(GestureGeometry.Corner.LEFT, 0, 100, 20, 94, 10));
    }
}
