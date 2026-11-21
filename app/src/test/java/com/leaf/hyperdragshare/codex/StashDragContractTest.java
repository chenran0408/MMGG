package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ClipData;
import android.content.ClipDescription;
import android.net.Uri;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class StashDragContractTest {
    @Test public void markedImageKeepsMimeUrisAndTraceAcrossParcelDelivery() {
        Uri first = Uri.parse("content://stash/first");
        Uri second = Uri.parse("content://stash/second");
        ClipData original = new ClipData("Stash images", new String[]{"image/gif", "image/jpeg"},
                new ClipData.Item(first));
        original.addItem(new ClipData.Item(second));
        OperationTrace.put(original, "stash-drag-test");

        ClipData marked = StashDragContract.mark(original);
        String session = StashDragContract.session(marked.getDescription());
        assertNotNull(session);
        assertNull(StashDragContract.session(original.getDescription()));

        Parcel parcel = Parcel.obtain();
        try {
            marked.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            ClipData delivered = ClipData.CREATOR.createFromParcel(parcel);
            assertEquals("Stash images", delivered.getDescription().getLabel().toString());
            assertTrue(delivered.getDescription().hasMimeType("image/gif"));
            assertTrue(delivered.getDescription().hasMimeType("image/jpeg"));
            assertTrue(StashDragContract.matches(session, delivered.getDescription()));
            assertEquals("stash-drag-test", OperationTrace.read(delivered));
            assertEquals(2, delivered.getItemCount());
            assertEquals(first, delivered.getItemAt(0).getUri());
            assertEquals(second, delivered.getItemAt(1).getUri());
        } finally {
            parcel.recycle();
        }
    }

    @Test public void aDifferentStashDragOrOrdinaryDragDoesNotMatch() {
        ClipData text = ClipData.newPlainText("Stash text", "value");
        ClipDescription first = StashDragContract.mark(text).getDescription();
        ClipDescription second = StashDragContract.mark(text).getDescription();
        String session = StashDragContract.session(first);

        assertTrue(StashDragContract.matches(session, first));
        assertFalse(StashDragContract.matches(session, second));
        assertFalse(StashDragContract.matches(session, text.getDescription()));
        assertFalse(StashDragContract.matches(session, null));
    }
}
