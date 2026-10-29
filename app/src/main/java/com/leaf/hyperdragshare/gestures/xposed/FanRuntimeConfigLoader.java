package com.leaf.hyperdragshare.gestures.xposed;

import android.content.Context;
import android.os.Bundle;

import com.leaf.hyperdragshare.codex.GestureDiagnostics;
import com.leaf.hyperdragshare.gestures.config.ConfigContract;

import java.util.List;

/** Reads and resolves one complete fan runtime configuration snapshot. */
final class FanRuntimeConfigLoader {
    private final Context context;

    FanRuntimeConfigLoader(Context context) {
        this.context = context;
    }

    LoadedConfig load() {
        Bundle bundle = context.getContentResolver().call(ConfigContract.URI, "get", null, null);
        GestureDiagnostics.configure(bundle);
        GestureConfig config = GestureConfig.from(bundle);
        FanRuntimeTargetResolver.ResolvedTargets resolved =
                new FanRuntimeTargetResolver(context).resolve(config);
        return new LoadedConfig(config, resolved.targets, resolved.honeycombTargets,
                resolved.sideTargets);
    }

    static final class LoadedConfig {
        final GestureConfig config;
        final List<RuntimeTarget> targets;
        final List<RuntimeTarget> honeycombTargets;
        final List<RuntimeTarget> sideTargets;

        private LoadedConfig(GestureConfig config, List<RuntimeTarget> targets,
                             List<RuntimeTarget> honeycombTargets,
                             List<RuntimeTarget> sideTargets) {
            this.config = config;
            this.targets = targets;
            this.honeycombTargets = honeycombTargets;
            this.sideTargets = sideTargets;
        }
    }
}
