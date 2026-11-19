package com.leaf.hyperdragshare.codex;

import android.graphics.Bitmap;
import android.net.Uri;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;

import java.net.URI;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.leaf.hyperdragshare.compat.xposed.XposedHelpers;

/** The only adapter allowed to read Taplus' private fields and methods. */
final class PortalContentCaptureSource {
    private static final ThreadPoolExecutor ORIGINAL_IMAGE_EXECUTOR =
            new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "dragshare-portal-image");
                thread.setDaemon(true);
                return thread;
            });
    private static final ExecutorService ORIGINAL_IMAGE_ABORT_EXECUTOR =
            Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "dragshare-portal-image-abort");
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

    record ImageReference(String url, String sourcePackage, Rect bounds) {}
    record Pick(CapturedContent preview, ImageReference original) {}

    /** The callback result takes precedence over service fields from a previous pick. */
    static Pick capturePick(Object taplusService, DragShareSettings settings, Object catcherResult) {
        if (catcherResult == null) {
            CapturedContent legacy = capture(taplusService, settings);
            return legacy == null ? null : new Pick(legacy, null);
        }
        if (taplusService == null) {
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

            // Diagnostic only: record the shape of the current image result so
            // we can tell whether Portal exposed a media reference or only a
            // rendered preview. Never log payload text or URI query values.
            traceImagePayloadShape(catcherResult);

            Object textParams = XposedHelpers.callMethod(catcherResult, "getParams", "text_pick");
            if (textParams instanceof Map) {
                Map<?, ?> values = (Map<?, ?>) textParams;
                String text = textFromPickValues(values);
                if (text != null) {
                    Rect textBounds = rectFrom(values.get("pick_mode"));
                    CapturedContent content = CapturedContent.text(
                            text, sourcePackage, textBounds == null ? sourceBounds : textBounds);
                    traceCaptureOutcome(sourcePackage, "accepted_text_callback");
                    return new Pick(content, null);
                }
            }

            Bitmap bestBitmap = null;
            String imageUrl = null;
            Rect viewRect = null;
            Object imageParams = XposedHelpers.callMethod(catcherResult, "getParams", "image_pick");
            if (imageParams instanceof Map) {
                Map<?, ?> values = (Map<?, ?>) imageParams;
                bestBitmap = largerBitmap(bestBitmap, values.get("bitmap"), "image_pick");
                Object mode = values.get("image_pick_mode");
                if (mode instanceof Bundle) {
                    Bundle imageMode = (Bundle) mode;
                    bestBitmap = largerBitmap(bestBitmap, imageMode.get("viewBitmap"), "viewBitmap");
                    imageUrl = imageMode.getString("imgUrl");
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
            Rect bounds = viewRect == null ? sourceBounds : viewRect;
            ImageReference original = settings.isSharingEnabled(true) && isSupportedImageReference(imageUrl)
                    ? new ImageReference(normalizeImageUrl(imageUrl), sourcePackage,
                            bounds == null ? null : new Rect(bounds)) : null;
            CapturedContent preview = bestBitmap == null || bestBitmap.isRecycled() ? null
                    : CapturedContent.portalMediaImage(bestBitmap.copy(Bitmap.Config.ARGB_8888, false),
                            sourcePackage, bounds);
            traceCaptureOutcome(sourcePackage, preview == null
                    ? (original == null ? "missing_callback_image" : "accepted_image_reference")
                    : "accepted_image_callback");
            return preview == null && original == null ? null : new Pick(preview, original);
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
        return pixelCount(bitmap) > pixelCount(current) ? bitmap : current;
    }

    private static void traceImagePayloadShape(Object catcherResult) {
        try {
            Object imageParams = XposedHelpers.callMethod(catcherResult, "getParams", "image_pick");
            if (!(imageParams instanceof Map)) {
                DragShareLog.i("DragShare/PortalCapture",
                        "image payload shape params=" + describeType(imageParams));
                return;
            }
            Map<?, ?> values = (Map<?, ?>) imageParams;
            DragShareLog.i("DragShare/PortalCapture",
                    "image payload keys=" + safeKeys(values) + " fields=" + describeFields(values));
            Object mode = values.get("image_pick_mode");
            if (mode instanceof Bundle) {
                Bundle bundle = (Bundle) mode;
                DragShareLog.i("DragShare/PortalCapture",
                        "image mode keys=" + bundle.keySet() + " fields=" + describeBundle(bundle));
            } else if (mode != null) {
                DragShareLog.i("DragShare/PortalCapture",
                        "image mode type=" + describeType(mode));
            }
            Object propertyMap = XposedHelpers.callMethod(catcherResult, "getPropertyMap");
            if (propertyMap instanceof Map) {
                Map<?, ?> properties = (Map<?, ?>) propertyMap;
                DragShareLog.i("DragShare/PortalCapture",
                        "image property keys=" + safeKeys(properties)
                                + " fields=" + describeFields(properties));
            } else if (propertyMap != null) {
                DragShareLog.i("DragShare/PortalCapture",
                        "image property type=" + describeType(propertyMap));
            }
        } catch (Throwable error) {
            DragShareLog.i("DragShare/PortalCapture",
                    "image payload shape unavailable error=" + error.getClass().getSimpleName());
        }
    }

    private static String safeKeys(Map<?, ?> values) {
        StringBuilder result = new StringBuilder("[");
        boolean first = true;
        int count = 0;
        for (Object key : values.keySet()) {
            if (count++ >= 32) {
                result.append(",...");
                break;
            }
            if (!first) result.append(',');
            first = false;
            result.append(String.valueOf(key));
        }
        return result.append(']').toString();
    }

    private static String describeFields(Map<?, ?> values) {
        StringBuilder result = new StringBuilder("[");
        boolean first = true;
        int count = 0;
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (count++ >= 32) {
                result.append(",...");
                break;
            }
            if (!first) result.append(',');
            first = false;
            result.append(String.valueOf(entry.getKey())).append('=').append(describeValue(
                    String.valueOf(entry.getKey()), entry.getValue()));
        }
        return result.append(']').toString();
    }

    private static String describeBundle(Bundle bundle) {
        StringBuilder result = new StringBuilder("[");
        boolean first = true;
        int count = 0;
        for (String key : bundle.keySet()) {
            if (count++ >= 32) {
                result.append(",...");
                break;
            }
            if (!first) result.append(',');
            first = false;
            result.append(key).append('=').append(describeValue(key, bundle.get(key)));
        }
        return result.append(']').toString();
    }

    private static String describeValue(String key, Object value) {
        if (value == null) return "null";
        if (value instanceof Bitmap) {
            Bitmap bitmap = (Bitmap) value;
            return "Bitmap(" + bitmap.getWidth() + 'x' + bitmap.getHeight()
                    + (bitmap.isRecycled() ? ",recycled" : "") + ')';
        }
        if (value instanceof String || value instanceof CharSequence) {
            String text = value.toString();
            if (looksLikeReferenceKey(key)) return referenceShape(text);
            return "String(len=" + text.length() + ')';
        }
        if (value instanceof Uri) return referenceShape(value.toString());
        if (value instanceof Bundle) return "Bundle(keys=" + ((Bundle) value).keySet() + ')';
        if (value instanceof Map) return "Map(size=" + ((Map<?, ?>) value).size() + ')';
        if (value instanceof Parcelable) return "Parcelable(" + value.getClass().getSimpleName() + ')';
        return describeType(value);
    }

    private static String describeType(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static boolean looksLikeReferenceKey(String key) {
        String lower = key == null ? "" : key.toLowerCase(java.util.Locale.US);
        return lower.contains("url") || lower.contains("uri") || lower.contains("path")
                || lower.contains("file") || lower.contains("src") || lower.contains("ref")
                || lower.contains("uuid");
    }

    private static String referenceShape(String raw) {
        if (raw == null || raw.isEmpty()) return "ref(empty)";
        try {
            Uri uri = Uri.parse(raw.startsWith("//") ? "http:" + raw : raw);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            String extension = "";
            if (path != null) {
                int dot = path.lastIndexOf('.');
                if (dot >= 0 && dot + 1 < path.length() && dot >= path.lastIndexOf('/')) {
                    extension = path.substring(dot + 1).toLowerCase(java.util.Locale.US);
                    if (extension.length() > 8) extension = "long";
                }
            }
            return "ref(scheme=" + (scheme == null ? "none" : scheme)
                    + ",host=" + (host == null ? "none" : "present")
                    + ",path=" + (path == null ? "none" : "present")
                    + ",ext=" + (extension.isEmpty() ? "none" : extension)
                    + ",query=" + (uri.getQuery() == null ? "none" : "present") + ')';
        } catch (RuntimeException error) {
            return "ref(invalid,len=" + raw.length() + ')';
        }
    }

    private static long pixelCount(Bitmap bitmap) {
        return (long) bitmap.getWidth() * bitmap.getHeight();
    }

    private static PortalMediaLoader.Result loadPortalImageUnbounded(Object taplusService, String normalized,
            PortalMediaLoader.Cancellation cancellation) {
        try {
            cancellation.throwIfCancelled();
            android.content.Context context = (android.content.Context) taplusService;
            String scheme = Uri.parse(normalized).getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                    || "content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme)
                    || normalized.startsWith("/")) {
                try {
                    return PortalMediaLoader.load(context, normalized, cancellation);
                } catch (IOException networkError) {
                    if (cancellation.isCancelled()) return null;
                    DragShareLog.i("DragShare/PortalCapture",
                            "direct Portal image request failed; trying Taplus loader error="
                                    + networkError.getClass().getSimpleName());
                }
                cancellation.throwIfCancelled();
                Class<?> iconManager = XposedHelpers.findClassIfExists(
                        "com.miui.contentextension.utils.IconManager",
                        taplusService.getClass().getClassLoader());
                if (iconManager != null) {
                    android.content.Context appContext = context.getApplicationContext();
                    Object result = XposedHelpers.callStaticMethod(
                            iconManager, "loadImageSync", appContext == null ? context : appContext, normalized);
                    if (!cancellation.isCancelled() && result instanceof Bitmap
                            && !((Bitmap) result).isRecycled()) {
                        // Taplus uses Glide's original size, but its cached Bitmap is shared.
                        return new PortalMediaLoader.Result(((Bitmap) result).copy(Bitmap.Config.ARGB_8888, false));
                    }
                }
                return null;
            }
            return null;
        } catch (Throwable error) {
            if (cancellation.isCancelled()) return null;
            DragShareLog.w("DragShare/PortalCapture",
                    "Portal original image load failed error="
                            + error.getClass().getSimpleName());
            return null;
        }
    }

    private static String normalizeImageUrl(String url) {
        return url.startsWith("//") ? "http:" + url : url;
    }

    // One load per accepted source. Neither the host callback nor the UI waits on I/O.
    static OriginalImageRequest loadOriginalImageAsync(Object service, ImageReference reference,
                                                        Consumer<CapturedContent> callback) {
        OriginalImageRequest request = new OriginalImageRequest(service, reference, callback);
        ORIGINAL_IMAGE_EXECUTOR.execute(request.work);
        return request;
    }

    static CapturedContent loadOriginalImage(Object service, ImageReference reference) {
        return loadOriginalImage(service, reference, new PortalMediaLoader.Cancellation());
    }

    private static CapturedContent loadOriginalImage(Object service, ImageReference reference,
                                                     PortalMediaLoader.Cancellation cancellation) {
        PortalMediaLoader.Result result = loadPortalImageUnbounded(service, reference.url(), cancellation);
        if (result == null || result.preview == null || result.preview.isRecycled()) return null;
        return result.content(reference.sourcePackage(), reference.bounds());
    }

    static final class OriginalImageRequest {
        private final PortalMediaLoader.Cancellation cancellation = new PortalMediaLoader.Cancellation();
        private final FutureTask<Void> work;

        private OriginalImageRequest(Object service, ImageReference reference,
                                     Consumer<CapturedContent> callback) {
            work = new FutureTask<>(() -> {
                CapturedContent image = loadOriginalImage(service, reference, cancellation);
                if (!cancellation.isCancelled()) callback.accept(image);
                else if (image != null) image.bitmap.recycle();
                return null;
            });
        }

        void cancel() {
            if (work.isDone()) return;
            cancellation.cancel(ORIGINAL_IMAGE_ABORT_EXECUTOR);
            work.cancel(true);
            ORIGINAL_IMAGE_EXECUTOR.remove(work);
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
            return url instanceof String && isSupportedImageReference((String) url);
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

    private static boolean isSupportedImageReference(String value) {
        if (value == null || value.isEmpty() || !value.equals(value.trim())) return false;
        String normalized = value.startsWith("//") ? "http:" + value : value;
        if (isLoadableImageUrl(normalized)) return true;
        if (normalized.startsWith("/")) return true;
        try {
            Uri uri = Uri.parse(normalized);
            String scheme = uri.getScheme();
            return ("content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme))
                    && uri.getPath() != null && !uri.getPath().isEmpty();
        } catch (RuntimeException error) {
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
