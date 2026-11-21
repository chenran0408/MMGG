package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import com.leaf.hyperdragshare.compat.xposed.XC_MethodHook;
import com.leaf.hyperdragshare.compat.xposed.XposedHelpers;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** Adds media references to the existing read-only UIAgent reply, without changing native gestures. */
final class ContentCatcherMediaHooks {
    private static final String TAG = "DragShare/NativeMedia";
    private static final String CANDIDATE = "mmgg_media_candidate";
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static Context application;

    private ContentCatcherMediaHooks() {}

    static void install(ClassLoader loader) {
        // Actual 5.1.5 DEX signatures: XML completion runs on the catcher's worker Handler.
        XposedHelpers.findAndHookMethod("z.k", loader, "c", String.class, Bundle.class,
                String.class, int.class, "miui.security.IUIAgentCallback", replyObserver());
        XposedHelpers.findAndHookMethod("z.k", loader, "a", String.class, String.class,
                Bundle.class, "miui.security.IUIAgentCallback", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        discard((Bundle) param.args[2]);
                    }
                });
    }

    static XC_MethodHook replyObserver() {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Bundle args = (Bundle) param.args[1];
                if (args == null || !args.containsKey(CANDIDATE)) return;
                try { openCandidate(args); }
                catch (IOException | SecurityException error) {
                    discard(args);
                    DragShareLog.w(TAG, "media source open failed error=" + error);
                }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                discard((Bundle) param.args[1]);
            }
        };
    }

    static boolean collect(Object record, View view, Object fetcher, Bundle args) {
        if (!matchesMediaRequest(args, null) || args.containsKey(CANDIDATE)) return false;
        List<?> names = (List<?>) XposedHelpers.getObjectField(record, "b");
        List<?> values = (List<?>) XposedHelpers.getObjectField(record, "c");
        int index = names.indexOf("bounds");
        Rect expected = args.getParcelable(SystemUiAgent.MEDIA_BOUNDS, Rect.class);
        if (index < 0 || !matchesMediaRequest(args,
                SystemUiAgent.parseBounds((String) values.get(index)))) return false;
        if (!(view instanceof ImageView)
                && !"android.widget.ImageView".contentEquals(view.getAccessibilityClassName())) return false;
        // Password state can be inherited from a container even when the image itself is public.
        for (View current = view; current != null;) {
            AccessibilityNodeInfo info = current.createAccessibilityNodeInfo();
            try { if (info != null && info.isPassword()) return false; }
            finally { if (info != null) info.recycle(); }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View)parent : null;
        }
        // flags=3 can discard reflection-mapped media fields; use the verified image-only branch.
        Bundle content = (Bundle) XposedHelpers.callMethod(fetcher, "fetchViewContent", view, 2, false);
        Object source = mappedSource(content);
        if (source == null && view instanceof ImageView) {
            source = XposedHelpers.getObjectField(view, "mUri");
        }
        if (source == null) source = glideModel(view);
        if (source == null) return false;
        Bundle candidate = new Bundle();
        try {
            if (source instanceof ParcelFileDescriptor) {
                candidate.putParcelable("descriptor", ((ParcelFileDescriptor) source).dup());
            } else {
                Uri uri = asUri(source);
                if (uri == null) return false;
                candidate.putParcelable("uri", uri);
            }
        } catch (IOException error) {
            DragShareLog.w(TAG, "media descriptor duplication failed error=" + error);
            return false;
        }
        Context appContext = view.getContext().getApplicationContext();
        application = appContext == null ? view.getContext() : appContext;
        args.putBundle(CANDIDATE, candidate);
        DragShareLog.i(TAG, "media reference found type=" + source.getClass().getSimpleName()
                + " bounds=" + expected);
        return true;
    }

    static boolean matchesMediaRequest(Bundle args, Rect bounds) {
        if (!ContentCatcherNativeTextHooks.isCaptureRequest(args)
                || !args.getBoolean(SystemUiAgent.CAPTURE_MEDIA)) return false;
        Rect requested = args.getParcelable(SystemUiAgent.MEDIA_BOUNDS, Rect.class);
        return requested != null && (bounds == null || requested.equals(bounds))
                && requested.contains((int)args.getFloat(SystemUiAgent.TOUCH_X),
                        (int)args.getFloat(SystemUiAgent.TOUCH_Y));
    }

    static Object mappedSource(Bundle content) {
        if (content == null) return null;
        // Only fields declared by the native reflection map are treated as source fields.
        String[] mapped = content.getStringArray("apkParamList");
        if (mapped != null) for (String field : mapped) {
            Object value = content.get(field);
            if (value instanceof ParcelFileDescriptor || asUri(value) != null) return value;
        }
        Object url = content.get("imgUrl");
        return asUri(url) == null ? null : url;
    }

    static Uri asUri(Object source) {
        Uri uri;
        if (source instanceof Uri) uri = (Uri)source;
        else if (source instanceof File) uri = Uri.fromFile((File)source);
        else if (source instanceof CharSequence) {
            String value = source.toString();
            uri = value.startsWith("/") && !value.startsWith("//")
                    ? Uri.fromFile(new File(value)) : Uri.parse(value.startsWith("//") ? "https:" + value : value);
        } else return null;
        String scheme = uri.getScheme();
        return "content".equals(scheme) || "file".equals(scheme)
                || (("https".equals(scheme) || "http".equals(scheme)) && uri.getHost() != null) ? uri : null;
    }

    private static Object glideModel(View view) {
        // Verified Glide ViewTarget.tagId and SingleRequest.model; no recursive object/cache scan.
        ClassLoader loader = view.getContext().getClassLoader();
        Class<?> target = XposedHelpers.findClassIfExists("com.bumptech.glide.request.target.ViewTarget", loader);
        if (target == null) return null;
        int tag = (Integer) XposedHelpers.getStaticObjectField(target, "tagId");
        Object request = view.getTag(tag);
        return request != null && "com.bumptech.glide.request.SingleRequest".equals(request.getClass().getName())
                ? XposedHelpers.getObjectField(request, "model") : null;
    }

    static void openCandidate(Bundle args) throws IOException {
        Bundle candidate = args.getBundle(CANDIDATE);
        if (candidate == null) return;
        ParcelFileDescriptor descriptor = candidate.getParcelable("descriptor", ParcelFileDescriptor.class);
        if (descriptor != null) candidate.remove("descriptor");
        else {
            Uri uri = candidate.getParcelable("uri", Uri.class);
            if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                descriptor = pipe[0];
                Thread writer = new Thread(() -> copyNetwork(uri.toString(), pipe[1]),
                        "content-catcher-media");
                writer.setDaemon(true);
                writer.start();
            } else {
                descriptor = "file".equals(uri.getScheme())
                        ? ParcelFileDescriptor.open(new File(uri.getPath()), ParcelFileDescriptor.MODE_READ_ONLY)
                        : application.getContentResolver().openFileDescriptor(uri, "r");
            }
        }
        args.remove(CANDIDATE);
        if (descriptor != null) args.putParcelable(SystemUiAgent.MEDIA_STREAM, descriptor);
    }

    private static void copyNetwork(String reference, ParcelFileDescriptor target) {
        try (java.io.InputStream input = PortalMediaLoader.openNetwork(reference);
             java.io.OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(target)) {
            byte[] buffer = new byte[48 * 1024];
            int count, total = 0;
            while ((count = input.read(buffer)) != -1) {
                if ((long) total + count > EncodedMedia.MAX_BYTES) throw new IOException("媒体文件超过32MB");
                output.write(buffer, 0, count);
                total += count;
            }
        } catch (IOException error) {
            DragShareLog.w(TAG, "network media reference read failed error=" + error.getClass().getSimpleName());
            try { target.close(); } catch (IOException ignored) { }
        }
    }

    static void discard(Bundle args) {
        if (args == null) return;
        Bundle candidate = args.getBundle(CANDIDATE);
        if (candidate != null) SystemUiAgent.closeDescriptor(candidate, "descriptor");
        args.remove(CANDIDATE);
        SystemUiAgent.closeDescriptor(args, SystemUiAgent.MEDIA_STREAM);
        args.remove(SystemUiAgent.MEDIA_STREAM);
        args.remove(SystemUiAgent.MEDIA_REFERENCE);
    }
}
