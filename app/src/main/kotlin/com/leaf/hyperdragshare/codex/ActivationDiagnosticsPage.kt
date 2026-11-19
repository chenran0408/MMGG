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
    val presentation = activationPresentation(snapshot, monitor.checking, true, true)
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
    onOpenProblemDiagnostics: () -> Unit,
) {
    val snapshot = monitor.snapshot
    val portalInstalled = packageInstalled(context, PORTAL_PACKAGE)
    val aicrInstalled = packageInstalled(context, AICR_PACKAGE)
    val presentation = activationPresentation(snapshot, monitor.checking, portalInstalled, aicrInstalled)
    val rootNeeds = presentation.rootNeeds
    val missingPermissions = presentation.missingPermissions
    val missingSoftware = presentation.missingSoftware
    val portalNeedsReload = presentation.portalNeedsReload
    val hasUnknown = presentation.hasUnknown
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
                        title = if (monitor.checking) "正在检查…" else "重新检查",
                        icon = MiuixIcons.Update,
                        enabled = !monitor.checking,
                        onClick = monitor.refresh,
                    )
                }
            }
            item("diagnostics-root") {
                if (rootNeeds.isNotEmpty()) {
                    SimpleDiagnosticSection("需要 Root", rootNeeds.map { it to "请在 Root 管理器中为${it}授权后重新检查" }) {
                        monitor.refresh()
                    }
                }
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
                if (portalNeedsReload && missingSoftware.none { it.first == "小米传送门" }) {
                    SimpleDiagnosticSection("传送门待检查", listOf(RuntimeAction.ReloadApps.title to
                        "确认 LSPosed 作用域和 Root 授权后，可尝试重载")) { onReloadApps() }
                }
            }
            item("diagnostics-unknown") {
                if (hasUnknown) {
                    Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                        BasicComponent(
                            title = "有状态暂时无法确认",
                            summary = "可重新检查或导出诊断包",
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            item("diagnostics-clear") {
                if (snapshot != null && !monitor.checking && rootNeeds.isEmpty()
                    && missingPermissions.isEmpty() && missingSoftware.isEmpty() && !portalNeedsReload && !hasUnknown
                ) {
                    Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                        BasicComponent(
                            title = "没有发现需要处理的项目",
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
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

private const val PORTAL_PACKAGE = "com.miui.contentextension"
private const val AICR_PACKAGE = "com.xiaomi.aicr"

private fun packageInstalled(context: Context, packageName: String): Boolean? = try {
    context.packageManager.getApplicationInfo(packageName, 0)
    true
} catch (_: android.content.pm.PackageManager.NameNotFoundException) {
    false
} catch (_: RuntimeException) {
    null
}
