package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;
import org.json.JSONObject;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class DiagnosticBundleExporterTest {
    private File directory;

    @Before public void setUp() throws Exception {
        directory = Files.createTempDirectory("diagnostic-bundle-test").toFile();
        OperationTrace.clearForTests();
    }

    @After public void tearDown() {
        OperationTrace.clearForTests();
        deleteRecursively(directory);
    }

    @Test public void completeBundleContainsSummaryCompatibilityAndSafeRuntimeTrace() throws Exception {
        String runtime = "2026-10-24 12:30:01.123 I pid=44 uid=0 thread=main "
                + "DragShare/Operation: proc=mod-44 op=press-44-2 stage=stash "
                + "outcome=completed asset=0123456789abcdef0123456789abcdef\n";

        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary(), validCompatibility(), null,
                runtime, null, false);

        assertTrue(result.file.isFile());
        assertNull(result.missingLogReason);
        Map<String, String> entries = readZip(result.file);
        assertTrue(entries.get("summary.txt").contains("root=READY"));
        assertTrue(entries.get("compatibility.txt").contains("接口状态: ready"));
        assertTrue(entries.get("runtime-log.txt").contains("stage=stash outcome=completed"));
        assertTrue(entries.get("manifest.txt").contains("compatibility.txt: included"));
        assertTrue(DiagnosticBundleExporter.fileName().endsWith(".zip"));
    }

    @Test public void compatibilityFailureDoesNotPreventRuntimeExport() throws Exception {
        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary(), null, "IllegalStateException", safeRuntimeTrace(), null, false);

        Map<String, String> entries = readZip(result.file);
        assertFalse(entries.containsKey("compatibility.txt"));
        assertTrue(entries.containsKey("runtime-log.txt"));
        assertTrue(entries.get("manifest.txt").contains("Compatibility report reason: IllegalStateException"));
        assertNull(result.missingLogReason);
    }

    @Test public void unavailableRuntimeStillExportsOtherInformationWithoutRecommendingLogChanges() throws Exception {
        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary(), validCompatibility(), null, null,
                "运行日志未启用。", false);

        Map<String, String> entries = readZip(result.file);
        assertTrue(entries.containsKey("summary.txt"));
        assertTrue(entries.containsKey("compatibility.txt"));
        assertFalse(entries.containsKey("runtime-log.txt"));
        assertEquals("运行日志未启用。",
                result.missingLogReason);
        assertTrue(entries.get("manifest.txt").contains(result.missingLogReason));
        assertTrue(entries.get("manifest.txt").contains("原始运行日志不可读取"));
        assertFalse(entries.get("manifest.txt").contains("自由文本与异常详情均已过滤"));
        assertFalse(entries.get("manifest.txt").contains("启用文件日志"));
    }

    @Test public void emptyRuntimeIsReportedAsMissingInsteadOfCreatingAnEmptyLogEntry() throws Exception {
        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, null, null, "Report failed", "", "No safe structured runtime diagnostics were available.", false);

        Map<String, String> entries = readZip(result.file);
        assertFalse(entries.containsKey("runtime-log.txt"));
        assertEquals("No safe structured runtime diagnostics were available.", result.missingLogReason);
        assertTrue(entries.get("manifest.txt").contains("Report failed"));
    }

    @Test public void allSourcesMissingStillProducesAReadableSummaryAndManifest() throws Exception {
        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary(), null, "Report unavailable", null,
                "File log does not exist.", false);

        Map<String, String> entries = readZip(result.file);
        assertEquals(4, entries.size());
        assertTrue(entries.get("summary.txt").contains("root=READY"));
        assertTrue(entries.containsKey("root-connection.txt"));
        assertTrue(entries.containsKey("operation-trace.txt"));
        assertTrue(entries.get("manifest.txt").contains("root-connection.txt: included"));
        assertTrue(entries.get("manifest.txt").contains("compatibility.txt: unavailable"));
        assertTrue(entries.get("manifest.txt").contains("runtime-log.txt: unavailable"));
        assertTrue(entries.get("manifest.txt").contains("File log does not exist."));
    }

    @Test public void rootConnectionSummaryIsPresentWithoutFileLogsAndContainsOnlyControlledEvents() throws Exception {
        String attempt = RootConnectionDiagnostics.newAttempt();
        RootConnectionDiagnostics.event(attempt, "request");
        RootConnectionDiagnostics.event(attempt, "helper_start");
        RootConnectionDiagnostics.event(attempt, "already_registered");
        RootConnectionDiagnostics.retry(attempt, 250L);
        RootConnectionDiagnostics.event(attempt, "permission_denied");
        RootConnectionDiagnostics.event(attempt, "connect_failed");
        RootConnectionDiagnostics.event(attempt, "connect_success");
        RootConnectionDiagnostics.event(attempt, "lease_ready");
        RootConnectionDiagnostics.event(attempt, "binder_death");
        RootConnectionDiagnostics.event(attempt, "release");
        RootConnectionDiagnostics.event(attempt, "release");

        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary(), null, null, null, "Runtime log unavailable.", false);

        Map<String, String> entries = readZip(result.file);
        String root = entries.get("root-connection.txt");
        assertNotNull(root);
        assertTrue(root.contains("attempt=" + attempt + " proc="));
        assertTrue(root.contains("outcome=already_registered"));
        assertTrue(root.contains("retry_interval_ms=250"));
        assertTrue(root.contains("outcome=binder_death"));
        assertTrue(root.contains("outcome=release"));
        assertTrue(root.contains("outcome=permission_denied"));
        assertTrue(root.contains("outcome=connect_failed"));
        assertTrue(root.contains("outcome=connect_success"));
        assertTrue(root.contains("outcome=lease_ready"));
        assertFalse(root.contains("Throwable"));
        assertFalse(root.contains("content://"));
        assertFalse(root.contains("token="));
        assertFalse(entries.containsKey("runtime-log.txt"));
        assertTrue(entries.get("operation-trace.txt").contains("No recent structured operations"));
    }

    @Test public void rootStartupCodesMapOnlyToKnownConnectionCategories() throws Exception {
        assertEquals("already_registered", RootNodeCaptureClient.startupFailureCode(
                new JSONObject().put("error_code", "uiautomation_already_registered")));
        assertEquals("permission_denied", RootNodeCaptureClient.startupFailureCode(
                new JSONObject().put("error_code", "uiautomation_permission_denied")));
        assertEquals("connect_failed", RootNodeCaptureClient.startupFailureCode(
                new JSONObject().put("error_code", "uiautomation_connect_failed")));
        assertEquals("startup_rejected", RootNodeCaptureClient.startupFailureCode(
                new JSONObject().put("error_code", "content://private/token")));
    }

    @Test public void runtimeFilterKeepsStacksExceptionsPackageNamesAndUriPathsInDefaultMode() throws Exception {
        File raw = new File(directory, "raw.log");
        String text = "2026-10-24 12:30:00.123 I pid=44 uid=0 thread=main DragShare/Log: "
                + "diagnostic file opened session=abc-44 build=1.7.219-beta.20/262 device=Google Pixel android=15/35 "
                + "level=info destination=file max_bytes=4194304 sampled_debug=true\n"
                + "2026-10-24 12:30:01.123 I pid=44 uid=0 thread=main "
                + "DragShare/Operation: proc=mod-44 op=press-44-2 stage=stash "
                + "outcome=completed asset=0123456789abcdef0123456789abcdef\n"
                + "2026-10-24 12:30:01.124 I pid=44 uid=0 thread=main "
                + "DragShare/Operation: proc=mod-44 op=- stage=image_access "
                + "outcome=descriptor_opened asset=0123456789abcdef0123456789abcdef "
                + "callerUid=12345 read=unknown delivery=unknown\n"
                + "2026-10-24 12:30:02.123 E pid=44 uid=0 thread=main "
                + "DragShare/Actions: read failed payload=private message body\n"
                + "java.lang.IllegalStateException: content://secret/capability\n"
                + "\tat com.leaf.hyperdragshare.codex.DragShareController.finish(DragShareController.java:1234)\n"
                + "2026-10-24 12:30:03.123 I pid=44 uid=0 thread=main DragShare/Press: "
                + "source=com.tencent.mm body=private-chat image=content://private/image?token=abc123\n";
        Files.write(raw.toPath(), text.getBytes(StandardCharsets.UTF_8));

        String safe = DiagnosticBundleExporter.filterRuntimeLog(raw, false);

        assertTrue(safe.contains("stage=stash outcome=completed"));
        assertTrue(safe.contains("diagnostic file opened session=abc-44 build=1.7.219-beta.20/262"));
        assertTrue(safe.contains("callerUid=12345 read=unknown delivery=unknown"));
        assertTrue(safe.contains("java.lang.IllegalStateException: content://secret/capability"));
        assertTrue(safe.contains("\tat com.leaf.hyperdragshare.codex.DragShareController.finish"
                + "(DragShareController.java:1234)"));
        assertTrue(safe.contains("source=com.tencent.mm"));
        assertTrue(safe.contains("image=content://private/image?<redacted-query>"));
        assertFalse(safe.contains("private message body"));
        assertFalse(safe.contains("private-chat"));
        assertFalse(safe.contains("token=abc123"));
    }

    @Test public void fullDiagnosticsModeKeepsContentButStillRedactsCredentialFields() throws Exception {
        File raw = new File(directory, "raw-full.log");
        String text = "2026-10-24 12:30:02.123 E pid=44 uid=0 thread=main "
                + "DragShare/Actions: failed body=private message text token=abc123\n";
        Files.write(raw.toPath(), text.getBytes(StandardCharsets.UTF_8));

        String safe = DiagnosticBundleExporter.filterRuntimeLog(raw, true);

        assertTrue(safe.contains("body=private message text"));
        assertFalse(safe.contains("token=abc123"));
        assertTrue(safe.contains("token=<redacted-secret>"));
    }

    @Test public void redactionCoversSpacedCompactAndMultilineLogLayouts() throws Exception {
        File raw = new File(directory, "raw-layouts.log");
        String spaced = "2026-10-24 12:30:01.123 I pid=44 uid=0 thread=main DragShare/Press: "
                + "source=com.tencent.mm password=hunter2\n";
        String compact = "2026-10-24 12:30:01.123 I DragShare/Operation: proc=mod-44 op=press "
                + "stage=stash outcome=completed token=abc123\n";
        String multiline = "java.lang.IllegalStateException: boom\n"
                + "\tat com.example.Foo.bar(Foo.java:7)\n";
        Files.write(raw.toPath(), (spaced + compact + multiline).getBytes(StandardCharsets.UTF_8));

        String safe = DiagnosticBundleExporter.filterRuntimeLog(raw, false);

        assertTrue(safe.contains("source=com.tencent.mm"));
        assertFalse(safe.contains("hunter2"));
        assertTrue(safe.contains("op=press stage=stash outcome=completed"));
        assertFalse(safe.contains("token=abc123"));
        assertTrue(safe.contains("java.lang.IllegalStateException: boom"));
        assertTrue(safe.contains("\tat com.example.Foo.bar(Foo.java:7)"));
    }

    @Test public void manifestListsAppliedRedactionRulesAndFullModeNotice() throws Exception {
        DiagnosticBundleExporter.Result normal = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary(), null, null, null, "logs unavailable", false);
        String normalManifest = readZip(normal.file).get("manifest.txt");
        assertTrue(normalManifest.contains("打码规则"));
        assertTrue(normalManifest.contains("<redacted-secret>"));
        assertTrue(normalManifest.contains("<redacted-content>"));
        assertTrue(normalManifest.contains("<redacted-query>"));
        assertFalse(normalManifest.contains("此文件包含操作细节"));

        DiagnosticBundleExporter.Result full = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary(), null, null, null, "logs unavailable", true);
        String fullManifest = readZip(full.file).get("manifest.txt");
        assertTrue(fullManifest.contains("此文件包含操作细节"));
        assertTrue(fullManifest.contains("<redacted-secret>"));
        assertFalse(fullManifest.contains("<redacted-content>"));
    }

    @Test public void runtimeFilterFindsNewestCurrentTraceAfterLargeArchivedPrefix() throws Exception {
        File raw = new File(directory, "archived-and-current.log");
        StringBuilder content = new StringBuilder(1_100_200);
        for (int i = 0; i < 1_100_000; i++) content.append('x');
        content.append("\n").append(traceLine("current-op"));
        Files.write(raw.toPath(), content.toString().getBytes(StandardCharsets.UTF_8));

        String safe = DiagnosticBundleExporter.filterRuntimeLog(raw, false);

        assertTrue(safe.contains("op=current-op"));
        assertFalse(safe.contains("xxxxxxxx"));
    }

    @Test public void imageSourcePackageAndUriPathRemainVisibleWhileBodyIsRedacted() throws Exception {
        File raw = new File(directory, "image-decision.log");
        String prefix = "2026-10-24 18:10:00.123 I pid=44 uid=0 thread=main ";
        Files.write(raw.toPath(), (prefix + "DragShare/Operation: proc=mod-44 op=press-44-2 "
                + "stage=capture_kind outcome=wechat_image_screen_capture asset=-\n"
                + prefix + "DragShare/Operation: proc=mod-44 op=press-44-2 "
                + "stage=image_drag outcome=rejected_ocr_region_drag_screen_capture asset=-\n"
                + prefix + "DragShare/Press: source=com.tencent.mm body=private-chat "
                + "image=content://private/image\n").getBytes(StandardCharsets.UTF_8));

        String filtered = DiagnosticBundleExporter.filterRuntimeLog(raw, false);
        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary(), validCompatibility(), null, filtered, null, false);
        String exported = readZip(result.file).get("runtime-log.txt");

        assertTrue(exported.contains("stage=capture_kind outcome=wechat_image_screen_capture"));
        assertTrue(exported.contains("stage=image_drag outcome=rejected_ocr_region_drag_screen_capture"));
        assertTrue(exported.contains("com.tencent.mm"));
        assertTrue(exported.contains("image=content://private/image"));
        assertFalse(exported.contains("private-chat"));
    }

    @Test public void runtimeFilterRetainsLatestTracesWhenStructuredDataExceedsBudget() throws Exception {
        File raw = new File(directory, "many-traces.log");
        StringBuilder content = new StringBuilder(2_000_000);
        for (int i = 0; i < 10_000; i++) {
            content.append(traceLine(String.format(Locale.US, "trace%05d", i)));
        }
        Files.write(raw.toPath(), content.toString().getBytes(StandardCharsets.UTF_8));

        String safe = DiagnosticBundleExporter.filterRuntimeLog(raw, false);

        assertTrue(safe.length() <= 1_000_000);
        assertTrue(safe.contains("op=trace09999"));
        assertFalse(safe.contains("op=trace00000"));
    }

    @Test public void summaryKeepsReportedTextAndRedactsCredentialValues() throws Exception {
        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary() + " uri=content://secret/item capability=abc123", null, null,
                null, "logs unavailable", false);
        String summary = readZip(result.file).get("summary.txt");
        assertFalse(summary.contains("abc123"));
        assertTrue(summary.contains("uri=content://secret/item"));
        assertTrue(summary.contains("capability=<redacted-secret>"));
    }

    @Test public void invalidOutputLocationFailsWithoutLeavingPartialBundle() throws Exception {
        File notDirectory = new File(directory, "file");
        assertTrue(notDirectory.createNewFile());
        try {
            DiagnosticBundleExporter.writeBundle(notDirectory, validStateSummary(),
                    validCompatibility(), null, safeRuntimeTrace(), null, false);
            fail("Expected report directory creation to fail");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
        assertEquals(1, directory.listFiles().length);
    }

    @Test public void createWithDisabledFileLoggingStillExportsAndLeavesLoggingSettingsUntouched() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                "drag_share_settings", Context.MODE_PRIVATE);
        preferences.edit().putInt("log_level", DragShareSettings.LOG_LEVEL_DISABLED)
                .putInt("log_destination", DragShareSettings.LOG_DESTINATION_FILE).commit();
        int originalLevel = preferences.getInt("log_level", -1);
        int originalDestination = preferences.getInt("log_destination", -1);
        OperationTrace.event("press-export-disabled-1", "capture", "returned");

        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.create(
                context, validStateSummary());

        assertEquals("运行日志未启用。",
                result.missingLogReason);
        Map<String, String> entries = readZip(result.file);
        assertTrue(entries.containsKey("compatibility.txt"));
        assertTrue(entries.get("operation-trace.txt").contains("press-export-disabled-1"));
        assertTrue(entries.get("manifest.txt").contains("operation-trace.txt: included"));
        assertEquals(originalLevel, preferences.getInt("log_level", -1));
        assertEquals(originalDestination, preferences.getInt("log_destination", -1));
    }

    @Test public void createWithSystemOnlyLoggingDoesNotTryToEnableOrRedirectIt() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                "drag_share_settings", Context.MODE_PRIVATE);
        preferences.edit().putInt("log_level", DragShareSettings.LOG_LEVEL_INFO)
                .putInt("log_destination", DragShareSettings.LOG_DESTINATION_SYSTEM).commit();

        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.create(
                context, validStateSummary());

        assertEquals("运行日志写入系统日志，应用无法读取原始日志。",
                result.missingLogReason);
        assertEquals(DragShareSettings.LOG_LEVEL_INFO, preferences.getInt("log_level", -1));
        assertEquals(DragShareSettings.LOG_DESTINATION_SYSTEM,
                preferences.getInt("log_destination", -1));
        Map<String, String> entries = readZip(result.file);
        assertFalse(entries.containsKey("runtime-log.txt"));
        assertTrue(entries.containsKey("root-connection.txt"));
        assertTrue(entries.containsKey("operation-trace.txt"));
    }

    private static String validStateSummary() {
        return "overall=NORMAL; root=READY; injection=READY; portal_service=READY; "
                + "portal_root=READY; root_input=READY; press_reader=READY; overlay=READY";
    }

    private static String validCompatibility() {
        return "Hyper手势兼容性报告\n生成时间: 2026-10-24 12:30:00 +0000\n"
                + "说明: Hyper手势兼容性报告不包含快捷方式名称、联系人和用户选择的应用清单。\n"
                + "=== Hook 状态 ===\n接口状态: ready\n最后连接时间: 0\n"
                + "-- SystemUI --\nprivate body=must-not-appear\n";
    }

    private static String safeRuntimeTrace() {
        return traceLine("press-44-2");
    }

    private static String traceLine(String operation) {
        return "2026-10-24 12:30:01.123 I pid=44 uid=0 thread=main "
                + "DragShare/Operation: proc=mod-44 op=" + operation + " stage=stash "
                + "outcome=completed asset=0123456789abcdef0123456789abcdef\n";
    }

    private static Map<String, String> readZip(File file) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int count;
                while ((count = zip.read(buffer)) >= 0) bytes.write(buffer, 0, count);
                entries.put(entry.getName(), bytes.toString(StandardCharsets.UTF_8.name()));
                zip.closeEntry();
            }
        }
        return entries;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
