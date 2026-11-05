package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * Owns the WeChat press-candidate state: direct OCR, text delivery and
 * screen-capture rejection. Drag routing follows the shared gesture semantics;
 * generation checks and Root authority stay with {@link PressCaptureSource}
 * and this class reads them only via Host.
 */
final class WeChatPressFlow {
    /** Keeps the historical log tag so existing diagnostic greps stay valid. */
    private static final String TAG = "PressCaptureSource";

    interface Host {
        Context context();

        DragShareController controller();

        DragShareSettings settings();

        BackgroundTouchBlocker blocker();

        boolean isStopped();

        boolean isPointerDown();

        boolean isReleased();

        PressReleaseMode.Mode releaseMode();

        long downTime();

        float downX();

        float downY();

        long confirmedAt();

        long generation();

        String operationId();

        void showFailure(String reason);

        void recycleContent(CapturedContent content);

        void completeHandoff(Bundle result, long request, String operation);

        void startTouchBlocker(long request);

        void cancelActiveOcr();

        void setActiveOcr(PressCaptureSource.OcrRequest request);

        PressCaptureSource.OcrRecognizer ocrRecognizer();
    }

    static final class Candidate {
        final CapturedContent content;
        final long request;
        final String operation;
        final long started;
        boolean ocrComplete;
        String text;
        Exception error;
        boolean textDelivered;
        boolean textWindowAccepted;

        Candidate(CapturedContent content, long request, String operation) {
            this.content = content;
            this.request = request;
            this.operation = operation;
            this.started = SystemClock.elapsedRealtime();
        }
    }

    private final Host host;
    private Candidate candidate;

    WeChatPressFlow(Host host) {
        this.host = host;
    }

    boolean hasCandidate() {
        return candidate != null;
    }

    Candidate candidate() {
        return candidate;
    }

    void acceptPortalTakeover() {
        Candidate pending = candidate;
        candidate = null;
        if (pending != null && pending.ocrComplete) host.recycleContent(pending.content);
        host.cancelActiveOcr();
    }

    Candidate clearForCancel() {
        Candidate pending = candidate;
        candidate = null;
        return pending;
    }

    void startOcrDirect(long request, CapturedContent content) {
        OperationTrace.event(host.operationId(), "wechat_ocr", "started");
        Candidate pending = new Candidate(content, request, host.operationId());
        candidate = pending;
        host.startTouchBlocker(request);
        DragShareLog.i(TAG, "WeChat OCR started elapsedFromConfirmMs="
                + (SystemClock.uptimeMillis() - host.confirmedAt()) + "; gesture decides text or image");
        host.setActiveOcr(host.ocrRecognizer().recognize(host.context(), content.bitmap, (text, error) -> {
            if (pending != candidate || request != host.generation() || host.isStopped()) {
                OperationTrace.event(pending.operation, "image_ocr", "late_ignored");
                host.recycleContent(content);
                return;
            }
            host.setActiveOcr(null);
            pending.ocrComplete = true;
            pending.text = text;
            pending.error = error;
            DragShareLog.i(TAG, "WeChat OCR finished elapsedMs="
                    + (SystemClock.elapsedRealtime() - pending.started)
                    + " characters=" + (text == null ? 0 : text.length()));
            deliverText();
        }));
    }

