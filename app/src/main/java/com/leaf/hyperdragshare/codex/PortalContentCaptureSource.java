package com.leaf.hyperdragshare.codex;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.leaf.hyperdragshare.compat.xposed.XposedHelpers;

/** The only adapter allowed to read Taplus' private fields and methods. */
final class PortalContentCaptureSource {
    private static final long ORIGINAL_IMAGE_WAIT_MS = 250L;
    private static final ExecutorService ORIGINAL_IMAGE_EXECUTOR =
            Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "dragshare-portal-image");
                thread.setDaemon(true);
                return thread;
            });

    private PortalContentCaptureSource() {}

    static String clipboardCardText(Class<?> serviceClass) {
        return (String) XposedHelpers.callStaticMethod(serviceClass, "getClipboardContent");
    }

    static Object clipboardCardOwner(Object listener) {
        return XposedHelpers.getObjectField(listener, "this$0");
    }

    static void dismissClipboardCard(Object owner) {
        XposedHelpers.callMethod(owner, "hideSplashFloatView", true);
    }

    static CapturedContent capture(Object taplusService, DragShareSettings settings) {
        if (taplusService == null) {
            traceCaptureOutcome(null, "missing_payload");
            return null;
        }
        Class<?> serviceClass = taplusService.getClass();
        String sourcePackage = null;
        try {
            sourcePackage = (String) XposedHelpers.callStaticMethod(serviceClass, "getInjectorPackage");
            if (BuildConfig.APPLICATION_ID.equals(sourcePackage)) return null;
            android.content.Context context = (android.content.Context) taplusService;
            if (AccessibilityBlacklist.isBlocked(context, settings, sourcePackage)
                    || GameCapturePolicy.shouldPause(context, settings, sourcePackage)) {
                traceCaptureOutcome(sourcePackage, "ignored_by_policy");
                DragShareLog.i("DragShare/PortalCapture",
                        "portal content ignored source=" + sourcePackage + " reason=excluded_source");
                return null;
            }
            Rect sourceBounds = (Rect) XposedHelpers.callMethod(taplusService, "getContentRect");
            Object textMode = XposedHelpers.getStaticObjectField(serviceClass, "sIsTextMode");
            if (Boolean.TRUE.equals(textMode)) {
                String value = (String) XposedHelpers.getStaticObjectField(serviceClass, "sContent");
                CapturedContent content = CapturedContent.text(value, sourcePackage, sourceBounds);
                traceCaptureOutcome(sourcePackage, content == null ? "missing_payload" : "accepted_text");
                return content;
            }
            Object result = XposedHelpers.getStaticObjectField(serviceClass, "mCatcherResult");
            Bundle imageMode = imagePickMode(result);
            if (!hasCurrentImagePayload(imageMode)) {
                traceCaptureOutcome(sourcePackage, "missing_payload");
                DragShareLog.i("DragShare/PortalCapture",
                        "portal image ignored source=" + sourcePackage + " reason=missing_image_payload");
                return null;
            }
            Bitmap value = (Bitmap) XposedHelpers.callStaticMethod(serviceClass, "getBitmap");
            if (!matchesCurrentImageBitmap(imageMode, value)) {
                traceCaptureOutcome(sourcePackage, "stale_bitmap");
                DragShareLog.i("DragShare/PortalCapture",
                        "portal image ignored source=" + sourcePackage + " reason=stale_bitmap");
                return null;
            }
            // HyperOS 4's cancelTask() recycles the service-owned sBitmap. The
            // module stages images asynchronously, so keep an independent snapshot.
            Bitmap snapshot = value.copy(Bitmap.Config.ARGB_8888, false);
            DragShareLog.i("DragShare/PortalCapture",
                    "portal image accepted source=" + sourcePackage
                            + " origin=" + imageOrigin(imageMode)
                            + " bounds=" + sourceBounds
                            + " bitmap=" + snapshot.getWidth() + "x" + snapshot.getHeight());
            CapturedContent content = CapturedContent.portalMediaImage(snapshot, sourcePackage, sourceBounds);
            if (content != null) {
                traceCaptureOutcome(sourcePackage, "accepted_image_" + imageOrigin(imageMode));
            }
            return content;
        } catch (NoSuchFieldError | NoSuchMethodError | XposedHelpers.InvocationTargetError | RuntimeException error) {
            traceCaptureOutcome(sourcePackage, "read_error");
            DragShareLog.w("DragShare/PortalCapture", "Unable to read portal 5.1.5 content error="
                    + error.getClass().getSimpleName() + "; keeping host behavior");
            if (taplusService instanceof android.content.Context) {
                android.widget.Toast.makeText((android.content.Context) taplusService,
                        "无法读取传送门内容，请查看模块日志", android.widget.Toast.LENGTH_LONG).show();
            }
            return null;
        }
    }

    /**
     * Captures the result passed by Taplus 5.1.5's pick callback. The callback
     * argument is authoritative for this gesture; static service fields are
     * intentionally not consulted when it is present because they may still
     * contain the previous pick.
     */
    static CapturedContent capture(
            Object taplusService,
            DragShareSettings settings,
            Object catcherResult) {
        if (catcherResult == null) {
            return capture(taplusService, settings);
        }
        return captureFromCatcherResult(taplusService, settings, catcherResult);
    }

    private static CapturedContent captureFromCatcherResult(
            Object taplusService,
            DragShareSettings settings,
            Object catcherResult) {
        if (taplusService == null || catcherResult == null) {
            return null;
        }
        Class<?> serviceClass = taplusService.getClass();
        String sourcePackage = null;
        Rect sourceBounds = null;
        try {
            sourcePackage = (String) XposedHelpers.callStaticMethod(
                    serviceClass, "getInjectorPackage");
            sourceBounds = (Rect) XposedHelpers.callMethod(taplusService, "getContentRect");
            if (BuildConfig.APPLICATION_ID.equals(sourcePackage)) {
                return null;
            }
            android.content.Context context = (android.content.Context) taplusService;
            if (AccessibilityBlacklist.isBlocked(context, settings, sourcePackage)
                    || GameCapturePolicy.shouldPause(context, settings, sourcePackage)) {
                traceCaptureOutcome(sourcePackage, "ignored_by_policy");
                return null;
            }

            Object textParams = XposedHelpers.callMethod(catcherResult, "getParams", "text_pick");
            if (textParams instanceof Map) {
                Map<?, ?> values = (Map<?, ?>) textParams;
                String text = textFromPickValues(values);
                if (text != null) {
                    Rect textBounds = rectFrom(values.get("pick_mode"));
                    CapturedContent content = CapturedContent.text(
                            text, sourcePackage, textBounds == null ? sourceBounds : textBounds);
                    traceCaptureOutcome(sourcePackage, "accepted_text_callback");
                    return content;
                }
            }

            Bitmap bestBitmap = null;
            Rect viewRect = null;
            Object imageParams = XposedHelpers.callMethod(catcherResult, "getParams", "image_pick");
            if (imageParams instanceof Map) {
                Map<?, ?> values = (Map<?, ?>) imageParams;
                bestBitmap = largerBitmap(bestBitmap, values.get("bitmap"), "image_pick");
                Object mode = values.get("image_pick_mode");
                if (mode instanceof Bundle) {
                    Bundle imageMode = (Bundle) mode;
                    bestBitmap = largerBitmap(bestBitmap, imageMode.get("viewBitmap"), "viewBitmap");
                    String imageUrl = imageMode.getString("imgUrl");
                    if (isLoadableImageUrl(imageUrl)) {
                        Bitmap original = loadPortalImage(taplusService, imageUrl);
                        bestBitmap = largerBitmap(bestBitmap, original, "imgUrl");
                    }
                    viewRect = rectFrom(imageMode);
                }
            }
            try {
                Object propertyMap = XposedHelpers.callMethod(
                        catcherResult, "getPropertyMap");
                if (propertyMap instanceof Map) {
                    for (Object value : ((Map<?, ?>) propertyMap).values()) {
                        bestBitmap = largerBitmap(bestBitmap, value, "propertyMap");
                    }
                }
            } catch (RuntimeException ignored) {
                // The image_pick map remains sufficient on supported builds.
            }
            if (bestBitmap == null || bestBitmap.isRecycled()) {
                traceCaptureOutcome(sourcePackage, "missing_callback_image");
                return null;
            }
            Bitmap snapshot = bestBitmap.copy(Bitmap.Config.ARGB_8888, false);
            CapturedContent content = CapturedContent.portalMediaImage(
                    snapshot, sourcePackage, viewRect == null ? sourceBounds : viewRect);
            traceCaptureOutcome(sourcePackage, content == null
                    ? "missing_callback_image" : "accepted_image_callback");
            return content;
        } catch (Throwable error) {
            traceCaptureOutcome(sourcePackage, "callback_read_error");
            DragShareLog.w("DragShare/PortalCapture",
                    "Unable to read current Taplus callback result error="
                            + error.getClass().getSimpleName(), error);
            return null;
        }
    }

    private static String textFromPickValues(Map<?, ?> values) {
        Object mode = values.get("pick_mode");
        if (mode instanceof CharSequence) {
            String text = mode.toString().trim();
            return text.isEmpty() ? null : text;
        }
        if (mode instanceof Bundle) {
            CharSequence content = ((Bundle) mode).getCharSequence("viewContent");
            if (content != null && content.length() > 0) return content.toString();
        }
        Object content = values.get("viewContent");
        if (content instanceof CharSequence) {
            String text = content.toString().trim();
            return text.isEmpty() ? null : text;
        }
        return null;
    }

    private static Rect rectFrom(Object value) {
        if (value instanceof Bundle) {
            value = ((Bundle) value).get("view_rect");
        }
        return value instanceof Rect ? new Rect((Rect) value) : null;
    }

    private static Bitmap largerBitmap(Bitmap current, Object candidate, String source) {
        if (!(candidate instanceof Bitmap)) return current;
        Bitmap bitmap = (Bitmap) candidate;
        if (bitmap.isRecycled()) return current;
        DragShareLog.i("DragShare/PortalCapture", "callback image candidate source=" + source
                + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight());
        if (current == null) return bitmap;
        long currentPixels = (long) current.getWidth() * current.getHeight();
        long candidatePixels = (long) bitmap.getWidth() * bitmap.getHeight();
        return candidatePixels > currentPixels ? bitmap : current;
    }

    private static Bitmap loadPortalImage(Object taplusService, String url) {
        if (!(taplusService instanceof android.content.Context)) return null;
        String normalized = url.startsWith("//") ? "http:" + url : url;
        Future<Bitmap> request = ORIGINAL_IMAGE_EXECUTOR.submit(
                () -> loadPortalImageUnbounded(taplusService, normalized));
        try {
            return request.get(ORIGINAL_IMAGE_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            request.cancel(true);
            DragShareLog.i("DragShare/PortalCapture",
                    "Portal original image timed out after " + ORIGINAL_IMAGE_WAIT_MS + "ms");
            return null;
        } catch (InterruptedException interrupted) {
            request.cancel(true);
            Thread.currentThread().interrupt();
            return null;
        } catch (java.util.concurrent.ExecutionException error) {
            return null;
        }
    }

    private static Bitmap loadPortalImageUnbounded(Object taplusService, String normalized) {
        try {
            Class<?> iconManager = XposedHelpers.findClassIfExists(
                    "com.miui.contentextension.utils.IconManager",
                    taplusService.getClass().getClassLoader());
            if (iconManager == null) return null;
            android.content.Context context = (android.content.Context) taplusService;
            android.content.Context appContext = context.getApplicationContext();
            Object result = XposedHelpers.callStaticMethod(
                    iconManager, "loadImageSync", appContext == null ? context : appContext, normalized);
            return result instanceof Bitmap ? (Bitmap) result : null;
        } catch (Throwable error) {
            DragShareLog.w("DragShare/PortalCapture",
                    "Portal original image load failed error="
                            + error.getClass().getSimpleName());
            return null;
        }
    }

    static PointF touchPointFromResult(Object catcherResult) {
        if (catcherResult == null) return null;
        PointF point = touchPointFromParams(catcherResult, "text_pick");
        return point == null ? touchPointFromParams(catcherResult, "image_pick") : point;
    }

    private static PointF touchPointFromParams(Object catcherResult, String key) {
        try {
            Object params = XposedHelpers.callMethod(catcherResult, "getParams", key);
            if (!(params instanceof Map)) return null;
            Object mode = ((Map<?, ?>) params).get(
                    "text_pick".equals(key) ? "pick_mode" : "image_pick_mode");
            if (!(mode instanceof Bundle)) return null;
            Parcelable[] points = ((Bundle) mode).getParcelableArray("touch_points");
            if (points != null && points.length > 0 && points[0] instanceof PointF) {
                PointF first = (PointF) points[0];
                return new PointF(first.x, first.y);
            }
        } catch (Throwable ignored) {
            // The controller falls back to the latest Root pointer.
        }
        return null;
    }

    private static void traceCaptureOutcome(String sourcePackage, String outcome) {
        String source = sourcePackage == null ? "unknown" : sourcePackage;
        OperationTrace.event("-", "portal_capture", source + "_" + outcome);
    }

    private static Bundle imagePickMode(Object result) {
        if (result == null) return null;
        try {
            Object params = XposedHelpers.callMethod(result, "getParams", "image_pick");
            if (!(params instanceof Map)) return null;
            Object mode = ((Map<?, ?>) params).get("image_pick_mode");
            return mode instanceof Bundle ? (Bundle) mode : null;
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
    }

    static boolean hasCurrentImagePayload(Bundle imageMode) {
        if (imageMode == null) return false;
        if (imageMode.containsKey("imgUrl")) {
            Object url = imageMode.get("imgUrl");
            return url instanceof String && isLoadableImageUrl((String) url);
        }
        if (imageMode.containsKey("viewBitmap")) {
            Object candidate = imageMode.get("viewBitmap");
            return candidate instanceof Bitmap && !((Bitmap) candidate).isRecycled();
        }
        return false;
    }

    static boolean matchesCurrentImageBitmap(Bundle imageMode, Bitmap value) {
        if (!hasCurrentImagePayload(imageMode) || value == null || value.isRecycled()) return false;
        if (imageMode.containsKey("imgUrl")) return true;
        if (imageMode.containsKey("viewBitmap")) {
            return value == imageMode.get("viewBitmap");
        }
        return true;
    }

    private static boolean isLoadableImageUrl(String value) {
        if (value == null || value.isEmpty() || !value.equals(value.trim())) return false;
        String normalized = value.startsWith("//") ? "http:" + value : value;
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            return ("http".equals(scheme) || "https".equals(scheme))
                    && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (java.net.URISyntaxException error) {
            return false;
        }
    }

    private static String imageOrigin(Bundle imageMode) {
        if (imageMode == null) return "unknown";
        if (imageMode.containsKey("imgUrl")) return "image_url";
        if (imageMode.containsKey("viewBitmap")) return "view_bitmap";
        return "other";
    }

    static PointF initialPoint(Object taplusService, float fallbackX, float fallbackY) {
        if (fallbackX >= 0f && fallbackY >= 0f) {
            return new PointF(fallbackX, fallbackY);
        }
        if (taplusService != null) {
            try {
                PointF point = (PointF) XposedHelpers.callMethod(
                        taplusService,
                        "getFirstTouchPoint");
                if (point != null) {
                    return new PointF(point.x, point.y);
                }
                Point injectorPoint = (Point) XposedHelpers.callStaticMethod(
                        taplusService.getClass(),
                        "getInjectorPoint");
                if (injectorPoint != null) {
                    return new PointF(injectorPoint.x, injectorPoint.y);
                }
            } catch (NoSuchMethodError | XposedHelpers.InvocationTargetError | RuntimeException error) {
                DragShareLog.w("DragShare/PortalCapture", "Unable to read portal initial point", error);
            }
        }
        return null;
    }
}
