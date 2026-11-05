package com.leaf.hyperdragshare.gestures.xposed;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import java.util.Collections;
import java.util.List;

final class FanOverlayView extends View {
    interface TouchListener {
        void onLaunch(int index);
        void onDismiss();
        void onSelectionChanged(int index);
    }

    private final Paint backdrop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics labelMetrics = new Paint.FontMetrics();
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint railPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path iconClipPath = new Path();
    private final Rect iconOldBounds = new Rect();
    private int statusBarTop = -1;
    private CharSequence fittedRingLabel;
    private String fittedRingLabelSource;
    private float fittedRingLabelMaxWidth;
    private float fanRadius;
    private int fanLayoutMode;
    private int fanOuterCapacity = 7;
    private int fanMiddleCapacity = 6;
    private int fanInnerCapacity = 5;
    private GestureGeometry.Point[] fanCenters = new GestureGeometry.Point[0];
    private float iconDiameter;
    private List<RuntimeTarget> targets = Collections.emptyList();
    private GestureGeometry.Corner corner = GestureGeometry.Corner.LEFT;
    private int selected = -1;
    private float pointerX;
    private float pointerY;
    private boolean showBackdrop = true;
    private boolean showNames;
    private boolean animationsEnabled;
    private int animationSpeed = 100;
    private int revealAmount = 42;
    private int rotationDegrees = 24;
    private int selectionScalePercent = 18;
    private boolean showSelectionRing = true;
    private boolean forceCircularIcons = true;
    private int selectionTransformLevel;
    private long revealStartedAt;
    private int animatedSelection = -1;
    private long selectionStartedAt;
    private int confirmationSelection = -1;
    private long confirmationStartedAt;
    private boolean dismissing;
    private long dismissalStartedAt;
    private TouchListener touchListener;

    FanOverlayView(Context context) {
        super(context);
        iconShadowPaint.setColor(0x01000000);
        iconShadowPaint.setShadowLayer(dp(9), 0, dp(3), 0x5c000000);
        iconStrokePaint.setStyle(Paint.Style.STROKE);
        iconStrokePaint.setStrokeWidth(dp(1));
        iconStrokePaint.setColor(0x88ffffff);
        selectedPaint.setStyle(Paint.Style.STROKE);
        selectedPaint.setStrokeWidth(dp(3));
        selectedPaint.setShadowLayer(dp(10), 0, dp(3), 0x52000000);
        textPaint.setTextSize(dp(14));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        railPaint.setShadowLayer(dp(14), 0, dp(4), 0x50000000);
        updateTheme();
    }

    private void updateTheme() {
        boolean dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int[] colors = com.leaf.hyperdragshare.codex.OverlayThemeColors.colors(dark);
        selectedPaint.setColor(colors[3]);
        selectedFillPaint.setColor(colors[4]);
        textPaint.setColor(colors[1]);
        pillPaint.setColor(colors[0]);
        railPaint.setColor(colors[0]);
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        updateTheme();
        invalidate();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        statusBarTop = insets == null ? -1
                : insets.getInsets(WindowInsets.Type.statusBars()).top;
        return super.onApplyWindowInsets(insets);
    }

    void setForceCircularIcons(boolean forceCircularIcons) {
        this.forceCircularIcons = forceCircularIcons;
        invalidate();
    }

    void setSelectionTransformLevel(int level) {
        selectionTransformLevel = level;
    }

    void setTouchListener(TouchListener listener) {
        touchListener = listener;
    }

