package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * Owns the press OCR candidate state: direct OCR, text delivery and
 * screen-capture rejection. Drag routing follows the shared gesture semantics;
 * generation checks and Root authority stay with {@link PressCaptureSource}
 * and this class reads them only via Host.
 */
final class PressOcrFlow {
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

    PressOcrFlow(Host host) {
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
        OperationTrace.event(host.operationId(), "press_ocr", "started");
        Candidate pending = new Candidate(content, request, host.operationId());
        candidate = pending;
        host.startTouchBlocker(request);
        DragShareLog.i(TAG, "Press OCR started elapsedFromConfirmMs="
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
            DragShareLog.i(TAG, "Press OCR finished elapsedMs="
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
            DragShareLog.w(TAG, "Press OCR direct failed error=" + pending.error.getClass().getSimpleName());
            host.showFailure("文字识别失败：" + pending.error.getMessage());
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
                DragShareLog.i(TAG, "Press OCR text window accepted; image candidate retained until root release");
            }
        } catch (SecurityException | IllegalStateException | IllegalArgumentException failure) {
            OperationTrace.event(pending.operation, "text_window", "failed_" + failure.getClass().getSimpleName());
            DragShareLog.w(TAG, "cannot open press text panel error=" + failure.getClass().getSimpleName());
            host.showFailure("无法显示识别结果：" + failure.getMessage());
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
            DragShareLog.w(TAG, "cannot dismiss this press window error=" + error.getClass().getSimpleName());
            return false;
        }
    }

}
