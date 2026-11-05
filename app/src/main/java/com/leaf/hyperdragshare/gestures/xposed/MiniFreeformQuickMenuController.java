package com.leaf.hyperdragshare.gestures.xposed;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small action strip that follows the native mini-window bounds. */
final class MiniFreeformQuickMenuController {
    interface Actions {
        void onMute();
        void onFullscreen();
        void onClose();
    }

    private final Context context;
    private final WindowManager windowManager;
    private final Actions actions;
    private View view;
    private int taskId = -1;
    private Rect miniBounds;

    MiniFreeformQuickMenuController(Context context, WindowManager windowManager, Actions actions) {
        this.context = context;
        this.windowManager = windowManager;
        this.actions = actions;
    }

    void update(int taskId, Rect bounds) {
        if (bounds == null || bounds.isEmpty() || taskId < 0) {
            hide();
            return;
        }
        this.taskId = taskId;
        this.miniBounds = new Rect(bounds);
        if (view == null) showNow();
        else updateNow();
    }

    void hideForTask(int taskId) {
        if (taskId == this.taskId) hide();
    }

    void hide() {
        View current = view;
        view = null;
        taskId = -1;
        miniBounds = null;
        if (current != null && windowManager != null) {
            try { windowManager.removeViewImmediate(current); }
            catch (Throwable error) { Log.e("Cannot remove mini quick menu", error); }
        }
    }

    private void showNow() {
        if (windowManager == null || miniBounds == null) return;
        LinearLayout menu = new LinearLayout(context);
        menu.setOrientation(LinearLayout.HORIZONTAL);
        menu.setGravity(Gravity.CENTER);
        menu.setPadding(dp(6), dp(4), dp(6), dp(4));
        menu.setBackground(roundRect(0xee202124, dp(18)));
        menu.setOnClickListener(v -> hide());
        TextView mute = action("静音", actions::onMute);
        TextView full = action("放大", actions::onFullscreen);
        TextView close = action("关闭", actions::onClose);
        menu.addView(mute, new LinearLayout.LayoutParams(dp(58), dp(38)));
        menu.addView(full, new LinearLayout.LayoutParams(dp(58), dp(38)));
        menu.addView(close, new LinearLayout.LayoutParams(dp(58), dp(38)));
        menu.measure(View.MeasureSpec.makeMeasureSpec(screenWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(screenHeight(), View.MeasureSpec.AT_MOST));
        Placement placement = placement(menu.getMeasuredWidth(), menu.getMeasuredHeight());
        if (placement == null) return;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                placement.width, placement.height, 2038,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = placement.left;
        params.y = placement.top;
        params.setFitInsetsTypes(0);
        params.setTitle("HyperGestureMiniQuickMenu-" + taskId);
        try {
            windowManager.addView(menu, params);
            view = menu;
        } catch (Throwable error) { Log.e("Cannot attach mini quick menu", error); }
    }

    private void updateNow() {
        if (view == null || miniBounds == null) return;
        Placement p = placement(view.getWidth(), view.getHeight());
        if (p == null) { hide(); return; }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                p.width, p.height, 2038,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = p.left;
        params.y = p.top;
        params.setFitInsetsTypes(0);
        params.setTitle("HyperGestureMiniQuickMenu-" + taskId);
        try { windowManager.updateViewLayout(view, params); }
        catch (Throwable error) { Log.e("Cannot move mini quick menu", error); }
    }

    private Placement placement(int width, int height) {
        Rect display = windowManager == null ? null : windowManager.getCurrentWindowMetrics().getBounds();
        if (display == null || miniBounds == null || width <= 0 || height <= 0) return null;
        int left = Math.max(display.left, Math.min(miniBounds.right - width, display.right - width));
        int top = miniBounds.top - height - dp(6);
        if (top < display.top) top = Math.min(display.bottom - height, miniBounds.bottom + dp(6));
        if (top < display.top || top + height > display.bottom) return null;
        return new Placement(left, top, width, height);
    }

    private TextView action(String text, Runnable run) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(13f);
        view.setGravity(Gravity.CENTER);
        view.setOnClickListener(v -> { run.run(); hide(); });
        return view;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable out = new GradientDrawable();
        out.setColor(color);
        out.setCornerRadius(radius);
        return out;
    }
    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private int screenWidth() { return context.getResources().getDisplayMetrics().widthPixels; }
    private int screenHeight() { return context.getResources().getDisplayMetrics().heightPixels; }

    private static final class Placement {
        final int left, top, width, height;
        Placement(int left, int top, int width, int height) { this.left = left; this.top = top; this.width = width; this.height = height; }
    }
}
