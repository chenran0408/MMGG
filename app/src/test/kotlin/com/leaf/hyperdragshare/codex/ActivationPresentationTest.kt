package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationPresentationTest {
    @Test
    fun savedBackTapReportsWhetherThePortalCanUseIt() {
        assertEquals("已保存，下次背部轻敲生效", backTapSavedMessage(true, false))
        assertEquals("已保存，软重启 Android 启用传送门后可用", backTapSavedMessage(true, true))
        assertEquals("已保存，启用传送门并生效后可用", backTapSavedMessage(false, false))
        assertEquals("已保存，启用传送门并生效后可用", backTapSavedMessage(false, true))
        assertEquals("已保存，传送门启用状态未确认", backTapSavedMessage(null, false))
    }
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
    fun aNewCheckDisplaysCheckingEvenWithAnOlderResult() {
        val presentation = activationPresentation(snapshot(), true, true, true)

        assertEquals(ActivationState.CHECKING, presentation.overallState)
        assertEquals("正在检查运行状态", presentation.homeTitle)
        assertNull(presentation.homeSummary)
        assertEquals("正在检查", presentation.diagnosticsTitle)
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
        assertNull(presentation.homeSummary)
        assertTrue(presentation.hasUnknown)
        assertFalse(presentation.portalNeedsReload)
        assertEquals(listOf("已授权", "已授权"), presentation.rootRows.map { it.second })
        assertTrue(presentation.missingPermissions.isEmpty())
        assertTrue(presentation.missingSoftware.isEmpty())
        assertEquals("部分状态暂时无法确认", presentation.diagnosticsTitle)
    }

    @Test
    fun namesOnlyTheAppWhoseRootNeedsCheckingAndKeepsOtherPermissionActions() {
        val ready = snapshot()
        val portal = activationPresentation(ready.copy(portalRoot = ActivationEvidence.UNKNOWN,
            overlayPermission = ActivationEvidence.UNAVAILABLE), false, true, true)
        assertTrue(portal.homeTitle.contains("Root"))
        assertTrue(portal.homeSummary.orEmpty().contains("小米传送门"))
        assertFalse(portal.homeSummary.orEmpty().contains("MMGG"))
        assertEquals("已授权", portal.rootRows.toMap()["MMGG"])
        assertTrue(portal.rootRows.toMap().getValue("小米传送门").contains("未确认"))
        assertEquals(listOf("悬浮窗"), portal.missingPermissions)

        val module = activationPresentation(ready.copy(root = ActivationEvidence.UNAVAILABLE), false, true, true)
        assertTrue(module.homeSummary.orEmpty().contains("MMGG"))
        assertFalse(module.homeSummary.orEmpty().contains("小米传送门"))
        assertEquals("已授权", module.rootRows.toMap()["小米传送门"])
        assertTrue(module.rootRows.toMap().getValue("MMGG").contains("不可用"))
        assertNull(activationPresentation(ready, false, true, true).homeSummary)
    }

    @Test
    fun twoUnconfirmedRootsExplainSeparateGrantsAndCheckingClearsOldWarnings() {
        val both = snapshot().copy(root = ActivationEvidence.UNKNOWN, portalRoot = ActivationEvidence.UNKNOWN)
        val result = activationPresentation(both, false, true, true)
        assertTrue(result.homeSummary.orEmpty().contains("分别"))
        assertTrue(result.homeSummary.orEmpty().contains("MMGG"))
        assertTrue(result.homeSummary.orEmpty().contains("小米传送门"))
        assertTrue(result.rootRows.all { (_, value) -> value.contains("未确认") })
        val checking = activationPresentation(both, true, true, true)
        assertNull(checking.homeSummary)
        assertTrue(checking.rootRows.all { (_, value) -> value == "正在检查" })
        val waitingForResult = activationPresentation(snapshot().copy(root = ActivationEvidence.CHECKING),
            false, true, true)
        assertEquals("正在检查", waitingForResult.diagnosticsTitle)
    }

    @Test
    fun waitingForPortalProbeKeepsTheKnownModuleGrantAndSuppressesRootWarnings() {
        val presentation = activationPresentation(snapshot().copy(portalRoot = ActivationEvidence.CHECKING),
            false, true, true)
        assertEquals("已授权", presentation.rootRows.toMap()["MMGG"])
        assertEquals("正在检查", presentation.rootRows.toMap()["小米传送门"])
        assertNull(presentation.homeSummary)
        assertEquals(ActivationState.CHECKING, presentation.overallState)
    }

    @Test
    fun pendingEnableExplainsRestartWithoutSuggestingScopeOrReloadAndPendingDisablePreservesKnownGrants() {
        val pendingEnable = activationPresentation(snapshot().copy(portalEnabled = false,
            pendingPortalEnabled = true, injection = ActivationEvidence.NOT_APPLICABLE,
            portalRoot = ActivationEvidence.NOT_APPLICABLE), false, true, true)
        assertEquals("传送门更改待生效", pendingEnable.homeTitle)
        assertTrue(pendingEnable.homeSummary.orEmpty().contains("软重启 Android 后启用"))
        assertEquals(listOf("MMGG" to "已授权"), pendingEnable.rootRows)
        assertFalse(pendingEnable.portalNeedsReload)

        val pendingDisable = activationPresentation(snapshot().copy(pendingPortalEnabled = false),
            false, true, true)
        assertTrue(pendingDisable.pendingPortalSummary.orEmpty().contains("软重启 Android 后关闭"))
        assertEquals("已授权", pendingDisable.rootRows.toMap()["小米传送门"])
        assertFalse(pendingDisable.portalNeedsReload)
    }

    @Test
    fun unconnectedMissingAndDisabledPortalHaveDifferentGuidance() {
        val disconnected = snapshot().copy(injection = ActivationEvidence.UNKNOWN,
            portalService = ActivationEvidence.UNKNOWN, portalRoot = ActivationEvidence.UNKNOWN)
        val waiting = activationPresentation(disconnected, false, true, true)
        assertTrue(waiting.rootRows.toMap().getValue("小米传送门").contains("作用域"))
        assertFalse(waiting.rootRows.toMap().getValue("小米传送门").contains("拒绝"))
        val firstRun = activationPresentation(disconnected.copy(root = ActivationEvidence.UNKNOWN),
            false, null, null)
        assertTrue(firstRun.homeSummary.orEmpty().contains("MMGG"))
        assertTrue(firstRun.homeSummary.orEmpty().contains("小米传送门连接后"))
        val absent = activationPresentation(disconnected, false, false, true)
        assertTrue(absent.rootRows.toMap().getValue("小米传送门").contains("未安装"))
        assertTrue(absent.missingSoftware.any { it.first == "小米传送门" })

        val unbound = activationPresentation(disconnected.copy(portalEnabled = null),
            false, true, true)
        assertTrue(unbound.rootRows.toMap().getValue("小米传送门").contains("等待 LSPosed"))
        assertNull(unbound.homeSummary)

        val disabled = activationPresentation(snapshot().copy(portalEnabled = false,
            portalRoot = ActivationEvidence.NOT_APPLICABLE), false, false, true)
        assertEquals(listOf("MMGG"), disabled.rootRows.map { it.first })
        assertTrue(disabled.missingSoftware.isEmpty())
        assertNull(disabled.homeSummary)
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
