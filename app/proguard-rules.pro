# libxposed API 102 entry point is loaded by class name from META-INF/xposed/java_init.list.
-keep class com.leaf.hyperdragshare.codex.DragShareModule { *; }
-keep class com.leaf.hyperdragshare.compat.xposed.** { *; }
-keep class com.leaf.hyperdragshare.codex.MainHook { *; }
-keep class com.leaf.hyperdragshare.codex.RootNodeCaptureMain {
    public static void main(java.lang.String[]);
}
-keep class com.leaf.hyperdragshare.codex.RootClipboardMain {
    public static void main(java.lang.String[]);
}
-keep class com.leaf.hyperdragshare.codex.RootProcessKeepAliveMain {
    public static void main(java.lang.String[]);
}

# HyperGesture runtime hooks and proxies use reflection across the host boundary.
-keep class com.leaf.hyperdragshare.gestures.xposed.** { *; }
-keep class com.leaf.hyperdragshare.gestures.config.ConfigProvider { *; }
