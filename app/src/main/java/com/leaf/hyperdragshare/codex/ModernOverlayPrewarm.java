package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

/**
 * Owns the warm-up overlay window used to pre-initialize the modern preview
 * path. The window is disposed once it finishes its first draw, on controller
 * teardown, or when the warm-up itself fails. Process-wide once semantics are
 * preserved with a static flag.
 */
final class ModernOverlayPrewarm {
    interface Host {
        boolean isDestroyed();

        WindowManager.LayoutParams warmupParams();

        void log(String message);

        void log(String message, Throwable error);
    }

    private static boolean started;

    private final Context context;
    private final WindowManager windowManager;
    private final Handler mainHandler;
    private final Host host;

    private Runnable task;
    private ModernPreviewOverlayView view;
    private ModernOverlayWindow window;
    private ViewTreeObserver.OnDrawListener drawListener;

    ModernOverlayPrewarm(Context context, WindowManager windowManager, Handler mainHandler,
                         Host host) {
        this.context = context;
        this.windowManager = windowManager;
        this.mainHandler = mainHandler;
        this.host = host;
    }

    void start() {
        if (host.isDestroyed() || started) {
            return;
        }
        started = true;
        Runnable prewarm = () -> {
            task = null;
            if (host.isDestroyed()) return;
            try {
                long startedAt = SystemClock.elapsedRealtime();
                CapturedContent content = CapturedContent.text("预热", "warmup", null);
                ModernPreviewOverlayView warmupView = new ModernPreviewOverlayView(
                        context, content, DragShareSettings.defaults());
                view = warmupView;
                WindowManager.LayoutParams warmupParams = host.warmupParams();
                warmupParams.x = -2;
                warmupParams.y = -2;
                ModernOverlayWindow warmupWindow = new ModernOverlayWindow(
                        context, windowManager, warmupView, warmupParams, 0);
                window = warmupWindow;
                ViewTreeObserver.OnDrawListener listener =
                        new ViewTreeObserver.OnDrawListener() {
                            private boolean finishPosted;

                            @Override
                            public void onDraw() {
                                if (host.isDestroyed() || view != warmupView
                                        || window != warmupWindow) {
                                    return;
                                }
                                if (finishPosted) {
                                    return;
                                }
                                finishPosted = true;
                                warmupView.post(() -> {
                                    finish(warmupView, warmupWindow, this, startedAt);
                                });
                            }
                        };
                ViewTreeObserver observer = warmupView.getViewTreeObserver();
                observer.addOnDrawListener(listener);
                drawListener = listener;
                warmupWindow.showAnimated();
            } catch (RuntimeException error) {
                dispose();
                host.log("modern overlay prewarm failed", error);
            }
        };
        task = prewarm;
        mainHandler.post(prewarm);
    }

    private void finish(
            ModernPreviewOverlayView warmupView,
            ModernOverlayWindow warmupWindow,
            ViewTreeObserver.OnDrawListener listener,
            long startedAt) {
        if (host.isDestroyed() || view != warmupView || window != warmupWindow) return;
        ViewTreeObserver observer = warmupView.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnDrawListener(listener);
        drawListener = null;
        view = null;
        window = null;
        try {
            warmupWindow.dispose();
            host.log("modern overlay prewarm completed elapsed_ms="
                    + (SystemClock.elapsedRealtime() - startedAt));
        } catch (RuntimeException error) {
            host.log("modern overlay prewarm cleanup failed", error);
        }
    }

    void dispose() {
        if (task != null) {
            mainHandler.removeCallbacks(task);
            task = null;
        }
        ModernPreviewOverlayView warmupView = view;
        ModernOverlayWindow warmupWindow = window;
        ViewTreeObserver.OnDrawListener listener = drawListener;
        view = null;
        window = null;
        drawListener = null;
        if (warmupView != null && listener != null) {
            ViewTreeObserver observer = warmupView.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnDrawListener(listener);
        }
        if (warmupWindow != null) {
            try {
                warmupWindow.dispose();
            } catch (RuntimeException error) {
                host.log("modern overlay prewarm cleanup failed", error);
            }
        } else if (warmupView != null) {
            try {
                warmupView.disposeOverlay();
            } catch (RuntimeException error) {
                host.log("modern overlay prewarm view cleanup failed", error);
            }
        }
    }
}
