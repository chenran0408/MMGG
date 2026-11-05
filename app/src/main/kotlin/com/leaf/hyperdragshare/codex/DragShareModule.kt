package com.leaf.hyperdragshare.codex

import com.leaf.hyperdragshare.compat.xposed.LegacyBridge
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** libxposed API 102 entry point. Existing hooks are routed through LegacyBridge. */
class DragShareModule : XposedModule() {
    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        val packageName = param.packageName
        val processName = runCatching {
            param.applicationInfo?.processName ?: packageName
        }.getOrDefault(packageName)
        LegacyBridge.bind(this, packageName, processName, param.defaultClassLoader, param.applicationInfo)
    }
}
