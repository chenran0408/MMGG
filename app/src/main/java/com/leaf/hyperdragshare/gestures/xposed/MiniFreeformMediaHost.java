package com.leaf.hyperdragshare.gestures.xposed;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/** Owns the small SystemUI overlay used when a mini task has media controls. */
final class MiniFreeformMediaHost implements MiniFreeformMediaController.Host {
    private static final int TYPE_APPLICATION_OVERLAY = 2038;
    private static final int MIN_GAP = 1;

    interface WindowAccess {
        Rect displayBounds();
        void addView(View view, WindowManager.LayoutParams params);
        void updateViewLayout(View view, WindowManager.LayoutParams params);
        void removeViewImmediate(View view);
    }

    private final Context context;
    private final WindowAccess window;
    private final Handler main = new Handler(Looper.getMainLooper());
    private View view;
    private View requestedControls;
    private int taskId = -1;
    private Rect miniBounds;

    MiniFreeformMediaHost(Context context, WindowManager windowManager) {
        this(context, new RealWindowAccess(windowManager));
    }

    MiniFreeformMediaHost(Context context, WindowAccess window) {
        this.context = context;
        this.window = window;
    }

    @Override public void show(int taskId, Rect miniBounds, View controls) {
        runOnMain(() -> showNow(taskId, miniBounds, controls));
    }

    @Override public void update(int taskId, Rect miniBounds) {
        runOnMain(() -> updateNow(taskId, miniBounds));
    }

    @Override public void hide() {
        runOnMain(this::hideNow);
    }

    private void showNow(int nextTaskId, Rect nextBounds, View controls) {
        if (controls == null || nextTaskId < 0 || nextBounds == null || nextBounds.isEmpty()) {
            hideNow();
            return;
        }
        if (view != null) hideNow();
        Rect display = window.displayBounds();
        if (display == null || display.isEmpty()) return;
        measureControls(controls, display);
        Placement placement = placement(display, nextBounds,
                controls.getMeasuredWidth(), controls.getMeasuredHeight());
        if (placement == null) return;
        WindowManager.LayoutParams params = params(placement, nextTaskId);
        view = controls;
        requestedControls = controls;
        taskId = nextTaskId;
        miniBounds = new Rect(nextBounds);
        try {
            window.addView(controls, params);
        } catch (RuntimeException error) {
            Log.e("Cannot attach mini media host", error);
            hideNow();
        }
    }

    private void updateNow(int nextTaskId, Rect nextBounds) {
        if (nextTaskId != taskId || nextBounds == null || nextBounds.isEmpty()) {
            hideNow();
            return;
        }
        if (view == null) {
            if (requestedControls != null) showNow(taskId, nextBounds, requestedControls);
            return;
        }
        Rect display = window.displayBounds();
        if (display == null || display.isEmpty()) {
            hideNow();
            return;
        }
        measureControls(view, display);
        Placement placement = placement(display, nextBounds,
                view.getMeasuredWidth(), view.getMeasuredHeight());
        if (placement == null) {
            hideNow();
            return;
        }
        try {
            window.updateViewLayout(view, params(placement, taskId));
            miniBounds = new Rect(nextBounds);
        } catch (RuntimeException error) {
            Log.e("Cannot move mini media host", error);
            hideNow();
        }
    }

    private void hideNow() {
        View current = view;
        view = null;
        requestedControls = null;
        taskId = -1;
        miniBounds = null;
        if (current == null) return;
        try {
            window.removeViewImmediate(current);
        } catch (RuntimeException error) {
            // The state is cleared even when WindowManager already removed the view.
            Log.e("Cannot remove mini media host", error);
        }
    }

    private void measureControls(View controls, Rect display) {
        int width = Math.max(1, display.width());
        int height = Math.max(1, display.height());
        controls.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
    }

    @SuppressLint("RtlHardcoded")
    private static WindowManager.LayoutParams params(Placement placement, int taskId) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                placement.width, placement.height, TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = placement.left;
        params.y = placement.top;
        params.setTitle("HyperGestureMiniMedia-" + taskId);
        params.setFitInsetsTypes(0);
        params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        return params;
    }

    static Placement placement(Rect display, Rect mini, int width, int height) {
        if (display == null || mini == null || display.isEmpty() || mini.isEmpty()
                || width <= 0 || height <= 0 || width > display.width()
                || height > display.height()) return null;
        Rect clippedMini = new Rect(mini);
        if (!clippedMini.intersect(display) || clippedMini.isEmpty()) return null;
        int left = clamp(clippedMini.centerX() - width / 2,
                display.left, display.right - width);
        if (height <= display.bottom - clippedMini.bottom) {
            return new Placement(left, clippedMini.bottom + MIN_GAP, width, height);
        }
        if (height <= clippedMini.top - display.top) {
            return new Placement(left, clippedMini.top - MIN_GAP - height, width, height);
        }
        int top = clamp(clippedMini.centerY() - height / 2,
                display.top, display.bottom - height);
        if (width <= display.right - clippedMini.right) {
            return new Placement(clippedMini.right + MIN_GAP, top, width, height);
        }
        if (width <= clippedMini.left - display.left) {
            return new Placement(clippedMini.left - MIN_GAP - width, top, width, height);
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == main.getLooper()) runnable.run();
        else main.post(runnable);
    }

    static final class Placement {
        final int left;
        final int top;
        final int width;
        final int height;

        Placement(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        Rect bounds() { return new Rect(left, top, left + width, top + height); }
    }

    private static final class RealWindowAccess implements WindowAccess {
        private final WindowManager window;

        RealWindowAccess(WindowManager window) { this.window = window; }

        @Override public Rect displayBounds() {
            return window == null ? null : window.getCurrentWindowMetrics().getBounds();
        }

        @Override public void addView(View view, WindowManager.LayoutParams params) {
            window.addView(view, params);
        }

        @Override public void updateViewLayout(View view, WindowManager.LayoutParams params) {
            window.updateViewLayout(view, params);
        }

        @Override public void removeViewImmediate(View view) {
            window.removeViewImmediate(view);
        }
    }
}
