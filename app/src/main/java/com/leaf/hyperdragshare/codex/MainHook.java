package com.leaf.hyperdragshare.codex;

import com.leaf.hyperdragshare.gestures.xposed.FanFreeformHook;
import com.leaf.hyperdragshare.gestures.xposed.SystemServerFreeformConfig;
import com.leaf.hyperdragshare.compat.xposed.IXposedHookLoadPackage;
import com.leaf.hyperdragshare.compat.xposed.callbacks.XC_LoadPackage;

public final class MainHook implements IXposedHookLoadPackage {
    static final String TAPLUS_PACKAGE = "com.miui.contentextension";
    static final String WECHAT_PACKAGE = "com.tencent.mm";
    static final String COOLAPK_PACKAGE = "com.coolapk.market";
    static final String AICR_PACKAGE = "com.xiaomi.aicr";
    static final String TRUST_SERVICE_PACKAGE = "com.xiaomi.trustservice";
    static final String ANDROID_PACKAGE = "android";
    static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    static final String SECURITY_CENTER_PACKAGE = "com.miui.securitycenter";
    private static volatile boolean sidebarHookInstalled;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        HookFeatureFlags flags = HookFeatureFlags.startup();
        if (TAPLUS_PACKAGE.equals(lpparam.packageName)) {
            if (flags.portal()) PortalHooks.install(lpparam.classLoader);
        } else if (WECHAT_PACKAGE.equals(lpparam.packageName)
                || COOLAPK_PACKAGE.equals(lpparam.packageName)) {
            if (!flags.portal()) return;
            // Share the same custom-text adapter and read-only diagnostics in the
            // already scoped hosts. Native input modes and events remain untouched.
            ContentCatcherProbeHooks.install(
                    lpparam.classLoader, lpparam.packageName, lpparam.processName,
                    ContentCatcherNativeTextHooks::install);
        } else if (AICR_PACKAGE.equals(lpparam.packageName)) {
            AicrCopyHooks.install(lpparam.classLoader);
            if (flags.portal()) AicrCoreAccessHooks.install();
        } else if (TRUST_SERVICE_PACKAGE.equals(lpparam.packageName)) {
            TrustServiceRiskHooks.install(lpparam);
        } else if (SYSTEM_UI_PACKAGE.equals(lpparam.packageName)
                || "com.miui.home".equals(lpparam.packageName)) {
            if (flags.windowRuntime()) {
                new FanFreeformHook().handleLoadPackage(lpparam);
            }
        } else if (SECURITY_CENTER_PACKAGE.equals(lpparam.packageName)
                && "com.miui.securitycenter:ui".equals(lpparam.processName)
                && android.os.Build.VERSION.SDK_INT >= 37) {
            if (!flags.portal() && !flags.windowRuntime()) return;
            com.leaf.hyperdragshare.compat.xposed.XposedHelpers.findAndHookMethod(android.app.Application.class,
                    "attach", android.content.Context.class, new com.leaf.hyperdragshare.compat.xposed.XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (sidebarHookInstalled || param.hasThrowable()) return;
                    android.content.Context context = (android.content.Context) param.args[0];
                    if (!SECURITY_CENTER_PACKAGE.equals(context.getPackageName())) return;
                    long version = -1;
                    try {
                        version = context.getPackageManager()
                                .getPackageInfo(SECURITY_CENTER_PACKAGE, 0).getLongVersionCode();
                    } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException error) {
                        DragShareLog.w("DragShare/Sidebar", "Cannot read SecurityCenter version", error);
                    }
                    sidebarHookInstalled = true;
                    NativeSidebarHooks.install(lpparam.classLoader, flags.portal(), version);
                }
            });
        } else if (ANDROID_PACKAGE.equals(lpparam.packageName)
                && ANDROID_PACKAGE.equals(lpparam.processName)) {
            if (flags.portal()) {
                RootProcessFrameworkHooks.install(lpparam.classLoader);
                BackTapFrameworkHooks.install(lpparam);
            }
            if (flags.portal() || flags.windowRuntime()) {
                OverlayNotificationHooks.install(lpparam.classLoader);
            }
            if (flags.freeform()) {
                SystemServerFreeformConfig.install(
                        lpparam.packageName, lpparam.processName, lpparam.classLoader);
            }
        }
    }
}
