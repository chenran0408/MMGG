package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.preference.ArrowPreference

internal object HookFeatureService : XposedServiceHelper.OnServiceListener {
    val service = MutableStateFlow<XposedService?>(null)

    init {
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        this.service.value = service
    }

    override fun onServiceDied(service: XposedService) {
        this.service.compareAndSet(service, null)
    }
}

private data class FeatureSwitchState(
    val preferences: SharedPreferences,
    val runtime: String,
    val flags: HookFeatureFlags,
    val pending: Set<SettingModule>,
)

internal data class HookFeatureSettings(
    val entries: List<SettingEntry>,
    val flags: HookFeatureFlags?,
    val portalRestartPending: Boolean,
)

private fun readSwitchState(prefs: SharedPreferences, runtime: String, legacyGestures: Boolean): FeatureSwitchState {
    val flags = HookFeatureFlags.read(prefs)
    val pending = buildSet {
        if (HookFeatureChanges.pending(prefs, HookFeatureFlags.PORTAL, flags.portal, runtime)) add(SettingModule.PORTAL)
        if (HookFeatureChanges.pending(prefs, HookFeatureFlags.FREEFORM, flags.freeform, runtime)) add(SettingModule.FREEFORM)
        if (HookFeatureChanges.pending(prefs, HookFeatureFlags.GESTURES, flags.gestures(legacyGestures), runtime)) add(SettingModule.GESTURES)
    }
    return FeatureSwitchState(prefs, runtime, flags, pending)
}

@Composable
internal fun hookFeatureSettingEntries(
    context: Context,
    legacyGesturesEnabled: Boolean,
    onApply: () -> Unit,
): HookFeatureSettings {
    val service by HookFeatureService.service.collectAsState()
    var state by remember(service) { mutableStateOf<FeatureSwitchState?>(null) }
    val flags = state?.flags
    val preferences = state?.preferences
    val pending = state?.pending.orEmpty()
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(service, legacyGesturesEnabled) {
        val connected = service ?: return@LaunchedEffect
        state = withContext(Dispatchers.IO) {
            runCatching {
                val prefs = connected.getRemotePreferences(HookFeatureFlags.PREFS)
                readSwitchState(prefs, HookFeatureChanges.runtime(context), legacyGesturesEnabled)
            }.onFailure { DragShareLog.w("DragShare/Features", "Cannot read LSPosed feature switches", it) }
        }.getOrNull()
    }
    fun entry(module: SettingModule, group: String, key: String, checked: Boolean?): SettingEntry {
        val title = "启用${module.title}"
        val summary = when {
            checked == null -> "状态未确认，请检查 LSPosed 连接"
            module in pending -> "已保存，待软重启生效"
            else -> "修改后需软重启 Android"
        }
        return SettingEntry(
            id = if (module == SettingModule.GESTURES)
                com.leaf.hyperdragshare.gestures.config.ConfigContract.KEY_ENABLED else "feature.$key",
            title = title, module = module, group = group, summary = summary,
            enabled = preferences != null && !saving,
        ) { enabled ->
            if (checked == null) BasicComponent(title = title, summary = summary)
            else SwitchPreference(title = title, summary = summary, checked = checked,
                enabled = enabled, onCheckedChange = { value ->
                    val current = state ?: return@SwitchPreference
                    val prefs = current.preferences
                    if (saving || value == checked) return@SwitchPreference
                    saving = true
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching {
                                if (HookFeatureChanges.save(prefs, key, checked, value, current.runtime))
                                    readSwitchState(prefs, current.runtime, legacyGesturesEnabled)
                                else null
                            }.onFailure {
                                DragShareLog.w("DragShare/Features", "Cannot save LSPosed feature switch", it)
                            }.getOrNull()
                        }
                        if (saved != null && state?.preferences === prefs) state = saved
                        if (saved == null) {
                            Toast.makeText(context, "保存失败，请检查 LSPosed 连接",
                                Toast.LENGTH_SHORT).show()
                        }
                        saving = false
                    }
                })
        }
    }
    val featureEntries = listOf(
        entry(SettingModule.PORTAL, "总开关", HookFeatureFlags.PORTAL, flags?.portal),
        entry(SettingModule.GESTURES, "总开关与通用", HookFeatureFlags.GESTURES,
            flags?.gestures(legacyGesturesEnabled)),
        entry(SettingModule.FREEFORM, "总开关", HookFeatureFlags.FREEFORM, flags?.freeform),
    )
    val applyTitle = RuntimeAction.Android.title
    val applyEntries = featureEntries.filter { it.module in pending }.map { entry ->
        SettingEntry(
            id = "runtime.apply.${entry.module.name}", title = applyTitle,
            module = entry.module, group = entry.group, keywords = "应用更改",
            open = onApply,
        ) { enabled ->
            ArrowPreference(title = applyTitle, enabled = enabled, onClick = onApply)
        }
    }
    return HookFeatureSettings(featureEntries + applyEntries, flags, SettingModule.PORTAL in pending)
}
