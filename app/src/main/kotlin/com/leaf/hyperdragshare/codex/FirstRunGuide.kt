package com.leaf.hyperdragshare.codex

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val GUIDE_PREFERENCES = "first_run_guide"
private const val KEY_CONFIRMED = "confirmed"

@Composable
internal fun FirstRunGuide(
    context: Context,
    showOnFirstRun: Boolean = true,
    requested: Boolean = false,
    onDismiss: () -> Unit = {},
    onOpenDiagnostics: (() -> Unit)? = null,
) {
    val preferences = remember(context) {
        context.getSharedPreferences(GUIDE_PREFERENCES, Context.MODE_PRIVATE)
    }
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(preferences, showOnFirstRun, requested) {
        show = requested || (showOnFirstRun && !preferences.getBoolean(KEY_CONFIRMED, false))
    }

    OverlayDialog(
        show = show,
        title = "开始使用 MMGG",
        onDismissRequest = {
            show = false
            onDismiss()
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "在 LSPosed 启用模块和推荐作用域，分别给 MMGG 和小米传送门授予 Root 权限，再重启。可在运行诊断检查授权。",
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "单指按住文字或图片，看到预览就拖动，未拖就松手会取消。双指按到设定时长即打开分词，图片先识字。",
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "从中转站把内容拖到聊天时，接收应用可能直接发送。",
                color = MiuixTheme.colorScheme.onSurface,
            )
            val dismissGuide = {
                preferences.edit().putBoolean(KEY_CONFIRMED, true).apply()
                show = false
                onDismiss()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = if (onOpenDiagnostics == null) "知道了" else "稍后",
                    onClick = dismissGuide, modifier = Modifier.weight(1f))
                if (onOpenDiagnostics != null) TextButton(
                    text = "运行诊断",
                    onClick = { dismissGuide(); onOpenDiagnostics() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
