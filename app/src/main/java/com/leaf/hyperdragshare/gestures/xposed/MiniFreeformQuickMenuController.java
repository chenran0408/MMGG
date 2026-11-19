package com.leaf.hyperdragshare.gestures.xposed;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.StateListDrawable;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.SurfaceControl;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.leaf.hyperdragshare.codex.BuildConfig;
import com.leaf.hyperdragshare.codex.R;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Flyme-style mini menu interaction and assets adapted from MeiWindow v1.4.7 (GPL-3.0). */
final class MiniFreeformQuickMenuController {
    interface Mount {
        Runnable attach(Target target, View root) throws ReflectiveOperationException;
    }

    record Target(int taskId, Rect local, Rect screen, SurfaceControl leash,
                  Display display, Configuration config) {}

    private final Context context;
    private final IntConsumer scaleAction;
    private final Mount mount;
    private final Resources icons;
    private volatile Session session;
    private volatile int taskId = -1;
    private volatile Rect shownBounds;

    MiniFreeformQuickMenuController(Context context, IntConsumer scaleAction) {
        this(context, scaleAction, (target, root) -> MiniMenuSurface.mount(context, target.display(),
                target.config(), target.leash(), root, target.local().width(), target.local().height()),
                moduleResources(context));
    }

    MiniFreeformQuickMenuController(Context context, IntConsumer scaleAction, Mount mount, Resources icons) {
        this.context = context;
        this.scaleAction = scaleAction;
        this.mount = mount;
        this.icons = icons;
    }

    boolean isShowingFor(int taskId) { return this.taskId == taskId && taskId >= 0; }

    boolean contains(float x, float y) {
        Rect frame = shownBounds;
        return frame != null && frame.contains((int) x, (int) y);
    }

