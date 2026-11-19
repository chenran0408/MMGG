package com.leaf.hyperdragshare.codex;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;

/** Immutable, source-neutral content handed to the common share UI. */
final class CapturedContent {
    enum ImageOrigin {
        UNKNOWN,
        REFERENCED_MEDIA,
        PORTAL_MEDIA,
        SCREEN_CAPTURE,
        NODE_CAPTURE
    }

    enum Kind {
        TEXT,
        IMAGE
    }

    final Kind kind;
    final String text;
    final String beforeText;
    final String afterText;
    final Bitmap bitmap;
    final String sourcePackage;
    final Rect sourceBounds;
    final boolean bitmapOwnedByDragShare;
    final boolean pressOcrRegion;
    final ImageOrigin imageOrigin;
    final byte[] encodedImage;
    final Uri mediaUri;
    private final String mediaMimeType;

    private CapturedContent(
            Kind kind,
            String text,
            Bitmap bitmap,
            String sourcePackage,
            Rect sourceBounds,
            boolean bitmapOwnedByDragShare,
            boolean pressOcrRegion,
            ImageOrigin imageOrigin,
            String beforeText,
            String afterText) {
        this(kind, text, bitmap, sourcePackage, sourceBounds, bitmapOwnedByDragShare,
                pressOcrRegion, imageOrigin, beforeText, afterText, null, null, null);
    }

    private CapturedContent(Kind kind, String text, Bitmap bitmap, String sourcePackage,
            Rect sourceBounds, boolean bitmapOwnedByDragShare, boolean pressOcrRegion,
            ImageOrigin imageOrigin, String beforeText, String afterText,
            byte[] encodedImage, Uri mediaUri, String mediaMimeType) {
        this.kind = kind;
        this.text = text;
        this.beforeText = beforeText;
        this.afterText = afterText;
        this.bitmap = bitmap;
        this.sourcePackage = sourcePackage;
        this.sourceBounds = sourceBounds == null ? null : new Rect(sourceBounds);
        this.bitmapOwnedByDragShare = bitmapOwnedByDragShare;
        this.pressOcrRegion = pressOcrRegion;
        this.imageOrigin = imageOrigin;
        this.encodedImage = encodedImage;
        this.mediaUri = mediaUri;
        this.mediaMimeType = mediaMimeType;
    }

    static CapturedContent text(String value, String sourcePackage, Rect sourceBounds) {
        return text(value, sourcePackage, sourceBounds, null, null);
    }

    static CapturedContent text(String value, String sourcePackage, Rect sourceBounds,
                                String beforeText, String afterText) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return new CapturedContent(Kind.TEXT, value, null, sourcePackage, sourceBounds,
                false, false, ImageOrigin.UNKNOWN, beforeText, afterText);
    }

    static CapturedContent image(
            Bitmap value,
            String sourcePackage,
            Rect sourceBounds,
            boolean bitmapOwnedByDragShare) {
        if (value == null || value.isRecycled()) {
            return null;
        }
        return new CapturedContent(
                Kind.IMAGE,
                null,
                value,
                sourcePackage,
                sourceBounds,
                bitmapOwnedByDragShare,
                false,
                ImageOrigin.UNKNOWN,
                null,
                null);
    }

    static CapturedContent screenCaptureImage(
            Bitmap value,
            String sourcePackage,
            Rect sourceBounds) {
        if (value == null || value.isRecycled()) return null;
        return new CapturedContent(Kind.IMAGE, null, value, sourcePackage, sourceBounds,
                true, false, ImageOrigin.SCREEN_CAPTURE, null, null);
    }

    static CapturedContent nodeImage(
            Bitmap value,
            String sourcePackage,
            Rect sourceBounds) {
        if (value == null || value.isRecycled()) return null;
        return new CapturedContent(Kind.IMAGE, null, value, sourcePackage, sourceBounds,
                true, false, ImageOrigin.NODE_CAPTURE, null, null);
    }

    static CapturedContent portalMediaImage(Bitmap value, String sourcePackage, Rect sourceBounds) {
        if (value == null || value.isRecycled()) return null;
        return new CapturedContent(Kind.IMAGE, null, value, sourcePackage, sourceBounds,
                true, false, ImageOrigin.PORTAL_MEDIA, null, null);
    }

    static CapturedContent referencedMediaImage(Bitmap value, String sourcePackage, Rect sourceBounds) {
        if (value == null || value.isRecycled()) return null;
        return new CapturedContent(Kind.IMAGE, null, value, sourcePackage, sourceBounds,
                true, false, ImageOrigin.REFERENCED_MEDIA, null, null);
    }

    static CapturedContent referencedMediaImage(EncodedMedia media, String sourcePackage, Rect bounds) {
        return referencedMediaImage(media, media.preview, sourcePackage, bounds);
    }

    static CapturedContent referencedMediaImage(EncodedMedia media, Bitmap preview,
            String sourcePackage, Rect bounds) {
        return new CapturedContent(Kind.IMAGE, null, preview, sourcePackage, bounds,
                true, false, ImageOrigin.REFERENCED_MEDIA, null, null, media.bytes, null, media.mimeType);
    }

    static CapturedContent stagedMediaImage(Bitmap preview, String sourcePackage, Rect bounds,
            Uri uri, String mimeType) {
        return new CapturedContent(Kind.IMAGE, null, preview, sourcePackage, bounds,
                true, false, ImageOrigin.REFERENCED_MEDIA, null, null, null, uri, mimeType);
    }

    /** A rendered/upscaled crop does not outrank bytes loaded from this press's media reference. */
    static boolean shouldReplaceImage(CapturedContent current, CapturedContent replacement) {
        if (!java.util.Objects.equals(current.sourcePackage, replacement.sourcePackage)) return false;
        boolean currentReference = current.imageOrigin == ImageOrigin.REFERENCED_MEDIA;
        boolean nextReference = replacement.imageOrigin == ImageOrigin.REFERENCED_MEDIA;
        if (currentReference != nextReference) return nextReference;
        boolean currentFile = current.encodedImage != null || current.mediaUri != null;
        boolean nextFile = replacement.encodedImage != null || replacement.mediaUri != null;
        if (currentFile != nextFile) return nextFile;
        return (long) replacement.bitmap.getWidth() * replacement.bitmap.getHeight()
                >= (long) current.bitmap.getWidth() * current.bitmap.getHeight();
    }

    static CapturedContent pressOcrRegion(Bitmap value, String sourcePackage, Rect sourceBounds) {
        if (value == null || value.isRecycled()) return null;
        return new CapturedContent(Kind.IMAGE, null, value, sourcePackage, sourceBounds,
                true, true, ImageOrigin.SCREEN_CAPTURE, null, null);
    }

    boolean isImage() {
        return kind == Kind.IMAGE;
    }

    String mimeType() {
        return isImage() ? (mediaMimeType == null ? "image/png" : mediaMimeType) : "text/plain";
    }
}
