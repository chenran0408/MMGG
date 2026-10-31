package com.leaf.hyperdragshare.gestures.xposed;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.media.session.PlaybackState.ACTION_PAUSE;
import static android.media.session.PlaybackState.ACTION_PLAY;
import static android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT;
import static android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public final class MiniFreeformMediaControllerTest {
    @Test public void missingSessionDoesNotShowControlsAndDisabledUpdateUnregisters() {
        FakeSessions sessions = new FakeSessions();
        FakeHost host = new FakeHost();
        MiniFreeformMediaController controller = controller(sessions, host, new FakeVolume());

        controller.update("player.app", 7, bounds(), true, false);
        assertEquals(0, host.showCount);
        assertEquals(1, sessions.registerCount);

        controller.update("player.app", 7, bounds(), false, false);
        assertEquals(1, sessions.unregisterCount);
        assertEquals(0, host.showCount);
    }

    @Test public void onlyTheCurrentMiniPackageSessionIsShownAndTransportActionsAreGated() {
        FakeSessions sessions = new FakeSessions();
        FakeHost host = new FakeHost();
        FakeSession unrelated = new FakeSession("other.app", ACTION_PLAY | ACTION_PAUSE);
        FakeSession current = new FakeSession("player.app", ACTION_PLAY | ACTION_SKIP_TO_NEXT);
        sessions.items.addAll(Arrays.asList(unrelated, current));
        MiniFreeformMediaController controller = controller(sessions, host, new FakeVolume());

        controller.update("player.app", 7, bounds(), true, false);

        assertEquals(1, host.showCount);
        assertEquals(7, host.taskId);
        assertNotNull(host.controls);
        LinearLayout row = (LinearLayout) host.controls;
        assertFalse(row.getChildAt(0).isEnabled());
        assertTrue(row.getChildAt(1).isEnabled());
        assertTrue(row.getChildAt(2).isEnabled());
        ((Button) row.getChildAt(1)).performClick();
        ((Button) row.getChildAt(2)).performClick();
        assertEquals(1, current.playPauseCount);
        assertEquals(1, current.nextCount);
        assertEquals(0, unrelated.playPauseCount);
    }

    @Test public void switchingSessionInvalidatesOldCallbackAndDestroyHidesAndUnregisters() {
        FakeSessions sessions = new FakeSessions();
        FakeHost host = new FakeHost();
        FakeSession old = new FakeSession("player.app", ACTION_PLAY | ACTION_PAUSE);
        sessions.items.add(old);
        MiniFreeformMediaController controller = controller(sessions, host, new FakeVolume());
        controller.update("player.app", 7, bounds(), true, false);
        Runnable oldCallback = old.registered;

        FakeSession current = new FakeSession("player.app", ACTION_PAUSE | ACTION_SKIP_TO_PREVIOUS);
        sessions.items.clear();
        sessions.items.add(current);
        sessions.fireChanged();
        assertEquals(1, old.unregisterCount);
        assertEquals(1, host.showCount);
        assertEquals(1, host.updateCount);
        oldCallback.run();
        LinearLayout row = (LinearLayout) host.controls;
        assertFalse(row.getChildAt(1).isEnabled());
        assertTrue(row.getChildAt(0).isEnabled());

        controller.destroy();
        assertEquals(1, host.hideCount);
        assertEquals(1, current.unregisterCount);
        assertEquals(1, sessions.unregisterCount);
        assertNull(host.controls);
    }

    @Test public void recreatingWrapperWithSameMediaTokenKeepsCallbackLease() {
        FakeSessions sessions = new FakeSessions();
        FakeHost host = new FakeHost();
        Object token = new Object();
        RecreatingSessionSource source = new RecreatingSessionSource(
                new FakeSession("player.app", ACTION_PLAY | ACTION_PAUSE, token));
        MiniFreeformMediaController controller = controller(source, host, new FakeVolume());

        controller.update("player.app", 7, bounds(), true, false);
        FakeSession selected = (FakeSession) source.lastReturned;
        source.fireChanged();

        assertEquals(1, host.showCount);
        assertEquals(0, source.unregisterCount);
        assertFalse(selected == source.lastReturned);
        assertEquals(1, selected.registerCount);
        assertEquals(0, selected.unregisterCount);
    }

    @Test public void muteLeaseRestoresOriginalLevelButPreservesUserVolumeChange() {
        FakeVolume volume = new FakeVolume();
        Object player = new Object();
        volume.player = player;
        volume.current = 0.65f;
        MiniFreeformMediaController.MuteLease lease = new MiniFreeformMediaController.MuteLease();

        lease.acquire("player.app", volume);
        assertEquals(0f, volume.current, 0f);
        lease.release(volume);
        assertEquals(0.65f, volume.current, 0f);

        volume.current = 0.4f;
        lease.acquire("player.app", volume);
        volume.current = 0.25f;
        lease.release(volume);
        assertEquals(0.25f, volume.current, 0f);
    }

    @Test public void muteLeaseDoesNotBecomeActiveWhenWriteFails() {
        FakeVolume volume = new FakeVolume();
        volume.player = new Object();
        volume.current = 0.65f;
        volume.writeSuccess = false;
        MiniFreeformMediaController.MuteLease lease = new MiniFreeformMediaController.MuteLease();

        lease.acquire("player.app", volume);

        assertFalse(lease.active());
        assertEquals(0.65f, volume.current, 0f);
        assertEquals(1, volume.writeCount);
    }

    @Test public void missingClientUidMethodDisablesPlayerOwnerLookup() {
        assertNull(MiniFreeformMediaController.XiaomiPlayerVolume
                .resolveClientUidMethod(NoClientUid.class));
    }

    @Test public void muteLeaseDoesNotRestoreAReplacedPlaybackOwner() {
        FakeVolume volume = new FakeVolume();
        volume.player = new Object();
        volume.current = 0.65f;
        MiniFreeformMediaController.MuteLease lease = new MiniFreeformMediaController.MuteLease();

        lease.acquire("player.app", volume);
        volume.player = new Object();
        volume.current = 0f;
        volume.writeCount = 0;
        lease.release(volume);

        assertEquals(0, volume.writeCount);
        assertEquals(0f, volume.current, 0f);
    }

    private static MiniFreeformMediaController controller(FakeSessions sessions, FakeHost host,
                                                           FakeVolume volume) {
        return controller((MiniFreeformMediaController.SessionSource) sessions, host, volume);
    }

    private static MiniFreeformMediaController controller(
            MiniFreeformMediaController.SessionSource sessions,
            FakeHost host, FakeVolume volume) {
        Context context = RuntimeEnvironment.getApplication();
        return new MiniFreeformMediaController(context, sessions, volume, host);
    }

    private static Rect bounds() { return new Rect(10, 20, 210, 180); }

    private static final class FakeSessions implements MiniFreeformMediaController.SessionSource {
        final List<MiniFreeformMediaController.Session> items = new ArrayList<>();
        Runnable registered;
        int registerCount;
        int unregisterCount;
        @Override public List<MiniFreeformMediaController.Session> activeSessions() { return items; }
        @Override public boolean register(Runnable changed) {
            registered = changed;
            registerCount++;
            return true;
        }
        @Override public void unregister(Runnable changed) {
            if (registered == changed) registered = null;
            unregisterCount++;
        }
        void fireChanged() { if (registered != null) registered.run(); }
    }

    private static final class FakeSession implements MiniFreeformMediaController.Session {
        final String packageName;
        final long actions;
        final Object token;
        Runnable registered;
        int registerCount;
        int unregisterCount;
        int playPauseCount;
        int previousCount;
        int nextCount;
        FakeSession(String packageName, long actions) {
            this(packageName, actions, new Object());
        }
        FakeSession(String packageName, long actions, Object token) {
            this.packageName = packageName;
            this.actions = actions;
            this.token = token;
        }
        @Override public String packageName() { return packageName; }
        @Override public Object token() { return token; }
        @Override public long actions() { return actions; }
        @Override public boolean playing() { return false; }
        @Override public void playPause() { playPauseCount++; }
        @Override public void previous() { previousCount++; }
        @Override public void next() { nextCount++; }
        @Override public void register(Runnable changed) { registered = changed; registerCount++; }
        @Override public void unregister(Runnable changed) {
            if (registered == changed) registered = null;
            unregisterCount++;
        }
    }

    private static final class FakeHost implements MiniFreeformMediaController.Host {
        int showCount;
        int updateCount;
        int hideCount;
        int taskId = -1;
        View controls;
        @Override public void show(int taskId, Rect miniBounds, View controls) {
            showCount++;
            this.taskId = taskId;
            this.controls = controls;
        }
        @Override public void update(int taskId, Rect miniBounds) {
            this.taskId = taskId;
            updateCount++;
        }
        @Override public void hide() { hideCount++; controls = null; }
    }

    private static final class FakeVolume implements MiniFreeformMediaController.Volume {
        float current;
        Object player;
        boolean writeSuccess = true;
        int writeCount;
        @Override public float read(String packageName) { return current; }
        @Override public boolean write(String packageName, Object player, float volume) {
            writeCount++;
            if (!writeSuccess) return false;
            this.player = player;
            current = volume;
            return true;
        }
        @Override public Object player(String packageName) { return player; }
    }

    private static final class NoClientUid {
    }

    private static final class RecreatingSessionSource
            implements MiniFreeformMediaController.SessionSource {
        private final FakeSession template;
        private Runnable registered;
        private int unregisterCount;
        private MiniFreeformMediaController.Session lastReturned;

        RecreatingSessionSource(FakeSession template) { this.template = template; }

        @Override public List<MiniFreeformMediaController.Session> activeSessions() {
            lastReturned = new FakeSession(template.packageName, template.actions, template.token);
            return Arrays.asList(lastReturned);
        }

        @Override public boolean register(Runnable changed) { registered = changed; return true; }

        @Override public void unregister(Runnable changed) {
            if (registered == changed) registered = null;
            unregisterCount++;
        }

        void fireChanged() { if (registered != null) registered.run(); }
    }
}