    void configure(List<RuntimeTarget> targets, GestureGeometry.Corner corner,
                   float radius, float iconDiameter, boolean showSelectedName,
                   boolean showBackdrop,
                   boolean animationsEnabled, int animationSpeed,
                   int revealAmount, int rotationDegrees,
                   int selectionScalePercent, boolean showSelectionRing,
                   int layoutMode, int outerCapacity,
                   int middleCapacity, int innerCapacity) {
        this.targets = targets;
        this.corner = corner;
        this.fanRadius = radius;
        this.iconDiameter = iconDiameter;
        this.showBackdrop = showBackdrop;
        this.showNames = showSelectedName;
        this.animationsEnabled = animationsEnabled;
        this.animationSpeed = Math.max(75, Math.min(160, animationSpeed));
        this.revealAmount = Math.max(15, Math.min(130, revealAmount));
        this.rotationDegrees = Math.max(0, Math.min(90, rotationDegrees));
        this.selectionScalePercent = Math.max(5, Math.min(40,
                selectionScalePercent));
        this.showSelectionRing = showSelectionRing;
        this.fanLayoutMode = layoutMode;
        this.fanOuterCapacity = outerCapacity;
        this.fanMiddleCapacity = middleCapacity;
        this.fanInnerCapacity = innerCapacity;
        rebuildFanCenters();
        this.revealStartedAt = SystemClock.uptimeMillis();
        this.animatedSelection = -1;
        this.confirmationSelection = -1;
        this.dismissing = false;
        selected = -1;
        updateBackdropShader();
        invalidate();
    }

    void updateSelection(int selected, float x, float y) {
        if (this.selected != selected && selected >= 0) {
            animatedSelection = selected;
            selectionStartedAt = SystemClock.uptimeMillis();
        }
        this.selected = selected;
        pointerX = x;
        pointerY = y;
        invalidate();
    }

