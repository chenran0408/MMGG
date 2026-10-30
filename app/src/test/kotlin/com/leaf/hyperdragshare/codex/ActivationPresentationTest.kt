package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationPresentationTest {
    @Test
    fun homeCardMapsAllFiveActivationStates() {
        val ready = snapshot()
        val cases = listOf(
            ready.copy(root = ActivationEvidence.CHECKING) to ActivationState.CHECKING,
            ready to ActivationState.NORMAL,
            ready.copy(overlayPermission = ActivationEvidence.UNAVAILABLE) to ActivationState.NEEDS_ACTION,
            ready.copy(portalService = ActivationEvidence.UNKNOWN) to ActivationState.UNKNOWN,
            snapshot(ActivationEvidence.NOT_APPLICABLE) to ActivationState.NOT_APPLICABLE,
        )

        cases.forEach { (stateSnapshot, expectedState) ->
            assertEquals(expectedState, activationPresentation(stateSnapshot, false, true, true).overallState)
        }
    }

    @Test
    fun featureStatesFollowTheirOwnDependencies() {
        val base = snapshot()
        val portalDependencies = listOf(
            base.copy(injection = ActivationEvidence.UNAVAILABLE),
            base.copy(portalService = ActivationEvidence.UNAVAILABLE),
            base.copy(portalRoot = ActivationEvidence.UNAVAILABLE),
            base.copy(rootInput = ActivationEvidence.UNAVAILABLE),
        )
        portalDependencies.forEach { changed ->
            assertEquals(ActivationState.NEEDS_ACTION, changed.portalFeature)
            assertEquals(ActivationState.NEEDS_ACTION, changed.pressFeature)
        }

        val readerUnavailable = base.copy(pressReader = ActivationEvidence.UNAVAILABLE)
        assertEquals(ActivationState.NORMAL, readerUnavailable.portalFeature)
        assertEquals(ActivationState.NEEDS_ACTION, readerUnavailable.pressFeature)

        val rootUnavailable = base.copy(root = ActivationEvidence.UNAVAILABLE)
        val overlayUnavailable = base.copy(overlayPermission = ActivationEvidence.UNAVAILABLE)
        assertEquals(ActivationState.NORMAL, rootUnavailable.portalFeature)
        assertEquals(ActivationState.NORMAL, rootUnavailable.pressFeature)
        assertEquals(ActivationState.NORMAL, overlayUnavailable.portalFeature)
        assertEquals(ActivationState.NORMAL, overlayUnavailable.pressFeature)

        val disabledReader = base.copy(
            thumbTriggerEnabled = false,
            pressReader = ActivationEvidence.UNAVAILABLE,
        )
        assertEquals(ActivationState.NORMAL, disabledReader.pressFeature)
    }

    @Test
    fun unknownEvidenceStaysUnknownAndDoesNotBecomeMissingPermissionOrReload() {
        val presentation = activationPresentation(
            snapshot().copy(rootInput = ActivationEvidence.UNKNOWN),
            checking = false,
            portalInstalled = true,
            aicrInstalled = true,
        )

        assertEquals(ActivationState.UNKNOWN, presentation.overallState)
        assertEquals("部分状态待确认", presentation.homeTitle)
        assertTrue(presentation.homeSummary.contains("不代表权限已被拒绝"))
        assertTrue(presentation.hasUnknown)
        assertFalse(presentation.portalNeedsReload)
        assertTrue(presentation.rootNeeds.isEmpty())
        assertTrue(presentation.missingPermissions.isEmpty())
        assertTrue(presentation.missingSoftware.isEmpty())
        assertEquals("部分状态暂时无法确认", presentation.diagnosticsTitle)
    }

    private fun snapshot(evidence: ActivationEvidence = ActivationEvidence.READY) = ActivationSnapshot(
        root = evidence,
        injection = evidence,
        portalService = evidence,
        portalRoot = evidence,
        rootInput = evidence,
        pressReader = evidence,
        overlayPermission = evidence,
        thumbTriggerEnabled = true,
        copyDirectEnabled = false,
        rootInputError = "",
        pressReaderError = "",
    )
}
