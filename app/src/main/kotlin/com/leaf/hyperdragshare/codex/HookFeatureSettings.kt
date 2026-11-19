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

@Composable
internal fun hookFeatureSettingEntries(
    context: Context,
    legacyGesturesEnabled: Boolean,
    pendingRestartModules: List<String>,
    onSwitchSaved: (SettingModule) -> Unit,
    onApply: () -> Unit,
): Pair<List<SettingEntry>, HookFeatureFlags?> {
    val service by HookFeatureService.service.collectAsState()
    var preferences by remember { mutableStateOf<SharedPreferences?>(null) }
    var flags by remember { mutableStateOf<HookFeatureFlags?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(service) {
        preferences = null
        flags = null
        val connected = service ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val prefs = connected.getRemotePreferences(HookFeatureFlags.PREFS)
                prefs to HookFeatureFlags.read(prefs)
            }.onFailure { DragShareLog.w("DragShare/Features", "Cannot read LSPosed feature switches", it) }
        }.getOrNull()
        preferences = result?.first
        flags = result?.second
    }
    fun entry(module: SettingModule, group: String, key: String, checked: Boolean): SettingEntry {
        val title = "启用${module.title}"
        val summary = when {
            preferences == null -> "连接 LSPosed 后可修改"
            module.name in pendingRestartModules -> "已保存，待软重启生效"
            else -> "修改后需软重启 Android"
        }
        return SettingEntry(
            id = if (module == SettingModule.GESTURES)
                com.leaf.hyperdragshare.gestures.config.ConfigContract.KEY_ENABLED else "feature.$key",
            title = title, module = module, group = group, summary = summary,
            enabled = preferences != null && !saving,
        ) { enabled ->
            SwitchPreference(title = title, summary = summary, checked = checked,
                enabled = enabled, onCheckedChange = { value ->
                    val prefs = preferences ?: return@SwitchPreference
                    if (saving || value == checked) return@SwitchPreference
                    saving = true
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching {
                                if (prefs.edit().putBoolean(key, value).commit()) HookFeatureFlags.read(prefs)
                                else null
                            }.onFailure {
                                DragShareLog.w("DragShare/Features", "Cannot save LSPosed feature switch", it)
                            }.getOrNull()
                        }
                        if (saved != null) {
                            if (preferences === prefs) flags = saved
                            onSwitchSaved(module)
                        }
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
        entry(SettingModule.PORTAL, "总开关", HookFeatureFlags.PORTAL, flags?.portal ?: true),
        entry(SettingModule.GESTURES, "总开关与通用", HookFeatureFlags.GESTURES,
            flags?.gestures(legacyGesturesEnabled) ?: legacyGesturesEnabled),
        entry(SettingModule.FREEFORM, "总开关", HookFeatureFlags.FREEFORM, flags?.freeform ?: true),
    )
    val applyTitle = RuntimeAction.Android.title
    val applyEntries = featureEntries.filter { it.module.name in pendingRestartModules }.map { entry ->
        SettingEntry(
            id = "runtime.apply.${entry.module.name}", title = applyTitle,
            module = entry.module, group = entry.group, keywords = "应用更改",
            open = onApply,
        ) { enabled ->
            ArrowPreference(title = applyTitle, enabled = enabled, onClick = onApply)
        }
    }
    return (featureEntries + applyEntries) to flags
}
