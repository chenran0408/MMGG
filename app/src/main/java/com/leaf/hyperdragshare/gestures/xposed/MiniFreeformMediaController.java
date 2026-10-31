package com.leaf.hyperdragshare.gestures.xposed;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import java.lang.reflect.Method;
import java.util.List;

/**
 * One controller per mini task. Its host places a compact overlay beside the mini
 * task without changing task geometry or covering the mini task hitbox.
 */
final class MiniFreeformMediaController {
    interface Host {
        void show(int taskId, Rect miniBounds, View controls);
        void update(int taskId, Rect miniBounds);
        void hide();
    }

    interface SessionSource {
        List<Session> activeSessions();
        boolean register(Runnable changed);
        void unregister(Runnable changed);
    }

    interface Session {
        String packageName();
        /** Stable media-session token; wrappers may be recreated on every query. */
        default Object token() { return this; }
        /** False after the platform reports that this session was destroyed. */
        default boolean valid() { return true; }
        long actions();
        boolean playing();
        void playPause();
        void previous();
        void next();
        void register(Runnable changed);
        void unregister(Runnable changed);
    }

    interface Volume {
        float read(String packageName);
        /** Returns true only when the platform call completed successfully. */
        boolean write(String packageName, Object player, float volume);
        Object player(String packageName);
    }

    static final class ControllerSessionSource implements SessionSource {
        private final MediaSessionManager manager;
        private final Handler handler;
        private final MediaSessionManager.OnActiveSessionsChangedListener listener;
        private Runnable changed;

        ControllerSessionSource(MediaSessionManager manager, Handler handler) {
            this.manager = manager;
            this.handler = handler;
            listener = controllers -> {
                Runnable current = changed;
                if (current != null) current.run();
            };
        }

        @Override public List<Session> activeSessions() {
            List<MediaController> controllers = manager.getActiveSessions(null);
            java.util.ArrayList<Session> result = new java.util.ArrayList<>();
            if (controllers != null) {
                for (MediaController controller : controllers) {
                    if (controller != null) result.add(new ControllerSession(controller));
                }
            }
            return result;
        }

        @Override public boolean register(Runnable callback) {
            if (changed == callback) return true;
            if (changed != null) unregister(changed);
            try {
                manager.addOnActiveSessionsChangedListener(listener, null, handler);
                changed = callback;
                return true;
            } catch (RuntimeException error) {
                changed = null;
                Log.e("Cannot observe active media sessions", error);
                return false;
            }
        }

        @Override public void unregister(Runnable callback) {
            if (changed != callback) return;
            changed = null;
            try {
                manager.removeOnActiveSessionsChangedListener(listener);
            } catch (RuntimeException error) {
                Log.e("Cannot stop observing active media sessions", error);
            }
        }
    }

    private static final class ControllerSession implements Session {
        private final MediaController controller;
        private final Object token;
        private Runnable changed;
        private boolean destroyed;
        private final MediaController.Callback callback = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState state) {
                Runnable current = changed;
                if (current != null) current.run();
            }