    private void deliverText() {
        Candidate pending = candidate;
        if (pending == null || !pending.ocrComplete) return;
        if (!host.isReleased()) return;
        if (host.releaseMode() == PressReleaseMode.Mode.NONE) {
            candidate = null;
            host.recycleContent(pending.content);
            host.controller().endPressPipeline(host.downTime());
            host.blocker().stopAsync();
            return;
        }
        if (pending.textDelivered) {
            finishCandidateIfReleased(pending);
            return;
        }
        if (pending.error != null) {
            if (host.isPointerDown()) return;
            candidate = null;
            host.recycleContent(pending.content);
            DragShareLog.w(TAG, "WeChat OCR direct failed error=" + pending.error.getClass().getSimpleName());
            host.showFailure("微信文字识别失败：" + pending.error.getMessage());
            host.controller().endPressPipeline(host.downTime());
            host.blocker().stopAsync();
            return;
        }
        if (pending.text == null || pending.text.isBlank()) {
            if (host.isPointerDown()) return;
            candidate = null;
            host.recycleContent(pending.content);
            host.showFailure("这次按压没有识别出文字");
            host.controller().endPressPipeline(host.downTime());
            host.blocker().stopAsync();
            return;
        }
        pending.textDelivered = true;
        Bundle recognized = new Bundle();
        recognized.putString("text", pending.text);
        recognized.putLong("press_confirmed_at", host.confirmedAt());
        recognized.putBoolean("drag_preview", host.releaseMode() == PressReleaseMode.Mode.DRAG_PREVIEW);
        recognized.putInt("x", Math.round(host.downX()));
        recognized.putInt("y", Math.round(host.downY()));
        OperationTrace.put(recognized, pending.operation);
        try {
            OperationTrace.event(pending.operation, "text_window", "requested");
            Bundle result = host.context().getContentResolver().call(ImageStagingClient.BASE_URI,
                    BigBangOverlayLauncher.METHOD_OPEN, null, recognized);
            host.completeHandoff(result, pending.request, pending.operation);
            if (pending.textWindowAccepted) {
                DragShareLog.i(TAG, "WeChat OCR text window accepted; image candidate retained until root release");
            }
        } catch (SecurityException | IllegalStateException | IllegalArgumentException failure) {
            OperationTrace.event(pending.operation, "text_window", "failed_" + failure.getClass().getSimpleName());
            DragShareLog.w(TAG, "cannot open WeChat text panel error=" + failure.getClass().getSimpleName());
            host.showFailure("无法显示微信识别结果：" + failure.getMessage());
            host.controller().endPressPipeline(host.downTime());
            host.blocker().stopAsync();
        }
        finishCandidateIfReleased(pending);
    }

    void finishPress() {
        Candidate pending = candidate;
        if (pending != null && pending.ocrComplete) deliverText();
    }

    private void finishCandidateIfReleased(Candidate pending) {
        if (!host.isPointerDown() && candidate == pending) {
            candidate = null;
            host.recycleContent(pending.content);
        }
    }

    boolean dismissTextWindow() {
        Bundle request = new Bundle();
        request.putLong("press_confirmed_at", host.confirmedAt());
        OperationTrace.put(request, host.operationId());
        try {
            Bundle result = host.context().getContentResolver().call(ImageStagingClient.BASE_URI,
                    BigBangOverlayLauncher.METHOD_DISMISS_PRESS, null, request);
            return result != null && result.getBoolean("accepted");
        } catch (SecurityException | IllegalStateException | IllegalArgumentException error) {
            DragShareLog.w(TAG, "cannot dismiss this WeChat press window error=" + error.getClass().getSimpleName());
            return false;
        }
    }

    static boolean isUnverifiedImage(CapturedContent content) {
        return content != null && "com.tencent.mm".equals(content.sourcePackage)
                && content.isImage()
                && content.imageOrigin != CapturedContent.ImageOrigin.PORTAL_MEDIA
                && !content.isWeChatThumbnail();
    }

    void rejectScreenCapture(CapturedContent content, String stage) {
        OperationTrace.event(host.operationId(), "image_drag", "rejected_" + stage + "_"
                + (content == null ? "unknown" : content.imageOrigin.name().toLowerCase(java.util.Locale.ROOT)));
        DragShareLog.i(TAG, "WeChat image drag rejected source="
                + (content == null ? "unknown" : content.imageOrigin.name())
                + " stage=" + stage);
        host.recycleContent(content);
        host.showFailure("没有取得原图，也未能识别图片边界，请通过传送门选图");
        host.controller().endPressPipeline(host.downTime());
        host.blocker().stopAsync();
    }
}
