package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.*;

import java.util.Set;
import org.junit.Test;

public final class RootRecordingActivityTest {
    @Test public void onlyNewLiveRecordingFromTheSameAppAndUserYields() {
        var timeline = new RootRecordingActivity.Timeline();
        var old = new RootRecordingActivity.Recording("source", 0, 1);
        var otherUser = new RootRecordingActivity.Recording("source", 10, 2);
        var otherApp = new RootRecordingActivity.Recording("other", 0, 3);
        var newVoice = new RootRecordingActivity.Recording("source", 0, 4);
        timeline.update(Set.of(old), 10);
        timeline.update(Set.of(old, otherUser, otherApp), 130);
        assertFalse(timeline.startedSince("source", 0, 100));
        timeline.update(Set.of(old, otherUser, otherApp, newVoice), 140);
        assertTrue(timeline.startedSince("source", 0, 100));
        timeline.update(Set.of(old), 160);
        assertFalse(timeline.startedSince("source", 0, 100));
    }
}
