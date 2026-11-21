package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

final class ImageStagingClient {
    interface Callback {
        void onStaged(Uri uri);

        void onFailure(Throwable error);
    }

    static final String AUTHORITY = "com.leaf.hyperdragshare.codex.share";
    static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY);
    static final String METHOD_STAGE = "stage_image";
    static final String METHOD_GRANT = "grant_image";
    static final String METHOD_REVOKE = "revoke_image";
    /** Legacy byte-array input accepted by the Provider during process upgrades. */
    static final String EXTRA_BYTES = "bytes";
    static final String EXTRA_STREAM = "stream";
    static final String EXTRA_PACKAGE = "package";
    static final String RESULT_URI = "uri";
    static final String RESULT_GRANTED = "granted";

    private static final long ENCODER_JOIN_TIMEOUT_MS = 60_000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "drag-share-image-stage");
        thread.setDaemon(true);
        return thread;
    });

    private ImageStagingClient() {}

    /** Copies an existing image into the share provider; call only from a worker thread. */
    static Uri stageUri(Context context, Uri source) throws IOException {
        try (ParcelFileDescriptor input = context.getContentResolver().openFileDescriptor(source, "r")) {
            if (input == null) throw new IOException("无法打开待复制图片");
            Bundle extras = new Bundle();
            extras.putParcelable(EXTRA_STREAM, input);
            Bundle result = context.getContentResolver().call(BASE_URI, METHOD_STAGE, null, extras);
            String value = result == null ? null : result.getString(RESULT_URI);
            if (value == null) throw new IOException("图片暂存未返回可用地址");
            return Uri.parse(value);
        }
    }

    static void stage(Context context, Bitmap bitmap, Callback callback) {
        submit(context, bitmap, null, callback);
    }

    /** A prepared URI completes immediately; queued work belongs to the drag session. */
    static Future<?> stage(Context context, CapturedContent content, Callback callback) {
        if (content.mediaUri != null) {
            callback.onStaged(content.mediaUri);
            return null;
        }
        return submit(context, content.bitmap, content.encodedImage, callback);
    }

    private static Future<?> submit(Context context, Bitmap bitmap, byte[] original, Callback callback) {
        return EXECUTOR.submit(() -> {
            try {
                Uri uri = original != null ? stageEncoded(context, original) : stagePng(context, bitmap);
                callback.onStaged(uri);
            } catch (Throwable error) {
                callback.onFailure(error);
            }
        });
    }

    /** Original compressed bytes only cross Binder through a descriptor, never a byte[] extra. */
    static Uri stageEncoded(Context context, byte[] bytes) throws Throwable {
        return stageStream(context, output -> output.write(bytes));
    }

    private interface StreamWriter {
        void write(java.io.OutputStream output) throws IOException;
    }

    private static Uri stagePng(Context context, Bitmap bitmap) throws Throwable {
        if (context == null || bitmap == null || bitmap.isRecycled()) {
            throw new IllegalArgumentException("Bitmap is unavailable");
        }
        return stageStream(context, output -> BitmapEncoder.writePng(bitmap, output));
    }

    private static Uri stageStream(Context context, StreamWriter writer) throws Throwable {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor readSide = pipe[0];
        ParcelFileDescriptor writeSide = pipe[1];
        AtomicReference<Throwable> encoderFailure = new AtomicReference<>();
        Thread encoder = new Thread(() -> {
            try (ParcelFileDescriptor.AutoCloseOutputStream output =
                         new ParcelFileDescriptor.AutoCloseOutputStream(writeSide)) {
                writer.write(output);
            } catch (Throwable error) {
                encoderFailure.set(error);
            }
        }, "drag-share-png-encoder");
        encoder.setDaemon(true);
        encoder.start();

        Bundle result = null;
        Throwable providerFailure = null;
        try {
            Bundle extras = new Bundle();
            extras.putParcelable(EXTRA_STREAM, readSide);
            result = context.getContentResolver().call(
                    BASE_URI, METHOD_STAGE, null, extras);
        } catch (Throwable error) {
            providerFailure = error;
        } finally {
            closeQuietly(readSide);
        }

        Throwable writeFailure = waitForEncoder(encoder, writeSide, encoderFailure);
        if (providerFailure != null) {
            if (writeFailure != null && writeFailure != providerFailure) {
                providerFailure.addSuppressed(writeFailure);
            }
            throw providerFailure;
        }
        if (writeFailure != null) {
            throw writeFailure;
        }
        String uriValue = result == null ? null : result.getString(RESULT_URI);
        if (uriValue == null) {
            throw new IllegalStateException("Image provider returned no URI");
        }
        return Uri.parse(uriValue);
    }

    private static Throwable waitForEncoder(
            Thread encoder,
            ParcelFileDescriptor writeSide,
            AtomicReference<Throwable> encoderFailure) {
        try {
            encoder.join(ENCODER_JOIN_TIMEOUT_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            closeQuietly(writeSide);
            encoder.interrupt();
            return new IOException("PNG encoding interrupted", interrupted);
        }
        if (encoder.isAlive()) {
            closeQuietly(writeSide);
            encoder.interrupt();
            return new IOException("PNG encoding timed out");
        }
        return encoderFailure.get();
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (IOException ignored) {
            // The paired stream can already have closed the descriptor.
        }
    }

    static void grantReadAccess(Context context, Uri uri, String packageName) {
        Bundle extras = new Bundle();
        extras.putString(RESULT_URI, uri.toString());
        extras.putString(EXTRA_PACKAGE, packageName);
        Bundle result = context.getContentResolver().call(
                BASE_URI, METHOD_GRANT, null, extras);
        if (result == null || !result.getBoolean(RESULT_GRANTED)) {
            throw new SecurityException("Image provider did not grant URI access");
        }
    }

    static void revokeReadAccess(Context context, Uri uri, String packageName) {
        Bundle extras = new Bundle();
        extras.putString(RESULT_URI, uri.toString());
        extras.putString(EXTRA_PACKAGE, packageName);
        context.getContentResolver().call(BASE_URI, METHOD_REVOKE, null, extras);
    }
}
