package com.leaf.hyperdragshare.codex

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import java.io.File
import java.io.IOException

/** One export operation owns generation, document selection, copying, and optional sharing. */
internal enum class DiagnosticExportOutcome { SAVED_CHOOSER_OPENED, SHARE_FAILED, SAVE_FAILED, STALE }

internal class DiagnosticExportSession(awaitingPickerToken: Long? = null) {
    private var nextToken = awaitingPickerToken ?: 0L
    private var savePickerConsumed = false
    var activeToken: Long? = awaitingPickerToken
        private set

    fun begin(): Long? {
        if (activeToken != null) return null
        nextToken += 1
        activeToken = nextToken
        savePickerConsumed = false
        return nextToken
    }

    fun isActive(token: Long): Boolean = activeToken == token

    fun consumeSavePickerResult(token: Long): Boolean {
        if (!isActive(token) || savePickerConsumed) return false
        savePickerConsumed = true
        return true
    }

    fun finish(token: Long): Boolean {
        if (activeToken != token) return false
        activeToken = null
        savePickerConsumed = false
        return true
    }

    fun cancelBeforeSavePicker(token: Long): Boolean = finish(token)

    fun cancelActive() {
        activeToken = null
        savePickerConsumed = false
    }

    fun finishAndShare(token: Long, share: () -> Unit): DiagnosticShareOutcome {
        if (!finish(token)) return DiagnosticShareOutcome.STALE
        return try {
            share()
            DiagnosticShareOutcome.CHOOSER_OPENED
        } catch (_: Exception) {
            DiagnosticShareOutcome.SHARE_FAILED
        }
    }

    suspend fun saveAndShare(
        token: Long,
        save: suspend () -> Unit,
        share: () -> Unit,
    ): DiagnosticExportOutcome {
        if (!isActive(token)) return DiagnosticExportOutcome.STALE
        try {
            save()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return if (finish(token)) DiagnosticExportOutcome.SAVE_FAILED
            else DiagnosticExportOutcome.STALE
        }
        return when (finishAndShare(token, share)) {
            DiagnosticShareOutcome.CHOOSER_OPENED -> DiagnosticExportOutcome.SAVED_CHOOSER_OPENED
            DiagnosticShareOutcome.SHARE_FAILED -> DiagnosticExportOutcome.SHARE_FAILED
            DiagnosticShareOutcome.STALE -> DiagnosticExportOutcome.STALE
        }
    }
}

internal enum class DiagnosticShareOutcome { CHOOSER_OPENED, SHARE_FAILED, STALE }

internal fun diagnosticMissingLogMessage(reason: String?): String? = when (reason) {
    null -> null
    "运行日志未启用。" -> "原始运行日志未启用，已附带可读取的操作摘要"
    "运行日志写入系统日志，应用无法读取原始日志。" -> "系统限制读取原始日志，已附带可读取的操作摘要"
    "没有可导出的结构化运行诊断记录。" -> "尚无可导出的操作追踪记录"
    else -> "当前无法读取运行日志"
}

