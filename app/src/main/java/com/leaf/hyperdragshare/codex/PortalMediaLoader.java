package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.CancellationSignal;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;

/** Worker-only source loading; display decoding never replaces the encoded media. */
final class PortalMediaLoader {
    private PortalMediaLoader() {}

    static final class Cancellation {
        private volatile boolean cancelled;
        private Runnable abort;

        boolean isCancelled() { return cancelled; }

        void cancel(Executor executor) {
            Runnable action;
            synchronized (this) {
                if (cancelled) return;
                cancelled = true;
                action = abort;
                abort = null;
            }
            if (action != null) executor.execute(action);
        }

        synchronized void abortWith(Runnable action) throws InterruptedIOException {
            throwIfCancelled();
            abort = action;
        }

        synchronized void clearAbort() { abort = null; }

        void throwIfCancelled() throws InterruptedIOException {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Original media request cancelled");
            }
        }
    }

    static final class Result {
        final Bitmap preview;
        final EncodedMedia encoded;
        Result(EncodedMedia media) { preview = media.preview; encoded = media; }
        Result(Bitmap preview) { this.preview = preview; encoded = null; }
        CapturedContent content(String source, Rect bounds) {
            return encoded == null ? CapturedContent.referencedMediaImage(preview, source, bounds)
                    : CapturedContent.referencedMediaImage(encoded, source, bounds);
        }
    }

    static Result load(Context context, String reference) throws IOException {
        return load(context, reference, new Cancellation());
    }

    static Result load(Context context, String reference, Cancellation cancellation) throws IOException {
        Uri uri = Uri.parse(reference);
        String scheme = uri.getScheme();
        try {
            cancellation.throwIfCancelled();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                try (InputStream input = openNetwork(reference, cancellation)) {
                    return decode(input, cancellation);
                }
            }
            if ("content".equalsIgnoreCase(scheme)) {
                CancellationSignal opening = new CancellationSignal();
                cancellation.abortWith(opening::cancel);
                try (AssetFileDescriptor asset = context.getContentResolver()
                        .openAssetFileDescriptor(uri, "r", opening)) {
                    if (asset == null) throw new IOException("媒体来源未返回文件");
                    try (InputStream input = asset.createInputStream()) {
                        return loadStream(input, cancellation);
                    }
                }
            }
            if ("file".equalsIgnoreCase(scheme) || reference.startsWith("/")) {
                try (InputStream input = new FileInputStream(
                        "file".equalsIgnoreCase(scheme) ? uri.getPath() : reference)) {
                    return loadStream(input, cancellation);
                }
            }
            throw new IOException("不支持的媒体引用");
        } finally {
            cancellation.clearAbort();
        }
    }

    static Result loadStream(InputStream input, Cancellation cancellation) throws IOException {
        cancellation.abortWith(() -> {
            try { input.close(); }
            catch (IOException error) {
                DragShareLog.d("DragShare/PortalCapture", "Unable to close cancelled original media stream");
            }
        });
        return decode(input, cancellation);
    }

    private static Result decode(InputStream input, Cancellation cancellation) throws IOException {
        byte[] bytes = EncodedMedia.read(new FilterInputStream(input) {
            @Override public int read(byte[] data, int offset, int length) throws IOException {
                cancellation.throwIfCancelled();
                return super.read(data, offset, length);
            }
        });
        cancellation.throwIfCancelled();
        return new Result(EncodedMedia.decode(bytes));
    }

    static InputStream openNetwork(String reference) throws IOException {
        return openNetwork(reference, null);
    }

    private static InputStream openNetwork(String reference, Cancellation cancellation) throws IOException {
        URL url = new URL(reference);
        if (!"https".equals(url.getProtocol()) && !"http".equals(url.getProtocol())) {
            throw new IOException("不支持的网络媒体引用");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(1200);
        connection.setReadTimeout(1800);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "MMGG-PortalCapture/1");
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        connection.setRequestProperty("Referer", url.getProtocol() + "://" + url.getHost() + "/");
        try {
            if (cancellation != null) cancellation.abortWith(connection::disconnect);
            return new FilterInputStream(connection.getInputStream()) {
                @Override public void close() throws IOException {
                    try { super.close(); } finally { connection.disconnect(); }
                }
            };
        } catch (IOException error) {
            connection.disconnect();
            throw error;
        }
    }
}
