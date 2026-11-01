package com.leaf.hyperdragshare.codex;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The single export-boundary redaction filter; rules are fixed by docs/DIAGNOSTICS.md. */
final class DiagnosticRedactionFilter {
    private static final Pattern URI_QUERY = Pattern.compile(
            "(?i)\\b(?:content|file|https?|shareimage)://[^\\s\\]\"']+");
    private static final Pattern CONTENT_FIELDS = Pattern.compile(
            "(?i)(\\b(?:payload|body|text|clipboard)[=:])[^\\n]*?(?=\\s+\\S+=|$)");
    private static final Pattern CREDENTIAL_FIELDS = Pattern.compile(
            "(?i)(\\b(?:token|capability|authorization|bearer|password|passwd|secret|credential)"
                    + "[=:])[^\\s,;\"']+");

    private DiagnosticRedactionFilter() { }

    static String line(String line, boolean fullDiagnostics) {
        if (line == null || line.isEmpty()) return line;
        String result = line;
        if (!fullDiagnostics) {
            result = redactUriQueries(result);
            result = CONTENT_FIELDS.matcher(result).replaceAll("$1<redacted-content>");
        }
        return CREDENTIAL_FIELDS.matcher(result).replaceAll("$1<redacted-secret>");
    }

    static String text(String value, boolean fullDiagnostics) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (String line : value.split("\\r?\\n", -1)) {
            if (result.length() > 0) result.append('\n');
            result.append(line(line, fullDiagnostics));
        }
        return result.toString();
    }

    private static String redactUriQueries(String line) {
        Matcher matcher = URI_QUERY.matcher(line);
        StringBuilder result = new StringBuilder(line.length());
        int copied = 0;
        while (matcher.find()) {
            String uri = matcher.group();
            int query = uri.indexOf('?');
            result.append(line, copied, matcher.start());
            result.append(query >= 0 ? uri.substring(0, query) + "?<redacted-query>" : uri);
            copied = matcher.end();
        }
        result.append(line, copied, line.length());
        return result.toString();
    }
}
