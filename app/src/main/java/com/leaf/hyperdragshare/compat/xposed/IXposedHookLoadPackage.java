package com.leaf.hyperdragshare.compat.xposed;

/**
 * Source compatibility marker for the existing hook implementation.
 * Runtime dispatch is provided by LegacyBridge on libxposed API 102.
 */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(com.leaf.hyperdragshare.compat.xposed.callbacks.XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