    void playConfirmation(int selected) {
        confirmationSelection = selected;
        confirmationStartedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    long confirmationDurationMs() {
        return animationsEnabled ? duration(150L) : 1L;
    }

    boolean playDismissal() {
        if (!animationsEnabled) return false;
        dismissing = true;
        dismissalStartedAt = SystemClock.uptimeMillis();
        invalidate();
        return true;
    }

    long dismissalDurationMs() {
        return animationsEnabled ? duration(150L) : 1L;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (targets.isEmpty()) return;
        float originX = corner == GestureGeometry.Corner.LEFT ? 0 : getWidth();
        float drawOriginY = getHeight();
        float radius = fanRadius > 0 ? fanRadius : Math.min(getWidth(), getHeight()) * 0.58f;
        if (showBackdrop) canvas.drawCircle(originX, drawOriginY,
                backdropRadius(radius), backdrop);

        long now = SystemClock.uptimeMillis();
        float revealProgress = animationsEnabled
                ? progress(revealStartedAt, duration(170L), now) : 1f;
        float reveal = animationsEnabled ? easeOutBack(revealProgress) : 1f;
        boolean keepAnimating = revealProgress < 1f;
        float dismissalProgress = dismissing
                ? easeOut(progress(dismissalStartedAt, dismissalDurationMs(), now)) : 0f;
        if (dismissing && dismissalProgress < 1f) keepAnimating = true;
        float startFactor = 1f - revealAmount / 100f;
        for (int i = 0; i < targets.size(); i++) {
            GestureGeometry.Point center = i < fanCenters.length
                    ? fanCenters[i]
                    : GestureGeometry.iconCenter(corner, i, targets.size(), getWidth(),
                    getHeight(), radius, fanLayoutMode, fanOuterCapacity,
                    fanMiddleCapacity, fanInnerCapacity);
            float iconOriginX = corner == GestureGeometry.Corner.LEFT ? 0f : getWidth();
            float iconOriginY = getHeight();
            float revealFactor = startFactor + (1f - startFactor) * reveal;
            revealFactor *= 1f - 0.32f * dismissalProgress;
            float x = iconOriginX + (center.x - iconOriginX) * revealFactor;
            float y = iconOriginY + (center.y - iconOriginY) * revealFactor;
            boolean active = i == selected;
            float pop = selectionPop(i, now);
            if (pop > 1f || revealRotationActive(i, now)) keepAnimating = true;
            float diameter = iconDiameter * (active ? selectionScale() : 1f)
                    * (0.78f + 0.22f * reveal) * pop
                    * (1f - 0.22f * dismissalProgress);
            float iconRadius = diameter / 2f;
            int save = canvas.save();
            canvas.rotate(revealRotation(i, now), x, y);
            if (active) {
                float pulse = Math.max(0f, pop - 1f) * 5f;
                int direction = corner == GestureGeometry.Corner.LEFT ? 1 : -1;
                canvas.rotate(SelectionTransform.rotation(selectionTransformLevel,
                        direction, pulse), x, y);
                canvas.scale(SelectionTransform.scaleX(selectionTransformLevel, pulse),
                        SelectionTransform.scaleY(selectionTransformLevel, pulse), x, y);
            }
            if (forceCircularIcons) canvas.drawCircle(x, y, iconRadius, iconShadowPaint);
            RuntimeTarget target = targets.get(i);
            drawCircularIcon(canvas, target.icon, x, y, diameter);
            if (target.isShortcut()) {
                ShortcutBadgeRenderer.draw(canvas, x, y, diameter, 1f,
                        getResources().getDisplayMetrics().density);
            }
            if (target.userId != 0) {
                ShortcutBadgeRenderer.drawDual(canvas, x, y, diameter, 1f,
                        getResources().getDisplayMetrics().density, target.isShortcut());
            }
            if (forceCircularIcons) {
                canvas.drawCircle(x, y, iconRadius - dp(0.5f), iconStrokePaint);
            }
            if (active && showSelectionRing) {
                canvas.drawCircle(x, y, iconRadius + dp(6), selectedPaint);
            }
            canvas.restoreToCount(save);
        }

        if (showNames && selected >= 0 && selected < targets.size()) {
            String label = targets.get(selected).label;
            float padding = dp(14);
            float width = textPaint.measureText(label) + padding * 2;
            float centerX = Math.max(width / 2 + dp(10),
                    Math.min(getWidth() - width / 2 - dp(10), pointerX));
            float centerY = Math.max(dp(42), pointerY - dp(58));
            canvas.drawRoundRect(centerX - width / 2, centerY - dp(22), centerX + width / 2,
                    centerY + dp(12), dp(17), dp(17), pillPaint);
            canvas.drawText(label, centerX, centerY, textPaint);
        }
        if (keepAnimating) postInvalidateOnAnimation();
    }

    private void drawCircularIcon(Canvas canvas, Drawable drawable, float centerX, float centerY,
                                  float diameter) {
        if (drawable == null) return;
        int intrinsicWidth = Math.max(1, drawable.getIntrinsicWidth());
        int intrinsicHeight = Math.max(1, drawable.getIntrinsicHeight());
        float target = diameter * (forceCircularIcons ? 1.16f : 0.94f);
        float scale = forceCircularIcons
                ? Math.max(target / intrinsicWidth, target / intrinsicHeight)
                : Math.min(target / intrinsicWidth, target / intrinsicHeight);
        int drawWidth = Math.round(intrinsicWidth * scale);
        int drawHeight = Math.round(intrinsicHeight * scale);
        drawable.copyBounds(iconOldBounds);
        int save = canvas.save();
        if (forceCircularIcons) {
            float radius = diameter / 2f;
            iconClipPath.reset();
            iconClipPath.addCircle(centerX, centerY, radius, Path.Direction.CW);
            canvas.clipPath(iconClipPath);
        }
        drawable.setBounds(Math.round(centerX - drawWidth / 2f),
                Math.round(centerY - drawHeight / 2f),
                Math.round(centerX + drawWidth / 2f),
                Math.round(centerY + drawHeight / 2f));
        drawable.draw(canvas);
        canvas.restoreToCount(save);
        drawable.setBounds(iconOldBounds);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float alphaFromDismissal(float dismissalProgress) {
        return Math.max(0f, Math.min(1f, 1f - dismissalProgress));
    }

    private float selectionPop(int index, long now) {
        if (!animationsEnabled) return 1f;
        if (index == confirmationSelection) {
            float p = progress(confirmationStartedAt, duration(150L), now);
            float amount = 0.18f;
            return 1f + amount * (float) Math.sin(Math.PI * p);
        }
        if (index != animatedSelection) return 1f;
        float p = progress(selectionStartedAt, duration(110L), now);
        float amount = 0.10f;
        return 1f + amount * (float) Math.sin(Math.PI * p);
    }

    private float selectionScale() {
        return 1f + selectionScalePercent / 100f;
    }

    private boolean selectionAnimationActive(int index, long now) {
        if (!animationsEnabled) return false;
        if (index == confirmationSelection) {
            return now < confirmationStartedAt + duration(150L);
        }
        return index == animatedSelection
                && now < selectionStartedAt + duration(110L);
    }

    private boolean revealRotationActive(int index, long now) {
        if (!animationsEnabled) return false;
        long start = revealStartedAt + index * revealStaggerMs();
        return now < start + duration(150L);
    }

    private float revealRotation(int index, long now) {
        if (!animationsEnabled) return 0f;
        long start = revealStartedAt + index * revealStaggerMs();
        float p = easeOutBack(progress(start, duration(150L), now));
        if (p >= 1f) return 0f;
        float direction = corner == GestureGeometry.Corner.LEFT ? 1f : -1f;
        return direction * rotationDegrees * (1f - p);
    }

    private long revealStaggerMs() {
        return Math.max(8L, Math.round(12L * 100f / animationSpeed));
    }

    private long duration(long base) {
        return Math.max(45L, Math.round(base * 100f / animationSpeed));
    }

    private static float progress(long startedAt, long duration, long now) {
        if (startedAt <= 0L) return 1f;
        return Math.max(0f, Math.min(1f, (now - startedAt) / (float) Math.max(1L, duration)));
    }

    private static float easeOut(float value) {
        float inverse = 1f - value;
        return 1f - inverse * inverse;
    }

    private static float easeOutBack(float value) {
        float shifted = value - 1f;
        return 1f + 2.70158f * shifted * shifted * shifted
                + 1.70158f * shifted * shifted;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        rebuildFanCenters();
        updateBackdropShader();
    }

    private void rebuildFanCenters() {
        if (targets.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            fanCenters = new GestureGeometry.Point[0];
            return;
        }
        float radius = fanRadius > 0
                ? fanRadius : Math.min(getWidth(), getHeight()) * 0.58f;
        GestureGeometry.Point[] next = new GestureGeometry.Point[targets.size()];
        for (int index = 0; index < next.length; index++) {
            next[index] = GestureGeometry.iconCenter(corner, index, targets.size(),
                    getWidth(), getHeight(), radius, fanLayoutMode,
                    fanOuterCapacity, fanMiddleCapacity, fanInnerCapacity);
        }
        fanCenters = next;
    }

    private void updateBackdropShader() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float radius = fanRadius > 0
                ? fanRadius : Math.min(getWidth(), getHeight()) * 0.58f;
        float originX = corner == GestureGeometry.Corner.LEFT ? 0 : getWidth();
        float drawOriginY = getHeight();
        backdrop.setShader(new RadialGradient(originX, drawOriginY, backdropRadius(radius),
                new int[]{0x99202742, 0x77202742, 0x00202742},
                new float[]{0f, 0.72f, 1f}, Shader.TileMode.CLAMP));
    }

    private float backdropRadius(float radius) {
        float originX = corner == GestureGeometry.Corner.LEFT ? 0f : getWidth();
        float originY = getHeight();
        float outermostCenter = 0f;
        for (int index = 0; index < targets.size(); index++) {
            GestureGeometry.Point center = index < fanCenters.length
                    ? fanCenters[index]
                    : GestureGeometry.iconCenter(corner, index, targets.size(), getWidth(),
                    getHeight(), radius, fanLayoutMode, fanOuterCapacity,
                    fanMiddleCapacity, fanInnerCapacity);
            outermostCenter = Math.max(outermostCenter,
                    (float) Math.hypot(center.x - originX, center.y - originY));
        }
        if (outermostCenter <= 0f) outermostCenter = radius;
        return outermostCenter + iconDiameter / 2f + dp(35);
    }
}
