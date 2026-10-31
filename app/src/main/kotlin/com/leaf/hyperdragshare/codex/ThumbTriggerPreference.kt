package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.ComponentName
import android.app.StatusBarManager
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import kotlin.math.roundToInt

@Composable
internal fun thumbTriggerEntries(context: Context,
    settings: DragShareSettings, onDurationChanged: (Int) -> Unit): List<SettingEntry> {
    var addingTile by remember { mutableStateOf(false) }
    var tileMessage by remember { mutableStateOf<String?>(null) }
    var thumb by remember { mutableStateOf(ThumbTriggerSettings.read(context)) }
    var duration by remember(settings.accessibilityLongPressTimeoutMillis, thumb.enabled) {
        mutableFloatStateOf(settings.resolvePressConfirmationTimeoutMillis().toFloat())
    }
    var portalInjection by remember(context) { mutableStateOf(ModuleActivation.injectionStatus(context)) }
    var portalRoot by remember(context) { mutableStateOf(ModuleActivation.portalRootStatus(context)) }
    // Reports may arrive while this page stays visible (for example after a Root prompt).
    DisposableEffect(context) {
        val preferences = context.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            portalInjection = ModuleActivation.injectionStatus(context)
            portalRoot = ModuleActivation.portalRootStatus(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        thumb = ThumbTriggerSettings.read(context)
        portalInjection = ModuleActivation.injectionStatus(context)
        portalRoot = ModuleActivation.portalRootStatus(context)
    }
    val triggerTitle = "按住取词与识字"
    val durationTitle = "按住确认时间"
    val tileTitle = "添加“点取内容”磁贴"
    val keywords = "按压 长按 evdev 点取 识字"
    val triggerSummary = when {
        portalInjection == ModuleActivation.ReportedStatus.UNAVAILABLE -> "传送门需要重载；可先保存开关"
        portalInjection == ModuleActivation.ReportedStatus.UNKNOWN -> "传送门状态待确认；可先保存开关，再查看运行诊断"
        portalRoot == ModuleActivation.ReportedStatus.UNAVAILABLE -> "传送门 Root 检查未通过；已授权时请查看运行诊断"
        portalRoot == ModuleActivation.ReportedStatus.UNKNOWN -> "传送门 Root 暂时无法确认；可先保存开关，再查看运行诊断"
        else -> "静止按住取词，移动取消"
    }
    val durationEnabled = thumb.enabled
    val durationPrerequisite = if (durationEnabled) null else "请先开启按住取词与识字"
    val tileSummary = tileMessage ?: "从控制中心点取文字或图片"

    return listOf(
        SettingEntry(
            id = "press.enabled",
            title = triggerTitle,
            module = SettingModule.PORTAL,
            group = "触发与识别",
            summary = triggerSummary,
            keywords = keywords,
            content = { enabled ->
                SwitchPreference(
                    title = triggerTitle,
                    summary = triggerSummary,
                    enabled = enabled,
                    checked = thumb.enabled,
                    onCheckedChange = { checked ->
                        // Saving a preference is not a Root grant. The actual Portal
                        // worker still has to obtain su; a diagnostic must not lock this switch.
                        ThumbTriggerSettings.setEnabled(context, checked)
                        thumb = ThumbTriggerSettings.read(context)
                        Toast.makeText(
                            context,
                            if (checked && (portalInjection != ModuleActivation.ReportedStatus.READY
                                    || portalRoot != ModuleActivation.ReportedStatus.READY)) {
                                "开关已保存；运行状态待确认，请查看运行诊断。"
                            } else {
                                "已保存；传送门会在下一次按住取词/识字时读取，无需重启。"
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            },
        ),
        SettingEntry(
            id = "press.duration",
            title = durationTitle,
            module = SettingModule.PORTAL,
            group = "触发与识别",
            summary = "250–3000 ms，默认 600 ms；时间越短越容易误触",
            keywords = keywords,
            enabled = durationEnabled,
            prerequisite = durationPrerequisite,
            content = { enabled ->
                SliderPreference(
                    title = durationTitle,
                    summary = "250–3000 ms，默认 600 ms；时间越短越容易误触",
                    enabled = enabled && durationEnabled,
                    value = duration,
                    valueText = "${duration.roundToInt()} ms",
                    valueRange = DragShareSettings.MIN_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS.toFloat()
                        ..DragShareSettings.MAX_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS.toFloat(),
                    steps = 54,
                    showKeyPoints = true,
                    keyPoints = listOf(
                        250f, 500f, 600f, 750f, 1000f, 1250f, 1500f,
                        1750f, 2000f, 2250f, 2500f, 2750f, 3000f,
                    ),
                    onValueChange = { duration = it },
                    onValueChangeFinished = { onDurationChanged(duration.roundToInt()) },
                )
            },
        ),
        SettingEntry(
            id = "press.tile",
            title = tileTitle,
            module = SettingModule.PORTAL,
            group = "触发与识别",
            summary = tileSummary,
            keywords = keywords,
            enabled = !addingTile,
            content = { enabled ->
                ArrowPreference(
                    title = tileTitle,
                    summary = tileSummary,
                    enabled = enabled && !addingTile,
                    onClick = {
                        addingTile = true
                        tileMessage = "正在请求添加…"
                        try {
                            context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                                ComponentName(context, PointCaptureTileService::class.java),
                                context.getString(R.string.point_capture_tile_label),
                                Icon.createWithResource(context, R.drawable.ic_point_capture_tile),
                                context.mainExecutor,
                            ) { result ->
                                addingTile = false
                                tileMessage = when (result) {
                                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已添加，可从控制中心点取当前页面"
                                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "已在控制中心，可直接使用"
                                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "未添加，可再次申请或在控制中心编辑磁贴"
                                    else -> {
                                        DragShareLog.w("DragShare/PointCapture", "tile add request failed code=$result")
                                        "系统未能添加（$result），可在控制中心编辑磁贴"
                                    }
                                }
                            }
                        } catch (error: SecurityException) {
                            addingTile = false
                            tileMessage = "系统拒绝添加磁贴：${error.message}"
                            DragShareLog.w("DragShare/PointCapture", "tile add request denied", error)
                        } catch (error: IllegalArgumentException) {
                            addingTile = false
                            tileMessage = "磁贴配置不可用：${error.message}"
                            DragShareLog.w("DragShare/PointCapture", "tile component unavailable", error)
                        }
                    },
                )
            },
        ),
    )
}