    boolean show(Target target, BooleanSupplier mutedState, BooleanSupplier toggleMute, boolean enlarged) {
        Rect local = target.local();
        Rect screen = target.screen();
        if (local.isEmpty() || screen.isEmpty()) return false;
        hide();

        FrameLayout root = new FrameLayout(context);
        FrameLayout mask = new FrameLayout(context);
        mask.setBackgroundColor(0x33000000);
        root.addView(mask, new FrameLayout.LayoutParams(-1, -1));
        root.setOnClickListener(v -> dismissMask(mask));
        mask.setOnClickListener(v -> dismissMask(mask));

        boolean portrait = local.width() < local.height();
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        mask.addView(buttons, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        float scale = (float) screen.width() / local.width();
        int size = Math.max(1, Math.max(dp(48), Math.min(Math.round(dp(56) / scale),
                Math.min(Math.round(local.width() * .55f), Math.round(local.height() * .38f)))));
        int gap = Math.min(Math.round(dp(12) / scale),
                Math.round((portrait ? local.height() : local.width()) * .08f));

        String prefix = enlarged ? "large_" : "small_";
        int id = target.taskId();
        ImageView audio = action("", null);
        boolean muted = mutedState.getAsBoolean();
        updateMuteIcon(audio, prefix, muted);
        audio.setOnClickListener(v -> {
            if (!isCurrent(mask)) return;
            if (toggleMute.getAsBoolean()) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                updateMuteIcon(audio, prefix, mutedState.getAsBoolean());
            }
        });
        buttons.addView(audio, new LinearLayout.LayoutParams(size, size));
        ImageView resize = action(enlarged ? "恢复原尺寸" : "放大",
                enlarged ? "large_scale_min" : "small_scale_max");
        resize.setOnClickListener(v -> {
            if (!isCurrent(mask)) return;
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            hide();
            scaleAction.accept(id);
        });
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(size, size);
        if (portrait) second.topMargin = gap;
        else second.setMarginStart(gap);
        buttons.addView(resize, second);
        int padding = Math.round(size * .1f);
        audio.setPadding(padding, padding, padding, padding);
        resize.setPadding(padding, padding, padding, padding);

        boolean animate = ValueAnimator.areAnimatorsEnabled();
        if (animate) {
            mask.setAlpha(0f);
            mask.setScaleX(.85f);
            mask.setScaleY(.85f);
        }
        try {
            Runnable release = mount.attach(target, root);
            Runnable ticker = new Runnable() {
                private boolean displayed = muted;
                @Override public void run() {
                    if (session == null || session.mask() != mask) return;
                    boolean current = mutedState.getAsBoolean();
                    if (current != displayed) {
                        displayed = current;
                        updateMuteIcon(audio, prefix, current);
                    }
                    mask.postDelayed(this, 400L);
                }
            };
            session = new Session(mask, release, ticker);
            mask.postDelayed(ticker, 400L);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            Log.e("Cannot mount menu on mini task=" + id, error);
            return false;
        }
        taskId = id;
        shownBounds = new Rect(screen);
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (animate) mask.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180)
                .setInterpolator(new DecelerateInterpolator()).start();
        return true;
    }

    /** The surface moves with the task. Only the screen hit test needs updated bounds. */
    void update(int taskId, Rect bounds) {
        if (isShowingFor(taskId)) shownBounds = new Rect(bounds);
    }

    void hideForTask(int taskId) {
        if (isShowingFor(taskId)) hide();
    }

    Runnable outsideDismissal(float x, float y) {
        Session current = session;
        if (current == null || contains(x, y)) return null;
        return () -> {
            if (session == current) hide();
        };
    }

    void hide() {
        Session current = session;
        if (current == null) return;
        session = null;
        taskId = -1;
        shownBounds = null;
        current.mask().removeCallbacks(current.muteTicker());
        current.mask().animate().cancel();
        current.release().run();
    }

    private void dismissMask(View mask) {
        if (isCurrent(mask)) hide();
    }

    private boolean isCurrent(View mask) {
        return session != null && session.mask() == mask;
    }

    private ImageView action(String label, String asset) {
        ImageView button = new ImageView(context);
        button.setContentDescription(label);
        button.setFocusable(true);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (asset != null) button.setImageDrawable(states(asset));
        return button;
    }

    private void updateMuteIcon(ImageView button, String prefix, boolean muted) {
        button.setContentDescription(muted ? "取消静音" : "静音");
        button.setImageDrawable(states(prefix + (muted ? "unmute" : "mute")));
    }

    private StateListDrawable states(String asset) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, icon(asset, "press"));
        states.addState(new int[]{}, icon(asset, "normal"));
        return states;
    }

    private android.graphics.drawable.Drawable icon(String name, String state) {
        boolean pressed = "press".equals(state);
        int id = switch (name) {
            case "small_mute" -> pressed ? R.drawable.mei_mini_small_mute_press : R.drawable.mei_mini_small_mute_normal;
            case "small_unmute" -> pressed ? R.drawable.mei_mini_small_unmute_press : R.drawable.mei_mini_small_unmute_normal;
            case "large_mute" -> pressed ? R.drawable.mei_mini_large_mute_press : R.drawable.mei_mini_large_mute_normal;
            case "large_unmute" -> pressed ? R.drawable.mei_mini_large_unmute_press : R.drawable.mei_mini_large_unmute_normal;
            case "small_scale_max" -> pressed ? R.drawable.mei_mini_small_scale_max_press : R.drawable.mei_mini_small_scale_max_normal;
            case "large_scale_min" -> pressed ? R.drawable.mei_mini_large_scale_min_press : R.drawable.mei_mini_large_scale_min_normal;
            default -> throw new IllegalArgumentException(name);
        };
        return icons.getDrawable(id, null);
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static Resources moduleResources(Context context) {
        try {
            return context.createPackageContext(BuildConfig.APPLICATION_ID, 0).getResources();
        } catch (PackageManager.NameNotFoundException error) {
            Log.e("Cannot load mini menu assets", error);
            return context.getResources();
        }
    }

    private record Session(FrameLayout mask, Runnable release, Runnable muteTicker) {}
}
