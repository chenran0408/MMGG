package com.leaf.hyperdragshare.codex;

import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.BiConsumer;

/** Recording metadata observed by the existing Root helper. No audio is captured. */
final class RootRecordingActivity {
    private static final String DISPATCHER = "android.media.IRecordingConfigDispatcher";
    private final Method packageName;
    private final Method uid;
    private final BiConsumer<Set<Recording>, Long> publish;
    private final Object observationLock = new Object();
    private final Binder callback = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(DISPATCHER);
            observe(data.createTypedArrayList(AudioRecordingConfiguration.CREATOR), SystemClock.uptimeMillis());
            return true;
        }
    };

    private RootRecordingActivity(BiConsumer<Set<Recording>, Long> publish) throws ReflectiveOperationException {
        this.publish = publish;
        packageName = AudioRecordingConfiguration.class.getMethod("getClientPackageName");
        uid = AudioRecordingConfiguration.class.getMethod("getClientUid");
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        IBinder binder = (IBinder) serviceManager.getMethod("getService", String.class).invoke(null, "audio");
        Class<?> serviceType = Class.forName("android.media.IAudioService");
        Object service = Class.forName("android.media.IAudioService$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
        Class<?> dispatcher = Class.forName(DISPATCHER);
        Object listener = Proxy.newProxyInstance(dispatcher.getClassLoader(), new Class<?>[]{dispatcher},
                (proxy, method, args) -> callback);
        synchronized (observationLock) {
            serviceType.getMethod("registerRecordingCallback", dispatcher).invoke(service, listener);
            @SuppressWarnings("unchecked")
            List<AudioRecordingConfiguration> baseline = (List<AudioRecordingConfiguration>)
                    serviceType.getMethod("getActiveRecordingConfigurations").invoke(service);
            observe(baseline, Long.MIN_VALUE);
        }
    }

    static RootRecordingActivity open(BiConsumer<Set<Recording>, Long> publish) {
        try {
            return new RootRecordingActivity(publish);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            android.util.Log.w("DragShare/RecordingAvoidance", "Recording metadata unavailable", error);
            return null;
        }
    }

    private void observe(List<AudioRecordingConfiguration> configs, long observedAt) {
        Set<Recording> active = new HashSet<>();
        try {
            for (AudioRecordingConfiguration config : configs) {
                int source = config.getClientAudioSource();
                if (!microphoneSource(source)) continue;
                String pkg = (String) packageName.invoke(config);
                int owner = (Integer) uid.invoke(config);
                if (pkg != null && !pkg.isEmpty() && owner >= 0) {
                    active.add(new Recording(pkg, owner / 100000, config.getClientAudioSessionId()));
                }
            }
        } catch (ReflectiveOperationException error) {
            android.util.Log.w("DragShare/RecordingAvoidance", "Cannot read recording owner", error);
            return;
        }
        synchronized (observationLock) {
            publish.accept(active, observedAt);
        }
    }

    private static boolean microphoneSource(int source) {
        return switch (source) {
            case MediaRecorder.AudioSource.DEFAULT, MediaRecorder.AudioSource.MIC,
                 MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.VOICE_RECOGNITION,
                 MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.UNPROCESSED,
                 MediaRecorder.AudioSource.VOICE_PERFORMANCE -> true;
            default -> false;
        };
    }

    record Recording(String pkg, int user, int session) {}

    static final class Timeline {
        private Map<Recording, Long> starts = new HashMap<>();

        synchronized void update(java.util.Set<Recording> active, long observedAt) {
            Map<Recording, Long> next = new HashMap<>();
            for (Recording recording : active) {
                next.put(recording, starts.getOrDefault(recording, observedAt));
            }
            starts = next;
        }

        synchronized boolean startedSince(String pkg, int user, long downTime) {
            for (var entry : starts.entrySet()) {
                Recording recording = entry.getKey();
                if (recording.user() == user && recording.pkg().equals(pkg)
                        && entry.getValue() >= downTime) return true;
            }
            return false;
        }
    }
}
