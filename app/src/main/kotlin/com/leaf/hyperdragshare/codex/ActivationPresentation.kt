package com.leaf.hyperdragshare.codex

internal fun backTapSavedMessage(portalEnabled: Boolean?, pendingRestart: Boolean): String = when {
    portalEnabled == null -> "已保存，传送门启用状态未确认"
    !portalEnabled -> "已保存，启用传送门并生效后可用"
    pendingRestart -> "已保存，软重启 Android 启用传送门后可用"
    else -> "已保存，下次背部轻敲生效"
}

/** Pure display decisions shared by the home status card and diagnostics page. */
internal data class ActivationPresentation(
    val overallState: ActivationState,
    val homeTitle: String,
    val homeSummary: String?,
    val rootRows: List<Pair<String, String>>,
    val missingPermissions: List<String>,
    val missingSoftware: List<Pair<String, String>>,
    val portalNeedsReload: Boolean,
    val hasUnknown: Boolean,
    val diagnosticsTitle: String,
    val pendingPortalSummary: String?,
)

internal fun activationPresentation(
    snapshot: ActivationSnapshot?,
    checking: Boolean,
    portalInstalled: Boolean?,
    aicrInstalled: Boolean?,
): ActivationPresentation {
    val state = when {
        checking || snapshot == null -> ActivationState.CHECKING
        snapshot.pendingPortalEnabled != null -> ActivationState.NEEDS_ACTION
        else -> snapshot.overallState
    }
    val result = snapshot.takeUnless { checking }
    val includePortal = snapshot?.portalEnabled == true
    val moduleRoot = result?.root ?: ActivationEvidence.CHECKING
    val portalConnected = result?.injection == ActivationEvidence.READY
    val portalRoot = result?.portalRoot?.takeIf { includePortal && portalInstalled != false && portalConnected }
    val portalRootDaemonUnreachable = portalInstalled != false && result?.portalRootDaemonUnreachable == true
    val rootChecks = buildList {
        if (moduleRoot.needsRootCheck()) add("MMGG")
        if (!portalRootDaemonUnreachable && portalRoot?.needsRootCheck() == true) add("小米传送门")
    }
    val rootRows = buildList {
        if (moduleRoot != ActivationEvidence.NOT_APPLICABLE) add("MMGG" to rootDetail(moduleRoot))
        if (includePortal) add("小米传送门" to when {
            result == null -> "正在检查"
            portalInstalled == false -> "未安装，无法检查授权"
            !portalConnected -> "尚未连接。请核对 LSPosed 作用域，连接后单独检查传送门的授权"
            portalRootDaemonUnreachable -> "连不上 Root 守护进程"
            else -> rootDetail(result.portalRoot)
        })
        if (result != null && result.portalEnabled == null && portalInstalled != false) {
            add("小米传送门" to "等待 LSPosed 连接。启用「传送门与大爆炸」后，需要单独允许小米传送门使用 Root")
        }
    }
    val waitingForPortal = result != null && includePortal && !portalConnected &&
        result.injection != ActivationEvidence.NOT_APPLICABLE
    val pendingSummary = result?.pendingPortalEnabled?.let {
        if (it) "已保存，软重启 Android 后启用传送门与大爆炸" else "已保存，软重启 Android 后关闭传送门与大爆炸"
    }
    val homeTitle = when {
        rootChecks.isNotEmpty() -> "检查 Root 授权"
        pendingSummary != null -> "传送门更改待生效"
        portalRootDaemonUnreachable -> "小米传送门连不上 Root 守护进程"
        else -> when (state) {
            ActivationState.CHECKING -> "正在检查运行状态"
            ActivationState.NORMAL -> "运行状态正常"
            ActivationState.NEEDS_ACTION -> "有功能需要处理"
            ActivationState.UNKNOWN -> "部分状态待确认"
            ActivationState.NOT_APPLICABLE -> "运行状态"
        }
    }
    val homeSummary = when {
        rootChecks.size == 2 -> "请在 Root 管理器中分别核对 MMGG 和小米传送门的授权"
        rootChecks.isNotEmpty() && waitingForPortal && portalInstalled != false ->
            "请先在 Root 管理器中核对 MMGG 的授权，小米传送门连接后单独检查"
        rootChecks.isNotEmpty() -> "请打开 Root 管理器，核对「${rootChecks.single()}」的授权"
        pendingSummary != null -> pendingSummary
        portalRootDaemonUnreachable -> "请检查手机上已安装的 Root 版本，或导出诊断 ZIP"
        waitingForPortal -> if (portalInstalled == false) "小米传送门未安装"
            else "等待小米传送门连接后检查其 Root 授权"
        state == ActivationState.NEEDS_ACTION -> buildList {
            if (snapshot?.portalFeature == ActivationState.NEEDS_ACTION) add("传送门取词与拖拽")
            if (snapshot?.overlayPermission == ActivationEvidence.UNAVAILABLE) add("部分悬浮工具")
            if (snapshot?.pressFeature == ActivationState.NEEDS_ACTION) add("按压取词")
        }.distinct().joinToString("、").takeIf { it.isNotBlank() }?.let { "可能影响 $it" }
        else -> null
    }

    val missingPermissions = buildList {
        if (result?.overlayPermission == ActivationEvidence.UNAVAILABLE) add("悬浮窗")
    }
    val missingSoftware = buildList {
        if (includePortal && portalInstalled == false) add("小米传送门" to "取词和相关拖拽功能需要此组件。")
        if (aicrInstalled == false) add("小米澎湃 AI 引擎" to "系统识字和 AI 识别功能需要此组件。")
    }
    val portalNeedsReload = result != null && pendingSummary == null && includePortal && !portalRootDaemonUnreachable && (result.injection == ActivationEvidence.UNAVAILABLE
        || result.portalService == ActivationEvidence.UNAVAILABLE
        || result.rootInput == ActivationEvidence.UNAVAILABLE
        || (result.thumbTriggerEnabled && result.pressReader == ActivationEvidence.UNAVAILABLE))
    val hasUnknown = (result?.let {
        listOf(
            it.root,
            it.injection,
            it.portalService,
            it.portalRoot,
            it.rootInput,
            if (it.thumbTriggerEnabled) it.pressReader else ActivationEvidence.NOT_APPLICABLE,
            it.overlayPermission,
        ).any { evidence -> evidence == ActivationEvidence.UNKNOWN }
    } ?: false) || (result != null && ((includePortal && portalInstalled == null) || aicrInstalled == null))
    val diagnosticsTitle = when {
        checking || snapshot == null -> "正在检查"
        moduleRoot == ActivationEvidence.UNAVAILABLE || portalRoot == ActivationEvidence.UNAVAILABLE || missingPermissions.isNotEmpty()
            || missingSoftware.isNotEmpty() || portalNeedsReload || portalRootDaemonUnreachable || pendingSummary != null -> "有项目需要处理"
        hasUnknown -> "部分状态暂时无法确认"
        state == ActivationState.CHECKING -> "正在检查"
        else -> "已检查项目正常"
    }

    return ActivationPresentation(
        overallState = state,
        homeTitle = homeTitle,
        homeSummary = homeSummary,
        rootRows = rootRows,
        missingPermissions = missingPermissions,
        missingSoftware = missingSoftware,
        portalNeedsReload = portalNeedsReload,
        hasUnknown = hasUnknown,
        diagnosticsTitle = diagnosticsTitle,
        pendingPortalSummary = pendingSummary,
    )
}

private fun ActivationEvidence.needsRootCheck(): Boolean =
    this == ActivationEvidence.UNAVAILABLE || this == ActivationEvidence.UNKNOWN

private fun rootDetail(evidence: ActivationEvidence): String = when (evidence) {
    ActivationEvidence.READY -> "已授权"
    ActivationEvidence.CHECKING -> "正在检查"
    ActivationEvidence.UNAVAILABLE -> "Root 不可用，请在 Root 管理器中允许此应用"
    ActivationEvidence.UNKNOWN -> "未确认，请在 Root 管理器中核对授权后重新检查"
    ActivationEvidence.NOT_APPLICABLE -> "无需检查"
}
