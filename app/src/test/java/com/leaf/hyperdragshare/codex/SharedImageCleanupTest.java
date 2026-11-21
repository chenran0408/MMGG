package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35, application = android.app.Application.class)
public final class SharedImageCleanupTest {
    @Test public void expiresOnlyOldPrivateImagesAndLeavesStashAndOtherCaches() throws Exception {
        java.util.Set<Uri> revoked = new java.util.HashSet<>();
        Context app = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public void revokeUriPermission(Uri uri, int flags) {
                assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, flags);
                revoked.add(uri);
            }
        };
        File oldShare = sample(app.getCacheDir(), "shared-images", ".png", true);
        File freshShare = sample(app.getCacheDir(), "shared-images", ".jpg", false);
        File oldInput = sample(app.getCacheDir(), "ocr-input", ".image", true);
        File oldPartial = sample(app.getCacheDir(), "shared-images", ".tmp", true);
        File stash = sample(app.getFilesDir(), "stash", ".png", true);
        File otherCache = sample(app.getCacheDir(), "unrelated", ".png", true);
        File nested = new File(oldShare.getParentFile(), "nested");
        assertTrue(nested.mkdir());
        assertTrue(nested.setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25)));

        assertEquals(3, SharedImageCleanupJob.cleanup(app));
        assertFalse(oldShare.exists());
        assertFalse(oldInput.exists());
        assertFalse(oldPartial.exists());
        assertTrue(revoked.contains(ImageStagingClient.BASE_URI.buildUpon()
                .appendPath("shared").appendPath(oldShare.getName()).build()));
        assertFalse(revoked.contains(ImageStagingClient.BASE_URI.buildUpon()
                .appendPath("shared").appendPath(freshShare.getName()).build()));
        assertTrue(freshShare.isFile());
        assertTrue(stash.isFile());
        assertTrue(otherCache.isFile());
        assertTrue(nested.isDirectory());
    }

    @Test public void unreadableCacheIsReportedWithoutDeletingIt() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File invalidDirectory = new File(app.getCacheDir(), "ocr-input");
        Files.writeString(invalidDirectory.toPath(), "preserve invalid cache entry");
        SharedImageCleanupJob.runSweep(app, false);
        assertEquals("preserve invalid cache entry", Files.readString(invalidDirectory.toPath()));
        assertTrue(app.getSharedPreferences(SharedImageCleanupJob.PREFS, Context.MODE_PRIVATE)
                .getString(SharedImageCleanupJob.LAST_ERROR, "").contains("暂存图片未能清理"));
    }

    @Test public void appRestartOnlyCatchesUpAnOverdueSweep() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File before = sample(app.getCacheDir(), "shared-images", ".png", true);
        SharedImageCleanupJob.runSweep(app, true);
        assertFalse(before.exists());
        File after = sample(app.getCacheDir(), "shared-images", ".png", true);
        SharedImageCleanupJob.runSweep(app, true);
        assertTrue("recent successful sweep skips the directory scan", after.exists());
        SharedImageCleanupJob.runSweep(app, false);
        assertFalse(after.exists());
    }

    private static File sample(File parent, String directory, String suffix, boolean old) throws Exception {
        File folder = new File(parent, directory);
        assertTrue(folder.isDirectory() || folder.mkdirs());
        File file = new File(folder, UUID.randomUUID() + suffix);
        Files.writeString(file.toPath(), "generated cleanup sample");
        if (old) assertTrue(file.setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25)));
        return file;
    }
}
