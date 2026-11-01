package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.net.Uri;

import com.leaf.hyperdragshare.gestures.config.ConfigStore;
import com.leaf.hyperdragshare.gestures.diagnostics.CompatibilityReport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a support bundle from independently available diagnostics; redaction runs
 * through {@link DiagnosticRedactionFilter} only. */
final class DiagnosticBundleExporter {
    private static final String REPORT_DIRECTORY = "diagnostics";
    private static final int MAX_RUNTIME_LOG_CHARS = 1_000_000;

    private DiagnosticBundleExporter() { }

    static final class Result {
        final File file;
        final String missingLogReason;

        Result(File file, String missingLogReason) {
            this.file = file;
            this.missingLogReason = missingLogReason;
        }
    }

    static String fileName() {
        return "diagnostic-bundle-" + new SimpleDateFormat(
                "yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date()) + ".zip";
    }

    static Result create(Context context, String stateSummary) throws IOException {
        if (context == null) throw new IOException("Missing application context");
        Context app = context.getApplicationContext();
        File directory = new File(app.getCacheDir(), REPORT_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create diagnostic report directory");
        }
        boolean fullDiagnostics = DragShareSettings.readFullDiagnostics(app);

        String compatibility = null;
        String compatibilityFailure = null;
        try {
            compatibility = CompatibilityReport.build(app, new ConfigStore(app));
        } catch (Throwable error) {
            compatibilityFailure = safeFailure(error);
        }

        File rawRuntime = null;
        String runtime = null;
        String missingLogReason = null;
        DragShareSettings settings = DragShareSettings.readLocal(app);
        if (settings.logLevel == DragShareSettings.LOG_LEVEL_DISABLED) {
            missingLogReason = "运行日志未启用。";
        } else if (settings.logDestination != DragShareSettings.LOG_DESTINATION_FILE) {
            missingLogReason = "运行日志写入系统日志，应用无法读取原始日志。";
        } else {
            try {
                rawRuntime = File.createTempFile("runtime-log-", ".tmp", directory);
                DragShareLog.exportTo(app, Uri.fromFile(rawRuntime));
                runtime = filterRuntimeLog(rawRuntime, fullDiagnostics);
                if (runtime.isEmpty()) {
                    missingLogReason = "没有可导出的结构化运行诊断记录。";
                    runtime = null;
                }
            } catch (Throwable error) {
                missingLogReason = safeFailure(error);
            } finally {
                if (rawRuntime != null && rawRuntime.exists() && !rawRuntime.delete()) {
                    rawRuntime.deleteOnExit();
                }
            }
        }

        return writeBundle(directory, stateSummary, compatibility, compatibilityFailure,
                runtime, missingLogReason, fullDiagnostics);
    }

    /** Package-visible assembly seam keeps behavior tests independent of root and Android state. */
    static Result writeBundle(File directory, String stateSummary, String compatibility,
                              String compatibilityFailure, String runtime,
                              String missingLogReason, boolean fullDiagnostics) throws IOException {
        if (directory == null) throw new IOException("Missing report directory");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create diagnostic report directory");
        }
        File output = new File(directory, fileName());
        String safeRuntime = filterText(runtime, fullDiagnostics);
        boolean hasRuntime = !safeRuntime.isEmpty();
        String logReason = hasRuntime ? null : nonEmpty(missingLogReason,
                runtime == null || runtime.isEmpty()
                        ? "Runtime log could not be collected."
                        : "No structured runtime diagnostics were available.");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            addText(zip, "summary.txt", buildSummary(stateSummary, fullDiagnostics));
            addText(zip, "root-connection.txt",
                    filterText(RootConnectionDiagnostics.summary(), fullDiagnostics));
            String operationTrace = OperationTrace.summary();
            addText(zip, "operation-trace.txt", operationTrace.isEmpty()
                    ? "No recent structured operations were recorded in the MMGG app process.\n"
                    : "Controlled operation trace (bounded, MMGG app process):\n"
                            + filterText(operationTrace, fullDiagnostics));
            if (compatibility != null) {
                addText(zip, "compatibility.txt", filterText(compatibility, fullDiagnostics));
            }
            if (hasRuntime) addText(zip, "runtime-log.txt", safeRuntime);
            addText(zip, "manifest.txt", buildManifest(fullDiagnostics, compatibility != null,
                    compatibilityFailure, hasRuntime, logReason));
        } catch (IOException | RuntimeException error) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Unable to create diagnostic bundle", error);
        }
        return new Result(output, logReason);
    }

    static String filterRuntimeLog(File rawFile, boolean fullDiagnostics) throws IOException {
        RuntimeTail tail = new RuntimeTail();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(rawFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                tail.add(DiagnosticRedactionFilter.line(line, fullDiagnostics));
            }
        }
        return tail.text();
    }

    private static String filterText(String raw, boolean fullDiagnostics) {
        return raw == null || raw.isEmpty() ? "" : DiagnosticRedactionFilter.text(raw, fullDiagnostics);
    }

    private static final class RuntimeTail {
        private final Deque<String> lines = new ArrayDeque<>();
        private int chars;

        void add(String line) {
            int length = line.length() + 1;
            while (!lines.isEmpty() && chars + length > MAX_RUNTIME_LOG_CHARS) {
                chars -= lines.removeFirst().length() + 1;
            }
            if (length <= MAX_RUNTIME_LOG_CHARS) {
                lines.addLast(line);
                chars += length;
            }
        }

        String text() {
            StringBuilder result = new StringBuilder(chars);
            for (String line : lines) result.append(line).append('\n');
            return result.toString();
        }
    }

    private static String buildSummary(String stateSummary, boolean fullDiagnostics) {
        StringBuilder out = new StringBuilder();
        out.append("MMGG diagnostic bundle\n")
                .append("Version: ").append(BuildConfig.VERSION_NAME).append(" ( ")
                .append(BuildConfig.VERSION_CODE).append(" )\n")
                .append("Environment: ").append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL).append("; Android ")
                .append(android.os.Build.VERSION.RELEASE).append(" (SDK ")
                .append(android.os.Build.VERSION.SDK_INT).append(")\n\n")
                .append("Available state:\n")
                .append(stateSummary == null || stateSummary.trim().isEmpty()
                        ? "State summary unavailable.\n"
                        : DiagnosticRedactionFilter.text(stateSummary, fullDiagnostics));
        out.append("\nRoot connection events: see root-connection.txt.\n");
        if (out.charAt(out.length() - 1) != '\n') out.append('\n');
        return out.toString();
    }

    private static String buildManifest(boolean fullDiagnostics, boolean compatibilityAvailable,
                                        String compatibilityFailure,
                                        boolean runtimeAvailable, String logReason) {
        StringBuilder out = new StringBuilder("Diagnostic bundle contents\n");
        out.append("summary.txt: included\n")
                .append("root-connection.txt: included\n")
                .append("operation-trace.txt: included (bounded, privacy-filtered, app-process events)\n")
                .append("compatibility.txt: ")
                .append(compatibilityAvailable ? "included" : "unavailable")
                .append('\n')
                .append("runtime-log.txt: ")
                .append(runtimeAvailable ? "included" : "unavailable")
                .append('\n');
        if (!compatibilityAvailable) {
            out.append("Compatibility report reason: ")
                    .append(DiagnosticRedactionFilter.text(
                            nonEmpty(compatibilityFailure, "Report generation failed."), fullDiagnostics))
                    .append('\n');
        }
        if (!runtimeAvailable) {
            out.append("Runtime log reason: ")
                    .append(DiagnosticRedactionFilter.text(logReason, fullDiagnostics)).append('\n')
                    .append("原始运行日志不可读取；operation-trace.txt与root-connection.txt仍包含当前可取得的受控摘要。\n");
        }
        out.append("打码规则（本次导出实际应用，规则见 docs/DIAGNOSTICS.md）：\n")
                .append("- 凭据/令牌字段 token|capability|authorization|bearer|password|passwd|secret|credential -> <redacted-secret>\n");
        if (!fullDiagnostics) {
            out.append("- 正文/剪贴板字段 payload|body|text|clipboard 及其后内容 -> <redacted-content>\n")
                    .append("- URI 查询参数 -> ?<redacted-query>（URI 路径原样保留）\n")
                    .append("Privacy: default mode redacts user content fields and URI query parameters; "
                            + "stacks, exception messages, package names, URI paths and timestamps are kept verbatim.\n");
        } else {
            out.append("完整诊断模式已开启：此文件包含操作细节；除凭据字段外全部原样记录。\n");
        }
        return out.toString();
    }

    private static String safeFailure(Throwable error) {
        return error == null ? "收集失败。" : error.toString();
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static void addText(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
