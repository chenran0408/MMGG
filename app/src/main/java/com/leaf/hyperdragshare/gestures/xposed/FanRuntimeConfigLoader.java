package com.leaf.hyperdragshare.gestures.xposed;

import android.content.Context;
import android.os.Bundle;

import com.leaf.hyperdragshare.codex.GestureDiagnostics;
import com.leaf.hyperdragshare.gestures.config.ConfigContract;

import java.util.List;

/** Reads and resolves one complete fan runtime configuration snapshot. */
final class FanRuntimeConfigLoader {
    private final Context context;
    private final FanRuntimeTargetResolver targetResolver;

    FanRuntimeConfigLoader(Context context) {
        this.context = context;
        this.targetResolver = new FanRuntimeTargetResolver(context);
    }

    LoadedConfig load() {
        Bundle bundle = context.getContentResolver().call(ConfigContract.URI, "get", null, null);
        GestureDiagnostics.configure(bundle);
        GestureConfig config = GestureConfig.from(bundle);
        FanRuntimeTargetResolver.ResolvedTargets resolved =
                targetResolver.resolve(config);
        return new LoadedConfig(config, resolved.targets, resolved.honeycombTargets);
    }

    void invalidatePackage(String packageName) {
        targetResolver.invalidatePackage(packageName);
    }

    static final class LoadedConfig {
        final GestureConfig config;
        final List<RuntimeTarget> targets;
        final List<RuntimeTarget> honeycombTargets;
        private LoadedConfig(GestureConfig config, List<RuntimeTarget> targets,
                             List<RuntimeTarget> honeycombTargets) {
            this.config = config;
            this.targets = targets;
            this.honeycombTargets = honeycombTargets;
        }
    }
}
