package com.leaf.hyperdragshare.compat.xposed.callbacks;

public final class XC_LoadPackage {
    private XC_LoadPackage() {}

    public static final class LoadPackageParam {
        public final String packageName;
        public final String processName;
        public final ClassLoader classLoader;
        public final android.content.pm.ApplicationInfo appInfo;

        public LoadPackageParam(String packageName, String processName,
                                ClassLoader classLoader,
                                android.content.pm.ApplicationInfo appInfo) {
            this.packageName = packageName;
            this.processName = processName;
            this.classLoader = classLoader;
            this.appInfo = appInfo;
        }
    }
}
