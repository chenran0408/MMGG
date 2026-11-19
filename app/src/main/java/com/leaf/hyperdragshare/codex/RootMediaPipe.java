package com.leaf.hyperdragshare.codex;

import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import org.json.JSONObject;

/** Bounded base64 frames carry original media outside JSON strings and Binder byte arrays. */
final class RootMediaPipe {
    private static final int CHUNK = 48 * 1024;

    private RootMediaPipe() {}

    static int write(InputStream input, PrintStream output) throws Exception {
        byte[] buffer = new byte[CHUNK];
        int total = 0, count;
        while ((count = input.read(buffer)) != -1) {
            if ((long) total + count > EncodedMedia.MAX_BYTES) throw new IOException("媒体文件超过32MB");
            byte[] frame = java.util.Arrays.copyOf(buffer, count);
            output.println(new JSONObject().put("media_chunk",
                    Base64.encodeToString(frame, Base64.NO_WRAP)));
            output.flush();
            total += count;
        }
        return total;
    }

    static void append(ByteArrayOutputStream output, String encoded) throws IOException {
        byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
        if ((long) output.size() + bytes.length > EncodedMedia.MAX_BYTES) {
            throw new IOException("Root媒体响应超过32MB");
        }
        output.write(bytes);
    }
}
