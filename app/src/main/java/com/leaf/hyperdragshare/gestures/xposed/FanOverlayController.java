package com.leaf.hyperdragshare.gestures.xposed;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.util.List;

final class FanOverlayController {
    private static final int TYPE_NAVIGATION_BAR_PANEL = 2024;
    private final Context context;
    private final Handler mainHandler;
    private final WindowManager windowManager;
    private View view;
    private boolean attached;
    private final OpacityUpdates opacityUpdates = new OpacityUpdates();

    FanOverlayController(Context context, Handler mainHandler) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.windowManager = context.getSystemService(WindowManager.class);
    }

    void show(List<RuntimeTarget> targets, GestureGeometry.Corner corner,
              float radius, float iconDiameter, boolean showSelectedName,
              boolean showBackdrop,
              boolean animationsEnabled, int animationSpeed,
              int revealAmount, int rotationDegrees, int selectionScalePercent,
              boolean showSelectionRing, int layoutMode, int outerCapacity,
              int middleCapacity, int innerCapacity,
              boolean forceCircularIcons) {
        showInternal(targets, corner, radius, iconDiameter, showSelectedName,
                showBackdrop, animationsEnabled, animationSpeed, revealAmount,
                rotationDegrees, selectionScalePercent, showSelectionRing, layoutMode,
                outerCapacity, middleCapacity, innerCapacity, forceCircularIcons);
    }

    private void showInternal(List<RuntimeTarget> targets, GestureGeometry.Corner corner,
                              float radius, float iconDiameter, boolean showNames,
                              boolean showBackdrop, boolean animationsEnabled,
                              int animationSpeed, int revealAmount, int rotationDegrees,
                              int selectionScalePercent, boolean showSelectionRing,
                              int layoutMode, int outerCapacity, int middleCapacity,
                              int innerCapacity, boolean forceCircularIcons) {
        runOnMain(() -> {
            removeNow();
            FanOverlayView fanView = new FanOverlayView(context);
            fanView.configure(targets, corner, radius, iconDiameter, showNames, showBackdrop,
                    animationsEnabled, animationSpeed, revealAmount, rotationDegrees,
                    selectionScalePercent, showSelectionRing, layoutMode, outerCapacity,
                    middleCapacity, innerCapacity);
            fanView.setForceCircularIcons(forceCircularIcons);
            fanView.setSelectionTransformLevel(0);
            attachFanView(fanView, animationSpeed, animationsEnabled, false);
        });
    }

    private void attachFanView(FanOverlayView fanView, int animationSpeed,
                               boolean animationsEnabled, boolean touchable) {
            view = fanView;
            view.setAlpha(0f);
            WindowManager.LayoutParams params = params(TYPE_NAVIGATION_BAR_PANEL, touchable);
            try {
                windowManager.addView(view, params);
            } catch (Throwable first) {
                try {
                    windowManager.addView(view, params(2038, touchable));
                } catch (Throwable second) {
                    Log.e("Cannot attach fan overlay", second);
                    view = null;
                    attached = false;
                    return;
                }
            }
            attached = true;
            view.animate().alpha(1f).setDuration(animationDuration(110, animationSpeed,
                    animationsEnabled)).start();
    }

    void update(int selected, float x, float y) {
        if (mainHandler.getLooper().isCurrentThread()) updateOnMain(selected, x, y);
        else mainHandler.post(() -> updateOnMain(selected, x, y));
    }

    private void updateOnMain(int selected, float x, float y) {
        if (attached && view instanceof FanOverlayView) {
            ((FanOverlayView) view).updateSelection(selected, x, y);
        }
    }

    void setSelectionTransformLevel(int level) {
        runOnMain(() -> {
            if (attached && view instanceof FanOverlayView) {
                ((FanOverlayView) view).setSelectionTransformLevel(level);
            }
        });
    }

    void confirmAndHide(int selected) {
        runOnMain(() -> {
            if (!attached || !(view instanceof FanOverlayView)) return;
            FanOverlayView fanView = (FanOverlayView) view;
            fanView.playConfirmation(selected);
            View target = view;
            mainHandler.postDelayed(() -> {
                if (view == target) removeNow();
            }, fanView.confirmationDurationMs());
        });
    }

    void setOpacity(float opacity) {
        float safeOpacity = Math.max(0f, Math.min(1f, opacity));
        if (mainHandler.getLooper().isCurrentThread()) setOpacityOnMain(safeOpacity);
        else mainHandler.post(() -> setOpacityOnMain(safeOpacity));
    }

    private void setOpacityOnMain(float opacity) {
        if (!attached || view == null || !opacityUpdates.shouldApply(opacity)) return;
        view.animate().cancel();
        view.setAlpha(opacity);
    }

    void hide() {
        runOnMain(() -> {
            if (!attached || view == null) return;
            opacityUpdates.reset();
            View target = view;
            long duration = 90L;
            if (target instanceof FanOverlayView
                    && ((FanOverlayView) target).playDismissal()) {
                duration = ((FanOverlayView) target).dismissalDurationMs();
            }
            target.animate().alpha(0f).setDuration(duration).withEndAction(() -> {
                if (view == target) removeNow();
            }).start();
        });
    }

    void removeNow() {
        opacityUpdates.reset();
        if (attached && view != null) {
            try {
                view.animate().cancel();
                windowManager.removeViewImmediate(view);
            } catch (Throwable error) {
                Log.e("Cannot remove fan overlay", error);
            }
        }
        attached = false;
        view = null;
    }

    private WindowManager.LayoutParams params(int type, boolean touchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("FanFreeformOverlay");
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        return params;
    }

    private void runOnMain(Runnable runnable) {
        if (mainHandler.getLooper().isCurrentThread()) runnable.run();
        else mainHandler.post(runnable);
    }

    private static long animationDuration(long base, int speed, boolean enabled) {
        if (!enabled) return 1L;
        return Math.max(45L, Math.round(base * 100f / Math.max(1, speed)));
    }
}
