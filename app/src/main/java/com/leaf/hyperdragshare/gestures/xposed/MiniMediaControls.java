package com.leaf.hyperdragshare.gestures.xposed;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.view.Display;
import android.view.SurfaceControl;
import android.widget.Toast;

import com.leaf.hyperdragshare.compat.xposed.XposedHelpers;

/** Mini-freeform media decoration state and controller lifecycle for the fan runtime. */
final class MiniMediaControls {
    private final Context context;
    private final Handler mainHandler;
    private final MediaSessionManager miniMediaSessions;
    private final MiniFreeformMediaHost miniMediaHost;

    private MiniFreeformQuickMenuController quickMenu;
    private final java.util.Map<Integer, MiniFreeformMediaController.PendingRestore> manualMutes = new java.util.HashMap<>();
    private final java.util.Set<Integer> manuallyUnmutedTasks = new java.util.HashSet<>();

    private MiniFreeformMediaController miniMediaController;
    private final java.util.List<MiniFreeformMediaController.PendingRestore> pendingRestores =
            new java.util.ArrayList<>();
    private AudioManager restoreAudioManager;
    private final AudioManager.AudioPlaybackCallback restoreOnPlayback =
            new AudioManager.AudioPlaybackCallback() {
                @Override public void onPlaybackConfigChanged(java.util.List<AudioPlaybackConfiguration> states) {
                    restorePendingMutes();
                }
            };

    private String miniMediaPackage;

    private int miniMediaTaskId = -1;
    private int miniMediaUserId = -1;

    private Rect miniMediaBounds;

