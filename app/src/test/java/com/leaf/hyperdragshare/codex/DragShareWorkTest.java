package com.leaf.hyperdragshare.codex;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, shadows = {DragShareWorkTest.StagingIo.class,
        DragShareWorkTest.ResolveResources.class, DragShareWorkTest.Clipboard.class},
        instrumentedPackages = "com.leaf.hyperdragshare.codex")
@LooperMode(LooperMode.Mode.PAUSED)
public class DragShareWorkTest {
    private static final byte[] GIF = Base64.getDecoder().decode(
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void cancelledQueueDoesNoImageIoBeforeTheChosenOriginal() throws Exception {
        StagingIo.encodes = 0;
        StagingIo.writes.clear();
        Clipboard.received.clear();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        Field worker = ImageStagingClient.class.getDeclaredField("EXECUTOR");
        worker.setAccessible(true);
        ExecutorService queue = (ExecutorService) worker.get(null);
        CountDownLatch release = new CountDownLatch(1);
        queue.submit(() -> { release.await(); return null; });
        try {
            for (int i = 0; i < 6; i++) {
                start(controller, i % 2 == 0 ? preview() : original(), 200L + i);
                invoke(controller, "cancelGestureOnMain", new Class<?>[0]);
                ShadowLooper.idleMainLooper();
            }
            start(controller, original(), 300L);
            set(controller, "selectedTarget", ShareTarget.copyImageToClipboard(null));
            invoke(controller, "finishGestureOnMain", new Class<?>[]{boolean.class}, true);
            long start = System.nanoTime();
            release.countDown();
            queue.submit(() -> {}).get(5, TimeUnit.SECONDS);
            ShadowLooper.idleMainLooper();

            System.out.println("queue fixture: encodes=" + StagingIo.encodes
                    + " writes=" + StagingIo.writes.size()
                    + " bytes=" + StagingIo.writes.stream().mapToInt(bytes -> bytes.length).sum()
                    + " queueToActionUs=" + TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start));
            assertEquals(1, Clipboard.received.size());
            assertEquals(0, StagingIo.encodes);
            assertEquals(1, StagingIo.writes.size());
            assertArrayEquals(GIF, StagingIo.writes.get(0));
        } finally {
            release.countDown();
            controller.destroy();
        }
    }

    @Test public void hiddenShareTargetsNeverReadResourcesButRemainAvailableInSettings() throws Exception {
        ShadowPackageManager packages = Shadow.extract(context.getPackageManager());
        List<String> hidden = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String pkg = "fixture.share.app" + i;
            ComponentName component = new ComponentName(pkg, pkg + ".Share");
            ActivityInfo activity = packages.addActivityIfNotPresent(component);
            activity.enabled = activity.exported = activity.applicationInfo.enabled = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_SEND);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            filter.addDataType("text/plain");
            packages.addIntentFilterForActivity(component, filter);
            if (i < 10) hidden.add(component.flattenToString());
        }
        Bundle settings = DragShareSettings.defaults().toBundle();
        settings.putStringArrayList("hidden_targets", new ArrayList<>(hidden));
        DragShareSettings menuSettings = DragShareSettings.fromBundle(settings);
        CapturedContent payload = CapturedContent.text("test", "source", null);
        for (int pass = 0; pass < 2; pass++) {
            ResolveResources.icons = ResolveResources.labels = 0;
            long start = System.nanoTime();
            List<ShareTarget> menu = ShareTargetRepository.queryMenu(context, payload, menuSettings);
            long micros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);
            System.out.println("menu fixture: pass=" + pass + " labels=" + ResolveResources.labels
                    + " icons=" + ResolveResources.icons + " queryUs=" + micros);
            assertEquals(2, menu.stream()
                    .filter(item -> item.packageName().startsWith("fixture.share.")).count());
            assertTrue(menu.stream().anyMatch(ShareTarget::isCopyTextToClipboard));
            assertEquals(2, ResolveResources.icons);
            assertEquals(2, ResolveResources.labels);
        }
        ResolveResources.icons = ResolveResources.labels = 0;
        assertEquals(12, ShareTargetRepository.queryAll(context).stream()
                .filter(item -> item.packageName().startsWith("fixture.share.")).count());
        assertEquals(12, ResolveResources.icons);
        assertEquals(12, ResolveResources.labels);
    }

    private static CapturedContent preview() {
        return CapturedContent.image(Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888),
                "source", null, true);
    }

    private static CapturedContent original() throws java.io.IOException {
        return CapturedContent.referencedMediaImage(EncodedMedia.decode(GIF), preview().bitmap, "source", null);
    }

    private static void start(DragShareController controller, CapturedContent payload, long down) throws Exception {
        set(controller, "rootDownTime", down);
        invoke(controller, "showOnMain", new Class<?>[]{CapturedContent.class, float.class, float.class,
                DragShareSettings.class, long.class}, payload, 120f, 240f, DragShareSettings.defaults(), down);
        ShadowLooper.idleMainLooper();
        assertTrue(controller.isActive());
    }

    private static void set(Object instance, String fieldName, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static Object invoke(Object instance, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = instance.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(instance, args);
    }

    @Implements(value = ImageStagingClient.class, isInAndroidSdk = false)
    public static class StagingIo {
        static int encodes;
        static final List<byte[]> writes = new ArrayList<>();

        @Implementation protected static Uri stagePng(Context context, Bitmap bitmap) throws Exception {
            encodes++;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BitmapEncoder.writePng(bitmap, out);
            return record(out.toByteArray());
        }
        @Implementation protected static Uri stageEncoded(Context context, byte[] bytes) {
            return record(bytes);
        }
        private static Uri record(byte[] bytes) {
            writes.add(Arrays.copyOf(bytes, bytes.length));
            return Uri.parse("content://fixture/" + writes.size());
        }
    }

    @Implements(ResolveInfo.class)
    public static class ResolveResources {
        @RealObject ResolveInfo info;
        static int labels, icons;
        @Implementation protected CharSequence loadLabel(PackageManager pm) {
            if (info.activityInfo.packageName.startsWith("fixture.share.")) labels++;
            return info.activityInfo.packageName;
        }
        @Implementation protected Drawable loadIcon(PackageManager pm) {
            if (info.activityInfo.packageName.startsWith("fixture.share.")) icons++;
            return null;
        }
    }

    @Implements(value = ClipboardWriter.class, isInAndroidSdk = false)
    public static class Clipboard {
        static final List<Uri> received = new ArrayList<>();
        @Implementation protected static void copyImage(Context app, Uri image, ClipboardWriter.Callback callback) {
            received.add(image);
            callback.onComplete(null);
        }
    }
}
