package com.leaf.hyperdragshare.codex;

import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Explicit, read-only HyperOS View query from the existing Root helper. No automation lease. */
final class SystemUiAgent {
    static final String CAPTURE_TEXT = "mmgg_capture_text";
    static final String TOUCH_X = "mmgg_touch_x";
    static final String TOUCH_Y = "mmgg_touch_y";
    static final String CAPTURE_MEDIA = "mmgg_capture_media";
    static final String MEDIA_BOUNDS = "mmgg_media_bounds";
    static final String MEDIA_STREAM = "mmgg_media_stream";
    static final String MEDIA_REFERENCE = "mmgg_media_reference";
    static final String REQUEST_ID = "mmgg_request_id";
    private static final String CALLBACK = "miui.security.IUIAgentCallback";
    private static final long WAIT_MS = 350L;

    private SystemUiAgent() {}

    static List<AccessibilityNodeSnapshot> readViews(String source, String activity, int userId,
            float x, float y, boolean allowText)
            throws Exception {
        Bundle result = query(source, activity, userId, x, y, allowText, null);
        try {
            ParcelFileDescriptor descriptor = result.getParcelable("content", ParcelFileDescriptor.class);
            if (descriptor == null) throw new IOException("UIAgent returned no View data: "
                    + result.getString("reason"));
            result.remove("content");
            try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
                 Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return parseViews(reader, source);
            }
        } finally { closeResult(result); }
    }

    static Media readMedia(String source, String activity, int userId, float x, float y, Rect bounds)
            throws Exception {
        Bundle result = query(source, activity, userId, x, y, false, bounds);
        try {
            if (!bounds.equals(result.getParcelable(MEDIA_BOUNDS, Rect.class))) return null;
            ParcelFileDescriptor stream = result.getParcelable(MEDIA_STREAM, ParcelFileDescriptor.class);
            String reference = result.getString(MEDIA_REFERENCE);
            if (stream == null && reference == null) return null;
            result.remove(MEDIA_STREAM);
            return new Media(stream, reference);
        } finally { closeResult(result); }
    }

    static final class Media implements AutoCloseable {
        final ParcelFileDescriptor descriptor;
        final String reference;
        private boolean streamOwnsDescriptor;
        Media(ParcelFileDescriptor descriptor, String reference) {
            this.descriptor = descriptor; this.reference = reference;
        }
        InputStream open() throws IOException {
            if (descriptor == null) return PortalMediaLoader.openNetwork(reference);
            streamOwnsDescriptor = true;
            return new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
        }
        @Override public void close() throws IOException {
            if (descriptor != null && !streamOwnsDescriptor) descriptor.close();
        }
    }

    private static Bundle query(String source, String activity, int userId, float x, float y,
            boolean allowText, Rect mediaBounds) throws Exception {
        Method getService = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class);
        IBinder pmBinder = (IBinder) getService.invoke(null, "package");
        Object pm = Class.forName("android.content.pm.IPackageManager$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, pmBinder);
        int uid = (Integer) Class.forName("android.content.pm.IPackageManager")
                .getMethod("getPackageUid", String.class, long.class, int.class)
                .invoke(pm, source, 0L, userId);
        String token = source + "~" + uid;
        IBinder securityBinder = (IBinder) getService.invoke(null, "security");
        Object security = Class.forName("miui.security.ISecurityManager$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, securityBinder);
        Class<?> callbackType = Class.forName(CALLBACK);
        Reply reply = new Reply();
        Object callback = Proxy.newProxyInstance(callbackType.getClassLoader(),
                new Class<?>[]{callbackType}, (proxy, method, args) ->
                        "asBinder".equals(method.getName()) ? reply : null);
        Bundle request = new Bundle();
        request.putInt("uiAgentType", 0); // Read only; never invoke the control/injection operation.
        request.putInt("catch_way", 1); // Raw Views; the accessibility XML is filtered in WeChat.
        request.putStringArray("packageNames", new String[]{token});
        request.putString("TargetActivity", activity);
        request.putInt("displayId", 0);
        request.putInt("max_process_time", 100);
        request.putInt("max_depth", 40);
        request.putBoolean(CAPTURE_TEXT, allowText);
        request.putFloat(TOUCH_X, x);
        request.putFloat(TOUCH_Y, y);
        String requestId = java.util.UUID.randomUUID().toString();
        request.putString(REQUEST_ID, requestId);
        if (mediaBounds != null) {
            request.putBoolean(CAPTURE_MEDIA, true);
            request.putParcelable(MEDIA_BOUNDS, new Rect(mediaBounds));
        }
        try {
            Class.forName("miui.security.ISecurityManager")
                    .getMethod("onUIAgentEvent", Bundle.class, callbackType)
                    .invoke(security, request, callback);
            Bundle result = reply.await();
            if (result == null) throw new IOException("UIAgent response timed out");
            if (!matches(result, token, requestId)) {
                throw new IOException("UIAgent returned a different source/request or rejected the request");
            }
            return result;
        } finally {
            reply.close();
        }
    }

    static boolean matches(Bundle result, String token, String requestId) {
        return result.getInt("code", -1) == 0 && token.equals(result.getString("token"))
                && requestId.equals(result.getString(REQUEST_ID));
    }

    static void closeDescriptor(Bundle value, String key) {
        if (value == null) return;
        ParcelFileDescriptor descriptor = value.getParcelable(key, ParcelFileDescriptor.class);
        if (descriptor == null) return;
        try { descriptor.close(); }
        catch (IOException error) { System.err.println("UIAgent descriptor close failed: " + error); }
    }

    static void closeResult(Bundle value) {
        closeDescriptor(value, "content");
        closeDescriptor(value, MEDIA_STREAM);
    }

    static List<AccessibilityNodeSnapshot> parseViews(Reader reader, String source) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(reader);
        List<AccessibilityNodeSnapshot> nodes = new ArrayList<>();
        List<Boolean> protectedParents = new ArrayList<>();
        boolean protectedNode = false;
        int depth = 0;
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == XmlPullParser.START_TAG && "node".equals(parser.getName())) {
                protectedParents.add(protectedNode);
                protectedNode |= Boolean.parseBoolean(parser.getAttributeValue(null, "password"));
                String className = parser.getAttributeValue(null, "class");
                String encodedBounds = parser.getAttributeValue(null, "bounds");
                // ROM raw-View bounds are Rect.toShortString(), not the accessibility XML format.
                Rect bounds = parseBounds(encodedBounds);
                if (bounds != null && !bounds.isEmpty()) {
                    nodes.add(new AccessibilityNodeSnapshot.Builder().packageName(source)
                            .className(className).viewId(parser.getAttributeValue(null, "resource-id"))
                            .text(protectedNode ? null : parser.getAttributeValue(null, "text"))
                            .contentDescription(protectedNode ? null : parser.getAttributeValue(null, "content-desc"))
                            .password(protectedNode).bounds(bounds).depth(depth)
                            .editable(className != null && className.contains("EditText"))
                            .rangeControl(className != null && (className.contains("SeekBar") || className.contains("Slider")))
                            .enabled(!"false".equals(parser.getAttributeValue(null, "enabled")))
                            .clickable(Boolean.parseBoolean(parser.getAttributeValue(null, "clickable")))
                            .longClickable(Boolean.parseBoolean(parser.getAttributeValue(null, "long-clickable")))
                            .leaf(false).traversalOrder(nodes.size()).build());
                }
                depth++;
            } else if (event == XmlPullParser.END_TAG && "node".equals(parser.getName())) {
                depth--;
                protectedNode = protectedParents.remove(protectedParents.size() - 1);
            }
        }
        return nodes;
    }

    static Rect parseBounds(String value) {
        if (value == null) return null;
        String[] coordinates = value.replace('[', ' ').replace(']', ' ').trim().split("\\s*[, ]\\s*");
        if (coordinates.length != 4) return null;
        return new Rect(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]),
                Integer.parseInt(coordinates[2]), Integer.parseInt(coordinates[3]));
    }

    private static final class Reply extends Binder {
        private final CountDownLatch ready = new CountDownLatch(1);
        private Bundle result;
        private boolean closed;

        @Override protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code != 1) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(CALLBACK);
            Bundle received = data.readTypedObject(Bundle.CREATOR);
            if (closed || result != null) closeResult(received);
            else { result = received; ready.countDown(); }
            return true;
        }

        Bundle await() throws InterruptedException {
            if (!ready.await(WAIT_MS, TimeUnit.MILLISECONDS)) return null;
            synchronized (this) { Bundle received = result; result = null; closed = true; return received; }
        }

        synchronized void close() {
            closed = true;
            closeResult(result);
            result = null;
        }

    }
}
