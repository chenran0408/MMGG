package com.leaf.hyperdragshare.codex

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.IOException

internal enum class RuntimeAction(val title: String, val detail: String) {
    ReloadApps("重载传送门与 AI 引擎", "按已启用的功能重载相关进程，可能中断识别或分享。"),
    SystemUi("重启系统界面", "重启状态栏、导航和侧栏，当前手势或小窗操作会中断。"),
    Android("软重启 Android", "重启 Android 运行环境，当前打开的应用会中断。"),
    Device("整机重启", "重新启动手机，当前应用会关闭，请先保存内容。"),
}

@Composable
internal fun RuntimeActionDialog(
    context: Context,
    action: RuntimeAction?,
    onDismiss: () -> Unit,
    onAppReloaded: () -> Unit,
) {
    val selected = action ?: return
    var running by remember(selected) { mutableStateOf(false) }
    var failure by remember(selected) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    WindowDialog(
        show = true,
        title = selected.title,
        summary = if (running) "正在执行…" else failure ?: selected.detail,
        onDismissRequest = { if (!running) onDismiss() },
    ) {
        if (running) {
            CircularProgressIndicator()
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(stringResource(android.R.string.cancel), onClick = onDismiss,
                    modifier = Modifier.weight(1f))
                TextButton(selected.title, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(), onClick = run@{
                        if (running) return@run
                        running = true
                        failure = null
                        scope.launch {
                            var result: String? = null
                            val error = withContext(Dispatchers.IO) {
                                try {
                                    when (selected) {
                                        RuntimeAction.ReloadApps -> {
                                            val service = HookFeatureService.service.value
                                                ?: throw IOException("请先连接 LSPosed")
                                            val flags = try {
                                                HookFeatureFlags.read(service.getRemotePreferences(HookFeatureFlags.PREFS))
                                            } catch (error: RuntimeException) {
                                                throw IOException("无法读取 LSPosed 功能开关", error)
                                            }
                                            result = if (ScopeRestart.reloadAppScopes(context, flags))
                                                "重载操作已执行" else "传送门和复制直达均未启用"
                                        }
                                        RuntimeAction.SystemUi -> {
                                            ScopeRestart.restartSystemUi()
                                            result = "已请求重启系统界面"
                                        }
                                        RuntimeAction.Android -> ScopeRestart.restartAndroidRuntime()
                                        RuntimeAction.Device -> ScopeRestart.restartDevice()
                                    }
                                    null
                                } catch (error: IOException) {
                                    DragShareLog.w("DragShare/ScopeRestart", "Runtime action failed", error)
                                    error.message ?: error.javaClass.simpleName
                                }
                            }
                            running = false
                            if (error != null) {
                                failure = "操作未完成：$error"
                            } else {
                                onDismiss()
                                if (selected == RuntimeAction.ReloadApps) onAppReloaded()
                                result?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                            }
                        }
                    })
            }
        }
    }
}
