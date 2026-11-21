package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.os.SystemClock;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** User-requested app scope reload or a restart of the target Android runtime/device. */
final class ScopeRestart {
    private static final String TAG = "DragShare/ScopeRestart";
    private ScopeRestart() {}

    static boolean reloadAppScopes(Context context, HookFeatureFlags flags)
            throws IOException {
        long started = SystemClock.elapsedRealtime();
        boolean portal = flags.portal();
        boolean copyDirect = IntegrationSettings.isCopyDirectEnabled(context);
        boolean readerRequired = portal && ThumbTriggerSettings.read(context).enabled;
        int oldPid = portal ? ModuleActivation.injectedProcessId(context) : 0;
        if (portal) ModuleActivation.clearPortalReport(context);
        RootProcessKeepAlive.Result keepAlive = portal
                ? ModuleActivation.ensurePortalRootKeepAlive(context) : null;
        boolean notificationReady = !copyDirect || ModuleActivation.ensureCopyDirectCardReady(context);
        java.util.List<String> commands = appScopeCommands(portal, copyDirect);
        if (commands.isEmpty()) return false;
        runRoot(String.join(" && ", commands), "重载传送门与 AI 引擎");
        if (!notificationReady) throw new IOException("系统复制直达卡片未就绪，请查看诊断日志");
        if (!portal) return true;
        DragShareLog.i(TAG, "reload commands elapsed_ms=" + (SystemClock.elapsedRealtime() - started));
        long deadline = SystemClock.uptimeMillis() + 10000L;
        try {
            while (SystemClock.uptimeMillis() < deadline) {
                int pid = ModuleActivation.injectedProcessId(context);
                if (pid > 0 && pid != oldPid && ModuleActivation.isCurrentBuildInjected(context)) {
                    if (ModuleActivation.isPortalServiceReady(context)
                            && ModuleActivation.isCurrentBuildPortalRootGranted(context)
                            && ModuleActivation.isRootInputReady(context)
                            && (!readerRequired || ModuleActivation.isPressReaderReady(context))) {
                        if (!keepAlive.ready) {
                            throw new IOException("Root 一键清理保护未就绪（传送门服务已恢复）："
                                    + keepAlive.detail);
                        }
                        DragShareLog.i(TAG, "new portal foreground service/input/reader confirmed pid=" + pid
                                + " elapsed_ms=" + (SystemClock.elapsedRealtime() - started));
                        return true;
                    }
                    String failure = ModuleActivation.rootInputError(context);
                    if (!failure.isEmpty()) throw new IOException("Root 输入未恢复：" + failure);
                    if (readerRequired) {
                        failure = ModuleActivation.pressReaderError(context);
                        if (!failure.isEmpty()) throw new IOException("按压取词未恢复：" + failure);
                    }
                }
                Thread.sleep(100L);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("作用域重载等待被中断", error);
        }
        String keepAliveError = keepAlive.ready ? "" : "；Root 一键清理保护未就绪：" + keepAlive.detail;
        throw new IOException("传送门尚未就绪，请查看运行诊断"
                + keepAliveError);
    }

    static java.util.List<String> appScopeCommands(boolean portal, boolean copyDirect) {
        java.util.List<String> commands = new java.util.ArrayList<>();
        if (portal) {
            commands.add("am force-stop --user current com.miui.contentextension");
            commands.add(ModuleActivation.portalStartCommand());
        }
        if (portal || copyDirect) commands.add("am force-stop --user current com.xiaomi.aicr");
        return commands;
    }

    static String systemUiRestartCommand() {
        return "killall com.android.systemui && { sidebar_pid=$(pidof com.miui.securitycenter:ui); "
                + "if [ -n \"$sidebar_pid\" ]; then kill $sidebar_pid; fi; }";
    }

    static void restartSystemUi() throws IOException {
        runRoot(systemUiRestartCommand(), "重启系统界面");
    }

    static void restartAndroidRuntime() throws IOException {
        runRoot("setprop ctl.restart zygote", "软重启 Android");
    }

    static void restartDevice() throws IOException {
        runRoot("reboot", "整机重启");
    }

    static void reloadTrustService() throws IOException {
        runRoot("trust_pid=$(pidof com.xiaomi.trustservice); "
                        + "if [ -n \"$trust_pid\" ]; then kill $trust_pid; fi",
                "重启 MiTrustService");
    }

    private static void runRoot(String command, String action) throws IOException {
        DragShareLog.i(TAG, "user requested " + action);
        Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        try (InputStream output = process.getInputStream(); OutputStream input = process.getOutputStream()) {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(action + "请求超时");
            }
            if (process.exitValue() != 0) {
                String reason = new String(output.readAllBytes(), StandardCharsets.UTF_8).trim();
                throw new IOException(reason.isEmpty() ? "Root未允许" + action : reason);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException(action + "请求被中断", error);
        } finally {
            process.destroy();
        }
    }
}
