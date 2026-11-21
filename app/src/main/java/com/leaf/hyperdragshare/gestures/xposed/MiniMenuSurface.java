package com.leaf.hyperdragshare.gestures.xposed;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Constructor;

/** Mounts the menu in task coordinates so the ROM moves, scales and clips both surfaces together. */
final class MiniMenuSurface {
    private MiniMenuSurface() {}

    static Runnable mount(Context context, Display display, Configuration config,
                          SurfaceControl leash, View root, int width, int height)
            throws ReflectiveOperationException {
        SurfaceControl.Builder builder = new SurfaceControl.Builder().setName("MMGG mini menu");
        builder.getClass().getMethod("setContainerLayer").invoke(builder);
        builder.getClass().getMethod("setParent", SurfaceControl.class).invoke(builder, leash);
        SurfaceControl surface = builder.build();
        SurfaceControlViewHost host = null;
        try {
            Object manager = windowlessManager(config, surface);
            host = viewHost(context, display, manager);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height,
                    WindowManager.LayoutParams.TYPE_APPLICATION,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.setTitle("MMGG-Mini-Menu");
            try {
                WindowManager.LayoutParams.class.getMethod("setTrustedOverlay").invoke(params);
            } catch (NoSuchMethodException oldRom) {
                // Older windowless hosts only expose the corresponding surface transaction.
            }
            SurfaceControlViewHost.class.getMethod("setView", View.class, WindowManager.LayoutParams.class)
                    .invoke(host, root, params);
            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                SurfaceControl.Transaction.class.getMethod("setTrustedOverlay", SurfaceControl.class,
                        boolean.class).invoke(transaction, surface, true);
                SurfaceControl.Transaction.class.getMethod("setLayer", SurfaceControl.class, int.class)
                        .invoke(transaction, surface, 1000);
                SurfaceControl.Transaction.class.getMethod("show", SurfaceControl.class)
                        .invoke(transaction, surface);
                transaction.apply();
            }
            SurfaceControlViewHost mounted = host;
            return () -> release(mounted, surface);
        } catch (ReflectiveOperationException | RuntimeException error) {
            release(host, surface);
            throw error;
        }
    }

    private static Object windowlessManager(Configuration config, SurfaceControl surface)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName("android.view.WindowlessWindowManager");
        for (Constructor<?> ctor : type.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 3 && params[0] == Configuration.class
                    && params[1] == SurfaceControl.class && !params[2].isPrimitive()) {
                return ctor.newInstance(config, surface, null);
            }
        }
        return type.getConstructor(Configuration.class, SurfaceControl.class).newInstance(config, surface);
    }

    private static SurfaceControlViewHost viewHost(Context context, Display display, Object manager)
            throws ReflectiveOperationException {
        Class<?> type = SurfaceControlViewHost.class;
        Class<?> wm = manager.getClass();
        try {
            return (SurfaceControlViewHost) type.getConstructor(Context.class, Display.class, wm,
                    String.class).newInstance(context, display, manager, "MMGG-Mini-Menu");
        } catch (NoSuchMethodException oldRom) {
            return (SurfaceControlViewHost) type.getConstructor(Context.class, Display.class, wm)
                    .newInstance(context, display, manager);
        }
    }

    private static void release(SurfaceControlViewHost host, SurfaceControl surface) {
        try {
            if (host != null) host.release();
        } finally {
            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                SurfaceControl.Transaction.class.getMethod("remove", SurfaceControl.class)
                        .invoke(transaction, surface);
                transaction.apply();
            } catch (ReflectiveOperationException | RuntimeException error) {
                Log.e("Cannot remove mini menu surface", error);
            } finally {
                surface.release();
            }
        }
    }
}
