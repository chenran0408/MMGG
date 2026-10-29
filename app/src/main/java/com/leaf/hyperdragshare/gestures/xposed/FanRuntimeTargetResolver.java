package com.leaf.hyperdragshare.gestures.xposed;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

import com.leaf.hyperdragshare.gestures.config.AppTarget;
import com.leaf.hyperdragshare.gestures.config.ShortcutIconLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves configured fan targets without changing runtime or gesture state. */
final class FanRuntimeTargetResolver {
    private final Context context;

    FanRuntimeTargetResolver(Context context) {
        this.context = context;
    }

    ResolvedTargets resolve(GestureConfig config) {
        PackageManager packageManager = context.getPackageManager();
        Map<String, RuntimeTarget> targetCache = new HashMap<>();
        return new ResolvedTargets(
                resolveTargets(packageManager, config.targets, config.fanMaxTargets,
                        "Configured target is unavailable", targetCache),
                resolveTargets(packageManager, config.honeycombTargets,
                        config.honeycombMaxTargets, "Configured honeycomb target is unavailable",
                        targetCache),
                resolveTargets(packageManager, config.sideTargets, config.sideMaxTargets,
                        "Configured side target is unavailable", targetCache));
    }

    private List<RuntimeTarget> resolveTargets(PackageManager packageManager,
                                               List<GestureConfig.TargetSpec> configured,
                                               int maximum,
                                               String errorMessage,
                                               Map<String, RuntimeTarget> cache) {
        ArrayList<RuntimeTarget> resolved = new ArrayList<>();
        for (GestureConfig.TargetSpec target : configured) {
            try {
                RuntimeTarget runtimeTarget = resolveTarget(packageManager, target, cache);
                if (runtimeTarget != null) {
                    resolved.add(runtimeTarget);
                }
            } catch (Throwable error) {
                Log.e(errorMessage, error);
            }
        }
        if (resolved.size() > maximum) {
            resolved.subList(maximum, resolved.size()).clear();
        }
        return Collections.unmodifiableList(resolved);
    }

    private RuntimeTarget resolveTarget(PackageManager packageManager,
                                        GestureConfig.TargetSpec target) throws Exception {
        if (target.isShortcut()) {
            return new RuntimeTarget(target.packageName, target.shortcutId,
                    target.shortcutIntentUri,
                    target.shortcutLabel.isEmpty() ? target.shortcutId : target.shortcutLabel,
                    ShortcutIconLoader.load(context, target.packageName, target.shortcutId,
                            target.userId),
                    target.userId);
        }
        ComponentName component = ComponentName.unflattenFromString(target.component);
        if (component == null) {
            return null;
        }
        ActivityInfo info = packageManager.getActivityInfo(component, 0);
        CharSequence label = info.loadLabel(packageManager);
        return new RuntimeTarget(component,
                label == null ? component.getPackageName() : label.toString(),
                info.loadIcon(packageManager), target.userId);
    }

    private RuntimeTarget resolveTarget(PackageManager packageManager,
                                        GestureConfig.TargetSpec target,
                                        Map<String, RuntimeTarget> cache) throws Exception {
        String key = target.component + '\u0000' + target.packageName + '\u0000'
                + target.shortcutId + '\u0000' + target.shortcutIntentUri + '\u0000'
                + target.userId;
        RuntimeTarget cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        RuntimeTarget resolved = resolveTarget(packageManager, target);
        if (resolved != null) {
            cache.put(key, resolved);
        }
        return resolved;
    }

    RuntimeTarget resolveAppTarget(PackageManager packageManager, AppTarget target,
                                   Map<String, RuntimeTarget> cache) throws Exception {
        String key = target.component + '\u0000' + target.shortcutPackage + '\u0000'
                + target.shortcutId + '\u0000' + target.shortcutIntentUri + '\u0000'
                + target.userId;
        RuntimeTarget cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        RuntimeTarget resolved;
        if (target.isShortcut()) {
            resolved = new RuntimeTarget(target.shortcutPackage, target.shortcutId,
                    target.shortcutIntentUri,
                    target.shortcutLabel == null || target.shortcutLabel.isEmpty()
                            ? target.shortcutId : target.shortcutLabel,
                    ShortcutIconLoader.load(context, target.shortcutPackage, target.shortcutId,
                            target.userId),
                    target.userId);
        } else {
            ComponentName component = target.componentName();
            if (component == null) {
                return null;
            }
            ActivityInfo info = packageManager.getActivityInfo(component, 0);
            CharSequence label = info.loadLabel(packageManager);
            resolved = new RuntimeTarget(component,
                    label == null ? component.getPackageName() : label.toString(),
                    info.loadIcon(packageManager), target.userId);
        }
        cache.put(key, resolved);
        return resolved;
    }

    static final class ResolvedTargets {
        final List<RuntimeTarget> targets;
        final List<RuntimeTarget> honeycombTargets;
        final List<RuntimeTarget> sideTargets;

        private ResolvedTargets(List<RuntimeTarget> targets,
                                List<RuntimeTarget> honeycombTargets,
                                List<RuntimeTarget> sideTargets) {
            this.targets = targets;
            this.honeycombTargets = honeycombTargets;
            this.sideTargets = sideTargets;
        }
    }
}
