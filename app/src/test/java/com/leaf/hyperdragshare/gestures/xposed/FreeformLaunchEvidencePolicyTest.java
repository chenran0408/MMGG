package com.leaf.hyperdragshare.gestures.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class FreeformLaunchEvidencePolicyTest {
    @Test
    public void staleFailureDoesNotClearReplacementPendingGeneration() {
        assertEquals(false, HyperOsFreeformBridge.shouldClearPendingMatchForEvidence(8L, 7L));
        assertEquals(true, HyperOsFreeformBridge.shouldClearPendingMatchForEvidence(8L, 8L));
    }

    @Test
    public void stagesDescribeEvidenceWithoutClaimingLaunchSuccess() {
        FreeformLaunchEvidencePolicy policy = new FreeformLaunchEvidencePolicy();
        FreeformLaunchEvidencePolicy.Transition queued = policy.queued();
        assertEquals(FreeformLaunchEvidencePolicy.Stage.QUEUED, policy.stage());

        FreeformLaunchEvidencePolicy.Transition requested = policy.requested();
        assertEquals(FreeformLaunchEvidencePolicy.Stage.QUEUED, requested.previous);
        assertEquals(FreeformLaunchEvidencePolicy.Stage.REQUESTED, requested.stage);
        assertNull(policy.observed(queued.generation));
        assertEquals(FreeformLaunchEvidencePolicy.Stage.REQUESTED, policy.stage());
        FreeformLaunchEvidencePolicy.Transition observed = policy.observed(requested.generation);
        assertEquals(FreeformLaunchEvidencePolicy.Stage.REQUESTED, observed.previous);
        assertEquals(FreeformLaunchEvidencePolicy.Stage.OBSERVED, observed.stage);
        assertEquals(FreeformLaunchEvidencePolicy.Stage.OBSERVED, policy.stage());
        assertNull("task observation is evidence, not a success verdict",
                policy.unknown(requested.generation));
    }

    @Test
    public void unansweredRequestCanBecomeUnknownAndLateObservationIsIgnored() {
        FreeformLaunchEvidencePolicy policy = new FreeformLaunchEvidencePolicy();
        FreeformLaunchEvidencePolicy.Transition requested = policy.requested();

        FreeformLaunchEvidencePolicy.Transition unknown =
                policy.unknown(requested.generation);
        assertEquals(FreeformLaunchEvidencePolicy.Stage.REQUESTED, unknown.previous);
        assertEquals(FreeformLaunchEvidencePolicy.Stage.UNKNOWN, unknown.stage);
        assertEquals(FreeformLaunchEvidencePolicy.Stage.UNKNOWN, policy.stage());
        assertNull(policy.observed(requested.generation));
    }

    @Test
    public void replacementRequestRejectsLateEvidenceFromPreviousRequest() {
        FreeformLaunchEvidencePolicy policy = new FreeformLaunchEvidencePolicy();
        FreeformLaunchEvidencePolicy.Transition first = policy.requested();
        FreeformLaunchEvidencePolicy.Transition replacement = policy.queued();

        assertNull(policy.observed(first.generation));
        assertNull(policy.unknown(first.generation));
        assertEquals(FreeformLaunchEvidencePolicy.Stage.QUEUED, policy.stage());
        assertNotNull(policy.unknown(replacement.generation));
    }
}