@Composable
internal fun ProblemDiagnosticsPage(
    context: Context,
    monitor: ActivationMonitorState,
    onBack: () -> Unit,
    onOpenTouchDiagnostics: () -> Unit,
) {
    var pendingPickerToken by rememberSaveable { mutableStateOf<Long?>(null) }
    var generatedFile by rememberSaveable { mutableStateOf<File?>(null) }
    var missingLogReason by rememberSaveable { mutableStateOf<String?>(null) }
    val session = remember { DiagnosticExportSession(pendingPickerToken) }
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(if (pendingPickerToken == null) null else "选择保存位置") }
    var status by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(session) {
        onDispose { session.cancelActive() }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination ->
        val token = pendingPickerToken
        val source = generatedFile
        if (token == null || source == null || !session.consumeSavePickerResult(token)) return@rememberLauncherForActivityResult
        pendingPickerToken = null
        if (destination == null) {
            session.cancelBeforeSavePicker(token)
            generatedFile = null
            phase = null
            status = "已取消保存"
            return@rememberLauncherForActivityResult
        }

        phase = "正在保存诊断包…"
        scope.launch {
            val outcome = session.saveAndShare(
                token = token,
                save = {
                    withContext(Dispatchers.IO) {
                        val output = appContext.contentResolver.openOutputStream(destination, "w")
                            ?: throw IOException("无法打开所选位置")
                        output.use { stream -> source.inputStream().use { it.copyTo(stream) } }
                    }
                },
                share = {
                    val uri = FileProvider.getUriForFile(
                        appContext,
                        "${BuildConfig.APPLICATION_ID}.updates",
                        source,
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "MMGG 诊断包 ${BuildConfig.VERSION_NAME}")
                        clipData = ClipData.newRawUri("MMGG 诊断包", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(send, "分享诊断包")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            when (outcome) {
                DiagnosticExportOutcome.STALE -> return@launch
                DiagnosticExportOutcome.SAVE_FAILED -> {
                    phase = null
                    status = "保存诊断 ZIP 失败，请重试"
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                }
                DiagnosticExportOutcome.SAVED_CHOOSER_OPENED,
                DiagnosticExportOutcome.SHARE_FAILED -> {
                    phase = null
                    status = buildString {
                        append(if (outcome == DiagnosticExportOutcome.SAVED_CHOOSER_OPENED) "诊断包已保存" else "诊断包已保存，无法打开分享面板")
                        missingLogReason?.let {
                            append("；诊断说明：").append(it)
                        }
                    }
                }
            }
            generatedFile = null
            if (outcome == DiagnosticExportOutcome.SHARE_FAILED) {
                Toast.makeText(context, "诊断包已保存，但无法打开分享面板", Toast.LENGTH_LONG).show()
            }
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = "问题反馈与诊断",
                largeTitle = "问题反馈与诊断",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onBack) },
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
            item("problem-diagnostics-export") {
                SmallTitle("诊断包")
                Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                    ArrowPreference(
                        title = phase ?: "导出诊断 ZIP",
                        enabled = phase == null,
                        onClick = {
                            val token = session.begin() ?: return@ArrowPreference
                            phase = "正在生成诊断包…"
                            status = null
                            missingLogReason = null
                            val safeSummary = diagnosticStateSummary(monitor.snapshot)
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        DiagnosticBundleExporter.create(appContext, safeSummary)
                                    }
                                    if (!session.isActive(token)) return@launch
                                    generatedFile = result.file
                                    missingLogReason = diagnosticMissingLogMessage(result.missingLogReason)
                                    phase = "选择保存位置"
                                    pendingPickerToken = token
                                    saveLauncher.launch(result.file.name)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    if (!session.finish(token)) return@launch
                                    pendingPickerToken = null
                                    generatedFile = null
                                    phase = null
                                    status = "生成诊断 ZIP 失败，请重试"
                                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                    )
                    status?.let {
                        BasicComponent(
                            title = it,
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            item("problem-diagnostics-touch") {
                SmallTitle("专项检查")
                Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                    SettingsNavigationPreference(
                        title = "触控诊断",
                        icon = MiuixIcons.Report,
                        onClick = onOpenTouchDiagnostics,
                    )
                }
            }
            item("problem-diagnostics-full-mode") {
                SmallTitle("导出选项")
                Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                    var fullDiagnostics by remember {
                        mutableStateOf(DragShareSettings.readFullDiagnostics(appContext))
                    }
                    SwitchPreference(
                        title = "完整诊断模式",
                        summary = "开启后导出的诊断包原样记录堆栈、异常与操作细节，仅凭据字段脱敏；默认关闭",
                        checked = fullDiagnostics,
                        onCheckedChange = { enabled ->
                            fullDiagnostics = enabled
                            DragShareSettings.writeFullDiagnostics(appContext, enabled)
                        },
                    )
                }
            }
            item("problem-diagnostics-feedback") {
                SmallTitle("反馈")
                Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                    SettingsNavigationPreference(
                        title = "在 GitHub 提交问题",
                        summary = "提交前可附上刚导出的诊断 ZIP",
                        icon = MiuixIcons.Help,
                        onClick = {
                            val issue = Uri.parse("https://github.com/chenran0408/MMGG/issues/new")
                                .buildUpon()
                                .appendQueryParameter("title", "MMGG 问题反馈")
                                .appendQueryParameter("body", "问题现象：\n\n复现步骤：\n\n请附上“问题反馈与诊断”页面导出的诊断 ZIP。")
                                .build()
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, issue).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, "无法打开浏览器或 GitHub 客户端", Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                }
            }
        }
    }
}

internal fun diagnosticStateSummary(snapshot: ActivationSnapshot?): String = buildString {
    append("overall=").append(snapshot?.overallState ?: ActivationState.CHECKING)
    append("; root=").append(snapshot?.root ?: ActivationEvidence.CHECKING)
    append("; injection=").append(snapshot?.injection ?: ActivationEvidence.CHECKING)
    append("; portal_service=").append(snapshot?.portalService ?: ActivationEvidence.CHECKING)
    append("; portal_root=").append(snapshot?.portalRoot ?: ActivationEvidence.CHECKING)
    append("; root_input=").append(snapshot?.rootInput ?: ActivationEvidence.CHECKING)
    append("; press_reader=").append(snapshot?.pressReader ?: ActivationEvidence.CHECKING)
    append("; overlay=").append(snapshot?.overlayPermission ?: ActivationEvidence.CHECKING)
}