            @Override public void onSessionDestroyed() {
                destroyed = true;
                Runnable current = changed;
                if (current != null) current.run();
            }
        };

        ControllerSession(MediaController controller) {
            this.controller = controller;
            Object sessionToken = controller.getSessionToken();
            token = sessionToken == null ? controller : sessionToken;
        }
        @Override public String packageName() { return controller.getPackageName(); }
        @Override public Object token() { return token; }
        @Override public boolean valid() { return !destroyed; }
        @Override public long actions() {
            PlaybackState state = controller.getPlaybackState();
            return state == null ? 0L : state.getActions();
        }
        @Override public boolean playing() {
            PlaybackState state = controller.getPlaybackState();
            return state != null && state.getState() == PlaybackState.STATE_PLAYING;
        }
        @Override public void playPause() {
            PlaybackState state = controller.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING)
                controller.getTransportControls().pause();
            else controller.getTransportControls().play();
        }
        @Override public void previous() { controller.getTransportControls().skipToPrevious(); }
        @Override public void next() { controller.getTransportControls().skipToNext(); }
        @Override public void register(Runnable changed) {
            if (this.changed == changed) return;
            if (this.changed != null) unregister(this.changed);
            this.changed = changed;
            try {
                controller.registerCallback(callback);
            } catch (RuntimeException error) {
                this.changed = null;
                Log.e("Cannot register mini media callback", error);
            }
        }
        @Override public void unregister(Runnable changed) {
            if (this.changed != changed) return;
            this.changed = null;
            try {
                controller.unregisterCallback(callback);
            } catch (RuntimeException error) {
                Log.e("Cannot unregister mini media callback", error);
            }
        }
    }

    /** Xiaomi's SystemUI AudioManager extension used by the reference decoration. */
    static final class XiaomiPlayerVolume implements Volume {
        private final AudioManager audioManager;
        private final int appUid;
        private final Method readMethod;
        private final Method writeMethod;
        private final Method clientUidMethod;

        XiaomiPlayerVolume(Context context, String packageName) {
            audioManager = context.getSystemService(AudioManager.class);
            int uid = -1;
            Method read = null;
            Method write = null;
            try {
                if (audioManager == null) throw new IllegalStateException("AudioManager unavailable");
                uid = context.getPackageManager().getApplicationInfo(packageName, 0).uid;
                read = audioManager.getClass().getMethod("getPlayerVolume", String.class);
                write = audioManager.getClass().getMethod("setPlayerVolume",
                        AudioPlaybackConfiguration.class, float.class);
                read.setAccessible(true);
                write.setAccessible(true);
            } catch (PackageManager.NameNotFoundException | NoSuchMethodException
                     | SecurityException error) {
                // This optional control is absent on Android builds without Xiaomi's API.
            }
            Method ownerMethod = resolveClientUidMethod(AudioPlaybackConfiguration.class);
            appUid = uid;
            readMethod = read;
            writeMethod = write;
            clientUidMethod = ownerMethod;
        }

        static Method resolveClientUidMethod(Class<?> type) {
            try {
                Method method = type.getDeclaredMethod("getClientUid");
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException | SecurityException error) {
                return null;
            }
        }

        @Override public float read(String packageName) {
            if (readMethod == null) return Float.NaN;
            try {
                Object value = readMethod.invoke(audioManager, packageName);
                return value instanceof Number ? ((Number) value).floatValue() : Float.NaN;
            } catch (Throwable error) {
                Log.e("Cannot read player volume package=" + packageName, error);
                return Float.NaN;
            }
        }

        @Override public boolean write(String packageName, Object player, float volume) {
            if (writeMethod == null || !(player instanceof AudioPlaybackConfiguration)) return false;
            try {
                writeMethod.invoke(audioManager, player, volume);
                return true;
            } catch (Throwable error) {
                Log.e("Cannot write player volume package=" + packageName, error);
                return false;
            }
        }

        @Override public Object player(String packageName) {
            if (appUid < 0 || clientUidMethod == null) return null;
            try {
                List<AudioPlaybackConfiguration> active = audioManager.getActivePlaybackConfigurations();
                if (active != null) {
                    for (AudioPlaybackConfiguration item : active) {
                        if (item == null) continue;
                        Object owner = clientUidMethod.invoke(item);
                        if (owner instanceof Number && ((Number) owner).intValue() == appUid) {
                            return item;
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                Log.e("Cannot find player volume owner package=" + packageName, error);
            }
            return null;
        }
    }

    static final class MuteLease {
        private String packageName;
        private Object player;
        private float original = Float.NaN;

        boolean active() { return packageName != null; }

        void acquire(String packageName, Volume volume) {
            if (active()) return;
            Object candidate = volume.player(packageName);
            float current = volume.read(packageName);
            if (candidate == null || !Float.isFinite(current) || current <= 0f) return;
            if (!volume.write(packageName, candidate, 0f)) return;
            float muted = volume.read(packageName);
            if (!Float.isFinite(muted) || muted != 0f) {
                // Do not claim a lease when the write could not be observed.
                volume.write(packageName, candidate, current);
                return;
            }
            this.packageName = packageName;
            this.player = candidate;
            original = current;
        }

        void release(Volume volume) {
            if (!active()) return;
            Object currentPlayer = volume.player(packageName);
            if (player == currentPlayer && volume.read(packageName) == 0f)
                volume.write(packageName, player, original);
            packageName = null;
            player = null;
            original = Float.NaN;
        }
    }

    private final Context context;
    private final SessionSource sessions;
    private final Volume volume;
    private final Host host;
    private final MuteLease muteLease = new MuteLease();
    private final LinearLayout controls;
    private final Button previousButton;
    private final Button playPauseButton;
    private final Button nextButton;
    private String packageName;
    private int taskId = -1;
    private Rect bounds;
    private Session selected;
    private Runnable selectedSessionCallback;
    private boolean enabled;
    private boolean autoMute;
    private boolean shown;
    private boolean observing;
    private Runnable activeSessionsCallback;
    private long generation;

    MiniFreeformMediaController(Context context, SessionSource sessions, Volume volume,
                                Host host) {
        this.context = context.getApplicationContext();
        this.sessions = sessions;
        this.volume = volume;
        this.host = host;
        controls = new LinearLayout(this.context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        previousButton = button("|◀");
        playPauseButton = button("▶");
        nextButton = button("▶|");
        controls.addView(previousButton);
        controls.addView(playPauseButton);
        controls.addView(nextButton);
        previousButton.setOnClickListener(view -> { if (selected != null) selected.previous(); });
        playPauseButton.setOnClickListener(view -> { if (selected != null) selected.playPause(); });
        nextButton.setOnClickListener(view -> { if (selected != null) selected.next(); });
    }

    private Button button(String label) {
        Button button = new Button(context);
        button.setText(label);
        int size = Math.round(48f * context.getResources().getDisplayMetrics().density);
        button.setMinWidth(size);
        button.setMinHeight(size);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    /** Call with the current freeform snapshot. Invalid/non-mini state tears down ownership. */
    void update(String miniPackage, int miniTaskId, Rect miniBounds,
                boolean enabled, boolean autoMute) {
        if (!enabled || miniPackage == null || miniPackage.isEmpty()
                || miniTaskId < 0 || miniBounds == null || miniBounds.isEmpty()) {
            clear();
            return;
        }
        boolean changedTask = miniTaskId != taskId || !miniPackage.equals(packageName);
        if (changedTask) clear();
        packageName = miniPackage;
        taskId = miniTaskId;
        bounds = new Rect(miniBounds);
        this.enabled = true;
        this.autoMute = autoMute;
        if (!observing) {
            long observedGeneration = ++generation;
            Runnable callback = () -> {
                if (observing && generation == observedGeneration) refreshSession();
            };
            observing = sessions.register(callback);
            activeSessionsCallback = observing ? callback : null;
        }
        refreshSession();
    }

    void destroy() { clear(); }

    private void refreshSession() {
        if (!enabled || packageName == null) return;
        Session next = null;
        if (selected == null || selected.valid()) {
            List<Session> active;
            try {
                active = sessions.activeSessions();
            } catch (RuntimeException error) {
                // SystemUI may temporarily lose media-session access while userspace
                // services restart. Keep the mini task callback alive and hide controls
                // until the next session change instead of propagating into task handling.
                Log.e("Cannot read active mini media sessions", error);
                active = java.util.Collections.emptyList();
            }
            if (active != null) {
                for (Session candidate : active) {
                    if (candidate == null || !candidate.valid()
                            || !packageName.equals(candidate.packageName())) continue;
                    if (next == null || candidate.playing()) next = candidate;
                    if (candidate.playing()) break;
                }
            }
        }
        if (!sameSession(selected, next)) {
            muteLease.release(volume);
            if (selected != null && selectedSessionCallback != null)
                selected.unregister(selectedSessionCallback);
            selected = next;
            if (selected != null) {
                Session registered = selected;
                selectedSessionCallback = () -> {
                    if (selected == registered) refreshSession();
                };
                selected.register(selectedSessionCallback);
            } else selectedSessionCallback = null;
        }
        if (autoMute && selected != null) muteLease.acquire(packageName, volume);
        else muteLease.release(volume);
        if (selected == null) {
            if (shown) host.hide();
            shown = false;
            renderState();
            return;
        }
        if (shown) host.update(taskId, new Rect(bounds));
        else {
            host.show(taskId, new Rect(bounds), controls);
            shown = true;
        }
        renderState();
    }

    private static boolean sameSession(Session first, Session second) {
        if (first == null || second == null) return first == second;
        if (!first.valid() || !second.valid()
                || !first.packageName().equals(second.packageName())) return false;
        Object firstToken = first.token();
        Object secondToken = second.token();
        return firstToken != null && firstToken.equals(secondToken);
    }

    private void renderState() {
        Session current = selected;
        long actions = current == null ? 0L : current.actions();
        long requiredAction = current != null && current.playing()
                ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
        boolean canPlayPause = current != null
                && ((actions & requiredAction) != 0L
                || (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0L);
        previousButton.setEnabled(current != null
                && (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L);
        playPauseButton.setEnabled(current != null && canPlayPause);
        playPauseButton.setText(current != null && current.playing() ? "Ⅱ" : "▶");
        nextButton.setEnabled(current != null
                && (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0L);
    }

    private void clear() {
        if (selected != null && selectedSessionCallback != null)
            selected.unregister(selectedSessionCallback);
        selected = null;
        selectedSessionCallback = null;
        generation++;
        if (observing && activeSessionsCallback != null)
            sessions.unregister(activeSessionsCallback);
        activeSessionsCallback = null;
        observing = false;
        if (shown) host.hide();
        shown = false;
        muteLease.release(volume);
        enabled = false;
        autoMute = false;
        packageName = null;
        taskId = -1;
        bounds = null;
        renderState();
    }
}
