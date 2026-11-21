package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import org.junit.Test;

public final class SidebarHostContractTest {
    @Test public void acceptsRenamedAnimationAndSceneTypes() throws Exception {
        SidebarHostContract original = SidebarHostContract.verify(
                OriginalTurbo.class, OriginalWrapper.class, DockSize.class, SidebarService.class);
        SidebarHostContract renamed = SidebarHostContract.verify(
                RenamedTurbo.class, RenamedWrapper.class, DockSize.class, SidebarService.class);

        assertEquals(OriginalWrapper.class.getDeclaredMethod("W", boolean.class), original.open());
        assertEquals(OriginalTurbo.class.getDeclaredMethod("e0"), original.search());
        assertEquals(RenamedWrapper.class.getDeclaredMethod("W", boolean.class), renamed.open());
        assertEquals(RenamedTurbo.class.getDeclaredMethod("e0"), renamed.search());
        assertEquals(DockSize.class.getDeclaredMethod("c"), renamed.measure());
        assertEquals(SidebarService.class.getDeclaredMethod("onDestroy"), renamed.destroy());
    }

    @Test public void rejectsChangedDockMeasurementReturnType() {
        NoSuchMethodException error = assertThrows(NoSuchMethodException.class,
                () -> SidebarHostContract.verify(
                        RenamedTurbo.class, RenamedWrapper.class, IncompatibleDockSize.class, SidebarService.class));

        assertEquals(IncompatibleDockSize.class.getName() + ".c must be static and return an int", error.getMessage());
    }

    private static final class OriginalTurbo extends RelativeLayout {
        OriginalScene j;
        boolean s;

        OriginalTurbo() { super(null); }
        View getAppsLayout() { return null; }
        View getBoxView() { return null; }
        Dock getDockLayout() { return null; }
        OriginalWrapper getSidebarWrapper() { return null; }
        boolean D() { return false; }
        boolean F() { return false; }
        boolean G() { return false; }
        void e0() {}
    }

    private static final class RenamedTurbo extends RelativeLayout {
        RenamedScene j;
        boolean s;

        RenamedTurbo() { super(null); }
        View getAppsLayout() { return null; }
        View getBoxView() { return null; }
        Dock getDockLayout() { return null; }
        RenamedWrapper getSidebarWrapper() { return null; }
        boolean D() { return false; }
        boolean F() { return false; }
        boolean G() { return false; }
        void e0() {}
    }

    private static final class OriginalWrapper {
        FrameLayout B() { return null; }
        OriginalTurbo C() { return null; }
        boolean H() { return false; }
        void W(boolean visible) {}
        OriginalAnimation o() { return null; }
        View w() { return null; }
    }

    private static final class RenamedWrapper {
        FrameLayout B() { return null; }
        RenamedTurbo C() { return null; }
        boolean H() { return false; }
        void W(boolean visible) {}
        RenamedAnimation o() { return null; }
        View w() { return null; }
    }

    private static final class OriginalAnimation {
        void O0(boolean visible) {}
    }

    private static final class RenamedAnimation {
        void O0(boolean visible) {}
    }

    private static final class OriginalScene {
        int c() { return 0; }
    }

    private static final class RenamedScene {
        int c() { return 0; }
    }

    private static final class Dock extends FrameLayout {
        Dock() { super(null); }
        void J() {}
    }

    private static final class DockSize {
        static int c() { return 0; }
    }

    private static final class IncompatibleDockSize {
        static long c() { return 0; }
    }

    private static final class SidebarService extends Service {
        @Override public IBinder onBind(Intent intent) { return null; }
        @Override public void onDestroy() {}
    }
}
