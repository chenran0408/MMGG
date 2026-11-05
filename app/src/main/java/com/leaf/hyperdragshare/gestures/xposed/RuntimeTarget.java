package com.leaf.hyperdragshare.gestures.xposed;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

final class RuntimeTarget {
    static final String MORE_APPS_PACKAGE = "com.leaf.hyperdragshare.more_apps";
    final ComponentName component;
    final String packageName;
    final String shortcutId;
    final String shortcutIntentUri;
    final String label;
    final Drawable icon;
    final int userId;
    final boolean moreApps;

    RuntimeTarget(ComponentName component, String label, Drawable icon) {
        this(component, label, icon, 0);
    }

    RuntimeTarget(ComponentName component, String label, Drawable icon, int userId) {
        this.component = component;
        this.packageName = component.getPackageName();
        this.shortcutId = "";
        this.shortcutIntentUri = "";
        this.label = label;
        this.icon = icon;
        this.userId = Math.max(0, userId);
        this.moreApps = false;
    }

    RuntimeTarget(String packageName, String shortcutId, String label, Drawable icon) {
        this(packageName, shortcutId, "", label, icon, 0);
    }

    RuntimeTarget(String packageName, String shortcutId, String shortcutIntentUri,
                  String label, Drawable icon, int userId) {
        this.component = null;
        this.packageName = packageName;
        this.shortcutId = shortcutId;
        this.shortcutIntentUri = shortcutIntentUri == null ? "" : shortcutIntentUri;
        this.label = label;
        this.icon = icon;
        this.userId = Math.max(0, userId);
        this.moreApps = false;
    }

    private RuntimeTarget(String label, Drawable icon) {
        this.component = null;
        this.packageName = MORE_APPS_PACKAGE;
        this.shortcutId = "";
        this.shortcutIntentUri = "";
        this.label = label;
        this.icon = icon;
        this.userId = 0;
        this.moreApps = true;
    }

    static RuntimeTarget moreApps(String label, Drawable icon) {
        return new RuntimeTarget(label, icon);
    }

    String positionIdentity() {
        return com.leaf.hyperdragshare.gestures.config.HoneycombPositions.identity(
                component == null ? "" : component.flattenToString(), packageName,
                shortcutId, shortcutIntentUri, userId);
    }

    boolean isShortcut() { return !shortcutId.isEmpty(); }
    boolean isLauncherShortcut() { return isShortcut() && !shortcutIntentUri.isEmpty(); }
    boolean isMoreApps() { return moreApps; }
}
