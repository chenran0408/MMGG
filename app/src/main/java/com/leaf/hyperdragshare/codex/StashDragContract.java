package com.leaf.hyperdragshare.codex;

import android.content.ClipData;
import android.content.ClipDescription;
import android.os.PersistableBundle;

import java.util.UUID;

/** Correlates events with the source drag without changing the target's data types. */
final class StashDragContract {
    private static final String MIME = "application/x-mmgg-stash";
    private static final String SESSION = "mmgg_stash_drag_session";

    private StashDragContract() {}

    static ClipData mark(ClipData content) {
        ClipDescription existing = content.getDescription();
        int count = existing.getMimeTypeCount();
        String[] types = new String[count + 1];
        for (int i = 0; i < count; i++) types[i] = existing.getMimeType(i);
        types[count] = MIME;
        ClipDescription description = new ClipDescription(existing.getLabel(), types);
        PersistableBundle extras = existing.getExtras() == null
                ? new PersistableBundle() : new PersistableBundle(existing.getExtras());
        extras.putString(SESSION, UUID.randomUUID().toString());
        description.setExtras(extras);
        ClipData marked = new ClipData(description, content.getItemAt(0));
        for (int i = 1; i < content.getItemCount(); i++) marked.addItem(content.getItemAt(i));
        return marked;
    }

    static String session(ClipDescription description) {
        if (description == null || !description.hasMimeType(MIME)) return null;
        PersistableBundle extras = description.getExtras();
        return extras == null ? null : extras.getString(SESSION);
    }

    static boolean matches(String expected, ClipDescription description) {
        return expected != null && expected.equals(session(description));
    }
}
