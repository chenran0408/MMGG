package com.leaf.hyperdragshare.gestures.xposed;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.media.session.MediaSessionManager;
import android.os.Handler;

import de.robv.android.xposed.XposedHelpers;

/** Mini-freeform media decoration state and controller lifecycle for the fan runtime. */
final class MiniMediaControls {
    private final Context context;
    private final Handler mainHandler;
    private final MediaSessionManager miniMediaSessions;
    private final MiniFreeformMediaHost miniMediaHost;

    private MiniFreeformMediaController miniMediaController;

    private String miniMediaPackage;

    private int miniMediaTaskId = -1;

    private Rect miniMediaBounds;

    MiniMediaControls(Context context, Handler mainHandler, MediaSessionManager miniMediaSessions,
                      MiniFreeformMediaHost miniMediaHost) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.miniMediaSessions = miniMediaSessions;
        this.miniMediaHost = miniMediaHost;
    }

    /** Feeds the media decoration from the same task callback that observes mini state. */
    void updateMiniMedia(Object info, GestureConfig config) {
        if (info == null) return;
        int taskId = miniMediaTaskId(info);
        if (taskId < 0) return;
        boolean mini = booleanCall(info, "isMiniState", false);
        boolean pinned = booleanCall(info, "isInPinMode", false)
                || booleanCall(info, "isMiniPinedState", false)
                || booleanCall(info, "isNormalPinedState", false);
        if (!mini || pinned || !isMiniMediaTaskVisible(info)) {
            if (taskId == miniMediaTaskId) clearMiniMedia();
            return;
        }
        String packageName = miniMediaPackage(info);
        Rect bounds = miniMediaBounds(info);
        if (packageName == null || packageName.isEmpty() || bounds == null || bounds.isEmpty()) {
            if (taskId == miniMediaTaskId) clearMiniMedia();
            return;
        }
        if (miniMediaController != null && !packageName.equals(miniMediaPackage))
            destroyMiniMediaController();
        miniMediaTaskId = taskId;
        miniMediaPackage = packageName;
        miniMediaBounds = new Rect(bounds);
        if (!config.miniMediaControlsEnabled) {
            destroyMiniMediaController();
            return;
        }
        startOrUpdateMiniMediaController(config);
    }

    void refreshMiniMediaConfig(GestureConfig config) {
        if (miniMediaPackage == null || miniMediaTaskId < 0 || miniMediaBounds == null) return;
        if (!config.miniMediaControlsEnabled) {
            destroyMiniMediaController();
            return;
        }
        startOrUpdateMiniMediaController(config);
    }

    void clearMiniMediaForTask(int taskId) {
        if (taskId == miniMediaTaskId) clearMiniMedia();
    }

    private void startOrUpdateMiniMediaController(GestureConfig config) {
        if (miniMediaController == null) {
            if (miniMediaSessions == null) return;
            try {
                miniMediaController = new MiniFreeformMediaController(context,
                        new MiniFreeformMediaController.ControllerSessionSource(
                                miniMediaSessions, mainHandler),
                        new MiniFreeformMediaController.XiaomiPlayerVolume(
                                context, miniMediaPackage), miniMediaHost);
            } catch (RuntimeException error) {
                Log.e("Cannot create mini media controller package=" + miniMediaPackage, error);
                return;
            }
        }
        miniMediaController.update(miniMediaPackage, miniMediaTaskId,
                new Rect(miniMediaBounds), config.miniMediaControlsEnabled,
                config.miniMediaAutoMute);
    }

    private void clearMiniMedia() {
        destroyMiniMediaController();
        miniMediaPackage = null;
        miniMediaTaskId = -1;
        miniMediaBounds = null;
    }

    private void destroyMiniMediaController() {
        if (miniMediaController != null) miniMediaController.destroy();
        miniMediaController = null;
    }

    static int miniMediaTaskId(Object info) {
        try {
            Object value = XposedHelpers.callMethod(info, "getTaskId");
            return value instanceof Number ? ((Number) value).intValue() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    static Rect miniMediaBounds(Object info) {
        try {
            Object value = XposedHelpers.callMethod(info, "getScaledBounds");
            return value instanceof Rect ? new Rect((Rect) value) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String miniMediaPackage(Object info) {
        try {
            Object task = XposedHelpers.callMethod(info, "getTaskInfo");
            if (task instanceof ActivityManager.RunningTaskInfo) {
                ActivityManager.RunningTaskInfo running = (ActivityManager.RunningTaskInfo) task;
                ComponentName component = running.topActivity;
                if (component == null) {
                    Object realActivity = XposedHelpers.getObjectField(running, "realActivity");
                    if (realActivity instanceof ComponentName)
                        component = (ComponentName) realActivity;
                }
                if (component == null) component = running.baseActivity;
                return component == null ? "" : component.getPackageName();
            }
            for (String field : new String[]{"topActivity", "realActivity", "baseActivity"}) {
                Object value = XposedHelpers.getObjectField(task, field);
                if (value instanceof ComponentName) return ((ComponentName) value).getPackageName();
            }
        } catch (Throwable ignored) {
            // Task info is vendor-private; an unavailable package cannot be controlled.
        }
        return "";
    }

    static boolean isMiniMediaTaskVisible(Object info) {
        try {
            Object task = XposedHelpers.callMethod(info, "getTaskInfo");
            return booleanCall(task, "isVisible", true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    static boolean booleanCall(Object target, String method, boolean fallback) {
        if (target == null) return fallback;
        try {
            Object value = XposedHelpers.callMethod(target, method);
            return value instanceof Boolean ? (Boolean) value : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
