package com.leaf.hyperdragshare.gestures.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FreeformLaunchEvidencePolicyTest {
    @Test
    public void stagesDescribeEvidenceWithoutClaimingLaunchSuccess() {
        FreeformLaunchEvidencePolicy policy = new FreeformLaunchEvidencePolicy();
        long queued = policy.queued();
        assertEquals(FreeformLaunchEvidencePolicy.Stage.QUEUED, policy.stage());

        long requested = policy.requested();
        assertFalse(policy.observed(queued));
        assertEquals(FreeformLaunchEvidencePolicy.Stage.REQUESTED, policy.stage());
        assertTrue(policy.observed(requested));
        assertEquals(FreeformLaunchEvidencePolicy.Stage.OBSERVED, policy.stage());
        assertFalse("task observation is evidence, not a success verdict",
                policy.unknown(requested));
    }

    @Test
    public void unansweredRequestCanBecomeUnknownAndLateObservationIsIgnored() {
        FreeformLaunchEvidencePolicy policy = new FreeformLaunchEvidencePolicy();
        long requested = policy.requested();

        assertTrue(policy.unknown(requested));
        assertEquals(FreeformLaunchEvidencePolicy.Stage.UNKNOWN, policy.stage());
        assertFalse(policy.observed(requested));
    }

    @Test
    public void replacementRequestRejectsLateEvidenceFromPreviousRequest() {
        FreeformLaunchEvidencePolicy policy = new FreeformLaunchEvidencePolicy();
        long first = policy.requested();
        long replacement = policy.queued();

        assertFalse(policy.observed(first));
        assertFalse(policy.unknown(first));
        assertEquals(FreeformLaunchEvidencePolicy.Stage.QUEUED, policy.stage());
        assertTrue(policy.unknown(replacement));
    }
}
