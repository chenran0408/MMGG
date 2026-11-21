package com.leaf.hyperdragshare.codex;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Encoded source and display preview have independent lifetimes and formats. */
final class EncodedMedia {
    static final int MAX_BYTES = 32 * 1024 * 1024; // Same limit as ShareImageProvider.
    final byte[] bytes;
    final Bitmap preview;
    final String mimeType;

    private EncodedMedia(byte[] bytes, Bitmap preview, String mimeType) {
        this.bytes = bytes;
        this.preview = preview;
        this.mimeType = mimeType;
    }

    static EncodedMedia decode(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("媒体文件大小超出限制");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        String mimeType = options.outMimeType;
        if (mimeType == null || !mimeType.startsWith("image/")) mimeType = sniffMime(bytes);
        if (mimeType == null || !mimeType.startsWith("image/")) throw new IOException("媒体来源不是可读取的图片");
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2048) {
            options.inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        Bitmap preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        return new EncodedMedia(bytes, preview, mimeType);
    }

    private static String sniffMime(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte)0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte)0xff && bytes[1] == (byte)0xd8
                && bytes[2] == (byte)0xff) return "image/jpeg";
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') {
            return "image/gif";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        return null;
    }

    static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[48 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_BYTES) throw new IOException("媒体文件超过32MB");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