    MiniMediaControls(Context context, Handler mainHandler, MediaSessionManager miniMediaSessions,
                      MiniFreeformMediaHost miniMediaHost) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.miniMediaSessions = miniMediaSessions;
        this.miniMediaHost = miniMediaHost;
    }

    void setQuickMenu(MiniFreeformQuickMenuController quickMenu) {
        this.quickMenu = quickMenu;
    }

    /** Feeds the media decoration from the same task callback that observes mini state. */
    void updateMiniMedia(Object info, GestureConfig config) {
        if (info == null) return;
        int taskId = miniMediaTaskId(info);
        if (taskId < 0) return;
        if (!isTouchableMini(info)) {
            clearMiniMediaForTask(taskId);
            return;
        }
        String packageName = miniMediaPackage(info);
        Rect bounds = miniMediaBounds(info);
        if (packageName == null || packageName.isEmpty() || bounds == null || bounds.isEmpty()) {
            clearMiniMediaForTask(taskId);
            return;
        }
        int userId = MiniMediaUser.taskUserId(info);
        if (miniMediaController != null
                && (taskId != miniMediaTaskId || userId != miniMediaUserId || !packageName.equals(miniMediaPackage)))
            destroyMiniMediaController();
        miniMediaTaskId = taskId;
        miniMediaUserId = userId;
        miniMediaPackage = packageName;
        miniMediaBounds = new Rect(bounds);
        if (quickMenu != null) quickMenu.update(taskId, bounds);
        if (!config.miniMediaControlsEnabled) {
            destroyMiniMediaController();
            return;
        }
        startOrUpdateMiniMediaController(config);
    }

    static boolean isTouchableMini(Object info) {
        boolean mini = booleanCall(info, "isMiniState", false);
        boolean pinned = booleanCall(info, "isInPinMode", false)
                || booleanCall(info, "isMiniPinedState", false)
                || booleanCall(info, "isNormalPinedState", false);
        return mini && !pinned && isMiniMediaTaskVisible(info);
    }

    void refreshMiniMediaConfig(GestureConfig config) {
        if (miniMediaPackage == null || miniMediaTaskId < 0 || miniMediaBounds == null) return;
        if (!config.miniMediaControlsEnabled) {
            destroyMiniMediaController();
            return;
        }
        startOrUpdateMiniMediaController(config);
    }

    boolean showQuickMenu(Object info, GestureConfig config, boolean enlarged) {
        if (quickMenu == null || !isTouchableMini(info)) return false;
        int id = miniMediaTaskId(info);
        if (id < 0) return false;
        updateMiniMedia(info, config);
        if (miniMediaTaskId != id) return false;
        MiniFreeformQuickMenuController.Target target;
        try {
            Object leash = XposedHelpers.callMethod(info, "getLeash");
            Object bounds = XposedHelpers.callMethod(info, "getBounds");
            if (!(leash instanceof SurfaceControl surface) || !surface.isValid()
                    || !(bounds instanceof Rect local) || local.isEmpty()) return false;
            Object running = XposedHelpers.callMethod(info, "getTaskInfo");
            ActivityManager.RunningTaskInfo task = running instanceof ActivityManager.RunningTaskInfo
                    ? (ActivityManager.RunningTaskInfo) running : null;
            DisplayManager displays = context.getSystemService(DisplayManager.class);
            int displayId = task == null ? Display.DEFAULT_DISPLAY
                    : XposedHelpers.getIntField(task, "displayId");
            Display display = displays.getDisplay(displayId);
            if (display == null) return false;
            Configuration configuration = task == null ? context.getResources().getConfiguration()
                    : (Configuration) XposedHelpers.getObjectField(task, "configuration");
            target = new MiniFreeformQuickMenuController.Target(id, new Rect(local),
                    new Rect(miniMediaBounds), surface, display, new Configuration(configuration));
        } catch (RuntimeException | LinkageError | XposedHelpers.InvocationTargetError error) {
            Log.e("Cannot resolve mini menu task=" + id, error);
            return false;
        }
        String pkg = miniMediaPackage;
        var volume = new MiniFreeformMediaController.XiaomiPlayerVolume(context, pkg, miniMediaUserId);
        return quickMenu.show(target, () -> {
            float level = volume.read(pkg);
            return Float.isFinite(level) ? level == 0f
                    : manualMutes.containsKey(id)
                    || (miniMediaTaskId == id && miniMediaController != null && miniMediaController.isMuted());
        }, volume.available() ? () -> {
            if (toggleMute(id, pkg, volume)) return true;
            Toast.makeText(context, "无法切换小窗静音", Toast.LENGTH_SHORT).show();
            return false;
        } : null, enlarged);
    }

    boolean isQuickMenuShowingFor(int taskId) {
        return quickMenu != null && quickMenu.isShowingFor(taskId);
    }

    boolean quickMenuContains(float x, float y) {
        return quickMenu != null && quickMenu.contains(x, y);
    }

    void dismissMenuOutside(float x, float y) {
        Runnable dismiss = quickMenu == null ? null : quickMenu.outsideDismissal(x, y);
        if (dismiss != null) mainHandler.post(dismiss);
    }

    void hideQuickMenu() {
        if (quickMenu != null) quickMenu.hide();
    }

    boolean toggleMute(int taskId, String packageName, MiniFreeformMediaController.Volume volume) {
        var current = taskId == miniMediaTaskId && packageName.equals(miniMediaPackage)
                ? miniMediaController : null;
        var muted = manualMutes.get(taskId);
        if (muted != null) {
            if (!muted.restore()) return false;
            manualMutes.remove(taskId);
        } else if (current == null || !current.isMuted()) {
            var lease = new MiniFreeformMediaController.MuteLease();
            lease.acquire(packageName, volume);
            if (!lease.active()) return false;
            manualMutes.put(taskId, new MiniFreeformMediaController.PendingRestore(lease, volume));
            return true;
        }
        if (current != null && !current.unmuteForCurrentTask()) return false;
        manuallyUnmutedTasks.add(taskId);
        return true;
    }

    void clearMiniMediaForTask(int taskId) {
        manuallyUnmutedTasks.remove(taskId);
        var muted = manualMutes.remove(taskId);
        if (muted != null && !muted.restore()) deferMuteRestore(muted);
        if (quickMenu != null) quickMenu.hideForTask(taskId);
        if (taskId == miniMediaTaskId) clearMiniMedia();
    }

    private void deferMuteRestore(MiniFreeformMediaController.PendingRestore restore) {
        pendingRestores.add(restore);
        if (restoreAudioManager != null) return;
        AudioManager audio = context.getSystemService(AudioManager.class);
        try {
            audio.registerAudioPlaybackCallback(restoreOnPlayback, mainHandler);
            restoreAudioManager = audio;
        } catch (RuntimeException error) {
            Log.e("Cannot watch playback to restore mini window volume", error);
        }
        restorePendingMutes();
    }

    private void restorePendingMutes() {
        pendingRestores.removeIf(MiniFreeformMediaController.PendingRestore::restore);
        if (pendingRestores.isEmpty() && restoreAudioManager != null) {
            restoreAudioManager.unregisterAudioPlaybackCallback(restoreOnPlayback);
            restoreAudioManager = null;
        }
    }

    private void startOrUpdateMiniMediaController(GestureConfig config) {
        if (miniMediaController == null) {
            if (miniMediaSessions == null || miniMediaUserId < 0) return;
            try {
                miniMediaController = new MiniFreeformMediaController(context,
                        new MiniFreeformMediaController.ControllerSessionSource(
                                miniMediaSessions, mainHandler,
                                android.os.UserHandle.getUserHandleForUid(miniMediaUserId * 100000)),
                        new MiniFreeformMediaController.XiaomiPlayerVolume(
                                context, miniMediaPackage, miniMediaUserId), miniMediaHost);
            } catch (RuntimeException error) {
                Log.e("Cannot create mini media controller package=" + miniMediaPackage, error);
                return;
            }
        }
        miniMediaController.update(miniMediaPackage, miniMediaTaskId,
                new Rect(miniMediaBounds), config.miniMediaControlsEnabled,
                config.miniMediaAutoMute && !manuallyUnmutedTasks.contains(miniMediaTaskId));
    }

    private void clearMiniMedia() {
        destroyMiniMediaController();
        miniMediaPackage = null;
        miniMediaTaskId = -1;
        miniMediaUserId = -1;
        miniMediaBounds = null;
    }

    private void destroyMiniMediaController() {
        if (miniMediaController == null) return;
        var restore = miniMediaController.destroy();
        miniMediaController = null;
        if (restore != null) deferMuteRestore(restore);
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
