package com.leaf.hyperdragshare.codex

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.icon.extended.Report

@Composable
internal fun ActivationStatusCard(
    monitor: ActivationMonitorState,
    onOpenDiagnostics: () -> Unit,
) {
    val snapshot = monitor.snapshot
    val presentation = activationPresentation(snapshot, monitor.checking, monitor.portalInstalled, monitor.aicrInstalled)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        SettingsNavigationPreference(
            title = presentation.homeTitle,
            summary = presentation.homeSummary,
            icon = if (presentation.overallState == ActivationState.NORMAL) MiuixIcons.Ok else MiuixIcons.Info,
            onClick = onOpenDiagnostics,
        )
    }
}

@Composable
internal fun ActivationDiagnosticsPage(
    context: Context,
    monitor: ActivationMonitorState,
    onBack: () -> Unit,
    onReloadApps: () -> Unit,
    onApplyPortalChange: () -> Unit,
    onOpenProblemDiagnostics: () -> Unit,
) {
    val snapshot = monitor.snapshot
    val presentation = activationPresentation(snapshot, monitor.checking, monitor.portalInstalled, monitor.aicrInstalled)
    val missingPermissions = presentation.missingPermissions
    val missingSoftware = presentation.missingSoftware
    val portalNeedsReload = presentation.portalNeedsReload
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = "运行诊断",
                largeTitle = "运行诊断",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 20.dp,
            ),
        ) {
            item("diagnostics-summary") {
                SmallTitle("功能状态")
                Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                    BasicComponent(
                        title = presentation.diagnosticsTitle,
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    )
                    SettingsNavigationPreference(
                        title = if (monitor.refreshing) "正在检查…" else "重新检查",
                        icon = MiuixIcons.Update,
                        enabled = !monitor.refreshing,
                        onClick = monitor.refresh,
                    )
                }
            }
            item("diagnostics-root") {
                SimpleDiagnosticSection("Root 状态", presentation.rootRows)
            }
            item("diagnostics-permissions") {
                if (missingPermissions.isNotEmpty()) {
                    SimpleDiagnosticSection("缺少权限", missingPermissions.map { it to "允许 MMGG 显示悬浮窗" }) {
                        openOverlayPermissionSettings(context)
                    }
                }
            }
            item("diagnostics-software") {
                if (missingSoftware.isNotEmpty()) {
                    SimpleDiagnosticSection("缺少软件", missingSoftware.map { (name, impact) ->
                        name to "${impact}请从系统组件更新或设备厂商提供的渠道安装。"
                    })
                }
            }
            item("diagnostics-reload") {
                val pending = presentation.pendingPortalSummary
                if (pending != null) {
                    SimpleDiagnosticSection("传送门更改待生效", listOf(RuntimeAction.Android.title to pending)) {
                        onApplyPortalChange()
                    }
                } else if (portalNeedsReload && missingSoftware.none { it.first == "小米传送门" }) {
                    SimpleDiagnosticSection("传送门待检查", listOf(RuntimeAction.ReloadApps.title to
                        "确认 LSPosed 作用域和 Root 授权后，可尝试重载")) { onReloadApps() }
                }
            }
            item("diagnostics-actions") {
                SmallTitle("处理入口")
                Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                    SettingsNavigationPreference(
                        title = "问题反馈与诊断",
                        summary = "导出诊断 ZIP、运行触控专项检查或提交问题",
                        icon = MiuixIcons.Report,
                        onClick = onOpenProblemDiagnostics,
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleDiagnosticSection(
    title: String,
    rows: List<Pair<String, String>>,
    onClick: (() -> Unit)? = null,
) {
    if (rows.isEmpty()) return
    SmallTitle(title)
    Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
        rows.forEach { (name, detail) ->
            if (onClick == null) {
                BasicComponent(
                    title = name,
                    summary = detail,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                SettingsNavigationPreference(
                    title = name,
                    summary = detail,
                    icon = MiuixIcons.Info,
                    onClick = onClick,
                )
            }
        }
    }
}
