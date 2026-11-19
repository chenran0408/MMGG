package com.leaf.hyperdragshare.codex

import com.leaf.hyperdragshare.compat.xposed.LegacyBridge
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** libxposed API 102 entry point. Existing hooks are routed through LegacyBridge. */
class DragShareModule : XposedModule() {
    private var systemServer = false
    private var processName: String? = null

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        systemServer = param.isSystemServer
        processName = param.processName
        val flags = try {
            HookFeatureFlags.read(getRemotePreferences(HookFeatureFlags.PREFS))
        } catch (error: RuntimeException) {
            log(4, "HyperDragShare", "Cannot read feature switches; skipping feature hooks", error)
            HookFeatureFlags(false, false, false)
        }
        HookFeatureFlags.setStartup(flags)
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        LegacyBridge.bind(this, "android", "android", param.classLoader, null)
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (systemServer) return
        val packageName = param.packageName
        LegacyBridge.bind(this, packageName, processName ?: packageName,
            param.defaultClassLoader, param.applicationInfo)
    }
}
