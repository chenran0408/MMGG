package com.leaf.hyperdragshare.codex;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Copies a captured image into the user's Pictures collection. */
final class LocalImageSaver {
    private static final String DIRECTORY_NAME = "HyperDragShare";
    private static final String MIME_PNG = "image/png";

    private LocalImageSaver() {}

    static Uri save(Context context, Bitmap bitmap) throws IOException {
        if (context == null || bitmap == null || bitmap.isRecycled()) {
            throw new IOException("Image is unavailable");
        }
        return save(context, timestampName(System.currentTimeMillis()), MIME_PNG,
                output -> writePng(bitmap, output));
    }

    static Uri save(Context context, Uri source, String mimeTypeHint) throws IOException {
        if (context == null || source == null) throw new IOException("Image source is unavailable");
        ContentResolver resolver = context.getContentResolver();
        String mimeType = resolver.getType(source);
        if (mimeType == null || mimeType.isEmpty() || !mimeType.startsWith("image/")) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(source.getLastPathSegment());
            String pathMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (pathMimeType != null && pathMimeType.startsWith("image/")) mimeType = pathMimeType;
        }
        if ((mimeType == null || !mimeType.startsWith("image/"))
                && mimeTypeHint != null && mimeTypeHint.startsWith("image/")) mimeType = mimeTypeHint;
        if (mimeType == null || !mimeType.startsWith("image/")) {
            throw new IOException("Unable to determine source image format");
        }
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (extension == null || extension.isEmpty()) {
            throw new IOException("Unsupported source image format");
        }
        String name = timestampName(System.currentTimeMillis(), extension);
        String sourceMimeType = mimeType;
        return save(context, name, sourceMimeType, output -> {
            try (InputStream input = resolver.openInputStream(source)) {
                if (input == null) throw new IOException("Unable to open source image");
                copy(input, output);
            }
        });
    }

    static String timestampName(long timestampMillis) {
        return timestampName(timestampMillis, "png");
    }

    static String timestampName(long timestampMillis, String extension) {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date(timestampMillis)) + "." + extension;
    }

    static void writePng(Bitmap bitmap, OutputStream output) throws IOException {
        BitmapEncoder.writePng(bitmap, output);
    }

    private static Uri save(Context context, String displayName, String mimeType,
            OutputWriter writer) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveWithMediaStore(context, displayName, mimeType, writer);
        }
        return saveLegacy(context, displayName, mimeType, writer);
    }

    private static Uri saveWithMediaStore(
            Context context,
            String displayName,
            String mimeType,
            OutputWriter writer) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
        values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + DIRECTORY_NAME);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("MediaStore insert returned null");
        }
        try {
            try (OutputStream output = resolver.openOutputStream(uri)) {
                if (output == null) {
                    throw new IOException("Unable to open destination image");
                }
                writer.write(output);
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, ready, null, null);
            return uri;
        } catch (Throwable error) {
            try {
                resolver.delete(uri, null, null);
            } catch (Throwable ignored) {
                // Preserve the original encoding or storage error.
            }
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("Unable to save image", error);
        }
    }

    @SuppressWarnings("deprecation")
    private static Uri saveLegacy(
            Context context,
            String displayName,
            String mimeType,
            OutputWriter writer) throws IOException {
        File directory = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                DIRECTORY_NAME);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create Pictures directory");
        }
        File destination = uniqueFile(directory, displayName);
        try (FileOutputStream output = new FileOutputStream(destination)) {
            writer.write(output);
            output.getFD().sync();
        } catch (Throwable error) {
            destination.delete();
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("Unable to save image", error);
        }
        MediaScannerConnection.scanFile(
                context,
                new String[]{destination.getAbsolutePath()},
                new String[]{mimeType},
                null);
        return Uri.fromFile(destination);
    }

    static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }

    private static File uniqueFile(File directory, String displayName) {
        File destination = new File(directory, displayName);
        if (!destination.exists()) {
            return destination;
        }
        int extension = displayName.lastIndexOf('.');
        String stem = extension < 0 ? displayName : displayName.substring(0, extension);
        String suffix = extension < 0 ? "" : displayName.substring(extension);
        int index = 1;
        do {
            destination = new File(directory, stem + "_" + index + suffix);
            index++;
        } while (destination.exists());
        return destination;
    }

    private interface OutputWriter {
        void write(OutputStream output) throws IOException;
    }
}
