package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ContentCatcherMediaHooksTest {
    @Test public void readsOnlyDeclaredReflectionMappedMediaFields() {
        Bundle result = new Bundle();
        result.putStringArray("apkParamList", new String[]{"label", "source"});
        result.putString("label", "message text");
        result.putParcelable("source", Uri.parse("content://media/images/7"));
        assertEquals(Uri.parse("content://media/images/7"),
                ContentCatcherMediaHooks.asUri(ContentCatcherMediaHooks.mappedSource(result)));

        result.remove("source");
        result.putString("imgUrl", "https://images.example.test/picture.jpg");
        assertEquals("https://images.example.test/picture.jpg",
                ContentCatcherMediaHooks.mappedSource(result));
    }

    @Test public void acceptsOnlyReadableImageReferenceSchemes() {
        Uri path = ContentCatcherMediaHooks.asUri("/data/image.webp");
        assertEquals("file", path.getScheme());
        assertTrue(path.getPath().endsWith("image.webp"));
        assertEquals(Uri.parse("https://images.example.test/picture.jpg"),
                ContentCatcherMediaHooks.asUri("https://images.example.test/picture.jpg"));
        assertNull(ContentCatcherMediaHooks.asUri("javascript:alert(1)"));
        assertNull(ContentCatcherMediaHooks.asUri(new byte[]{1, 2, 3}));
    }
}
