package com.leaf.hyperdragshare.codex

/** Pure display decisions shared by the home status card and diagnostics page. */
internal data class ActivationPresentation(
    val overallState: ActivationState,
    val homeTitle: String,
    val homeSummary: String,
    val rootNeeds: List<String>,
    val missingPermissions: List<String>,
    val missingSoftware: List<Pair<String, String>>,
    val portalNeedsReload: Boolean,
    val hasUnknown: Boolean,
    val diagnosticsTitle: String,
)

internal fun activationPresentation(
    snapshot: ActivationSnapshot?,
    checking: Boolean,
    portalInstalled: Boolean?,
    aicrInstalled: Boolean?,
): ActivationPresentation {
    val state = snapshot?.overallState ?: ActivationState.CHECKING
    val homeTitle = when (state) {
        ActivationState.CHECKING -> "运行状态检查中"
        ActivationState.NORMAL -> "运行状态正常"
        ActivationState.NEEDS_ACTION -> "有功能需要处理"
        ActivationState.UNKNOWN -> "部分状态待确认"
        ActivationState.NOT_APPLICABLE -> "运行状态"
    }
    val homeSummary = when (state) {
        ActivationState.CHECKING -> "正在确认运行状态${if (checking) " · 检查中" else ""}"
        ActivationState.NORMAL -> "已检查的功能运行正常，点击查看各组件状态"
        ActivationState.NEEDS_ACTION -> buildList {
            if (snapshot?.root == ActivationEvidence.UNAVAILABLE) add("Root 输入功能")
            if (snapshot?.portalFeature == ActivationState.NEEDS_ACTION) add("传送门取词与拖拽")
            if (snapshot?.overlayPermission == ActivationEvidence.UNAVAILABLE) add("部分悬浮工具")
            if (snapshot?.pressFeature == ActivationState.NEEDS_ACTION) add("按压取词")
        }.distinct().joinToString("、").let { affected ->
            if (affected.isBlank()) "有运行环节需要处理，查看具体影响和处理方法"
            else "可能影响：$affected。点击查看处理方法"
        }
        ActivationState.UNKNOWN -> "部分状态暂时无法确认；这不代表权限已被拒绝"
        ActivationState.NOT_APPLICABLE -> "当前没有需要检查的运行组件"
    }

    val rootNeeds = buildList {
        if (snapshot?.root == ActivationEvidence.UNAVAILABLE) add("MMGG")
        if (snapshot?.portalRoot == ActivationEvidence.UNAVAILABLE) add("小米传送门")
    }
    val missingPermissions = buildList {
        if (snapshot?.overlayPermission == ActivationEvidence.UNAVAILABLE) add("悬浮窗")
    }
    val missingSoftware = buildList {
        if (portalInstalled == false) add("小米传送门" to "取词和相关拖拽功能需要此组件。")
        if (aicrInstalled == false) add("小米澎湃 AI 引擎" to "系统识字和 AI 识别功能需要此组件。")
    }
    val portalNeedsReload = snapshot?.injection == ActivationEvidence.UNAVAILABLE
        || snapshot?.portalService == ActivationEvidence.UNAVAILABLE
        || snapshot?.rootInput == ActivationEvidence.UNAVAILABLE
        || (snapshot?.thumbTriggerEnabled == true && snapshot.pressReader == ActivationEvidence.UNAVAILABLE)
    val hasUnknown = (snapshot?.let {
        listOf(
            it.root,
            it.injection,
            it.portalService,
            it.portalRoot,
            it.rootInput,
            if (it.thumbTriggerEnabled) it.pressReader else ActivationEvidence.NOT_APPLICABLE,
            it.overlayPermission,
        ).any { evidence -> evidence == ActivationEvidence.UNKNOWN }
    } ?: false) || (snapshot != null && (portalInstalled == null || aicrInstalled == null))
    val diagnosticsTitle = when {
        checking || snapshot == null -> "正在检查"
        rootNeeds.isNotEmpty() || missingPermissions.isNotEmpty()
            || missingSoftware.isNotEmpty() || portalNeedsReload -> "有项目需要处理"
        hasUnknown -> "部分状态暂时无法确认"
        else -> "已检查项目正常"
    }

    return ActivationPresentation(
        overallState = state,
        homeTitle = homeTitle,
        homeSummary = homeSummary,
        rootNeeds = rootNeeds,
        missingPermissions = missingPermissions,
        missingSoftware = missingSoftware,
        portalNeedsReload = portalNeedsReload,
        hasUnknown = hasUnknown,
        diagnosticsTitle = diagnosticsTitle,
    )
}
