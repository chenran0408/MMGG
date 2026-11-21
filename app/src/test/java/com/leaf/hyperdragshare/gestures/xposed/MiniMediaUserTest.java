package com.leaf.hyperdragshare.gestures.xposed;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowApplicationPackageManager;
import org.robolectric.shadows.ShadowUserManager;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = {MiniMediaUserTest.Profiles.class, MiniMediaUserTest.PackageUsers.class,
        MiniMediaUserTest.Sessions.class})
public class MiniMediaUserTest {
    private static final int CLONE = 999;
    private static final int CLONE_UID = CLONE * 100000 + 10555;
    private static final UserHandle CLONE_USER = UserHandle.getUserHandleForUid(CLONE_UID);

    @Test public void packageGainIsAvailableOnlyForTheSoleInstalledOwner() {
        var context = RuntimeEnvironment.getApplication();
        assertEquals(-1, MiniMediaUser.isolatedVolumeUid(context, "both", 0));
        assertEquals(-1, MiniMediaUser.isolatedVolumeUid(context, "both", CLONE));
        assertEquals(CLONE_UID, MiniMediaUser.isolatedVolumeUid(context, "clone.only", CLONE));
        assertEquals(-1, MiniMediaUser.isolatedVolumeUid(context, "clone.only", 0));
        assertEquals(10555, MiniMediaUser.isolatedVolumeUid(context, "main.only", 0));
        assertEquals(-1, MiniMediaUser.isolatedVolumeUid(context, "unknown", -1));
    }

    @Test public void selectsAndObservesSessionsForTheMiniTaskUser() {
        var app = RuntimeEnvironment.getApplication();
        var handler = new Handler(Looper.getMainLooper());
        var controls = new MiniMediaControls(app, handler, Shadow.newInstanceOf(MediaSessionManager.class), null);
        var task = new MiniMediaControlsTest.MiniTask(25, "both");
        ReflectionHelpers.setField(task.getTaskInfo(), "userId", CLONE);
        assertEquals(-1, MiniMediaUser.taskUserId(new Object()));
        var config = GestureConfig.defaults();
        config.miniMediaControlsEnabled = true;
        controls.updateMiniMedia(task, config);
        assertEquals(CLONE_USER, Sessions.observed);
        assertEquals(CLONE_USER, Sessions.queried);
        controls.clearMiniMediaForTask(25);
        assertNull(Sessions.observed);
    }

    @Test @Config(shadows = {Sessions.class, Player.class, Transport.class})
    public void twoUsersPlayingTheSameAppPauseOnlyTheRequestedPlayerOnTap() {
        var manager = Shadow.newInstanceOf(MediaSessionManager.class);
        var mainUser = UserHandle.getUserHandleForUid(10555);
        var main = Shadow.newInstanceOf(MediaController.class);
        var clone = Shadow.newInstanceOf(MediaController.class);
        Sessions.players = java.util.Map.of(mainUser, main, CLONE_USER, clone);
        var source = new MiniFreeformMediaController.ControllerSessionSource(manager,
                new Handler(Looper.getMainLooper()), CLONE_USER);
        var visible = source.activeSessions();
        assertEquals(1, visible.size());
        assertEquals("both", visible.get(0).packageName());
        assertEquals(0, pauses(main));
        assertEquals(0, pauses(clone));
        visible.get(0).playPause();
        assertEquals(0, pauses(main));
        assertEquals(1, pauses(clone));
    }

    private static int pauses(MediaController controller) {
        return ((Transport) Shadow.extract(controller.getTransportControls())).pauses;
    }

    @Implements(MediaController.class)
    public static class Player {
        private final MediaController.TransportControls transport = Shadow.newInstanceOf(MediaController.TransportControls.class);
        @Implementation protected String getPackageName() { return "both"; }
        @Implementation protected PlaybackState getPlaybackState() {
            return new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0L, 1f).build();
        }
        @Implementation protected MediaController.TransportControls getTransportControls() { return transport; }
    }

    @Implements(MediaController.TransportControls.class)
    public static class Transport {
        int pauses;
        @Implementation protected void pause() { pauses++; }
    }

    @Implements(UserManager.class)
    public static class Profiles extends ShadowUserManager {
        @Implementation @Override protected List<UserHandle> getUserHandles(boolean omitDying) {
            return List.of(UserHandle.getUserHandleForUid(10555), CLONE_USER);
        }
    }

    @Implements(className = "android.app.ApplicationPackageManager", isInAndroidSdk = false)
    public static class PackageUsers extends ShadowApplicationPackageManager {
        @Implementation @Override protected int getPackageUidAsUser(String pkg, int user)
                throws PackageManager.NameNotFoundException {
            if ("both".equals(pkg) || (user == 0 && "main.only".equals(pkg))
                    || (user == CLONE && "clone.only".equals(pkg))) return user * 100000 + 10555;
            throw new PackageManager.NameNotFoundException(pkg);
        }
    }

    @Implements(MediaSessionManager.class)
    public static class Sessions {
        static UserHandle observed, queried;
        static java.util.Map<UserHandle, MediaController> players = java.util.Map.of();
        @Implementation protected List<MediaController> getActiveSessionsForUser(ComponentName listener, UserHandle user) {
            queried = user;
            MediaController player = players.get(user);
            return player == null ? List.of() : List.of(player);
        }
        @Implementation protected void addOnActiveSessionsChangedListener(ComponentName component, UserHandle user,
                Executor executor, MediaSessionManager.OnActiveSessionsChangedListener listener) {
            observed = user;
        }
        @Implementation protected void removeOnActiveSessionsChangedListener(
                MediaSessionManager.OnActiveSessionsChangedListener listener) { observed = null; }
    }
}
