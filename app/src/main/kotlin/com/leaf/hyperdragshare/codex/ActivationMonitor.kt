package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

private const val PORTAL_START_TIMEOUT_MS = 10_000L
// One bounded startup followed by the host's four-second Root probe and report.
private const val PORTAL_ROUND_LIMIT_MS = PORTAL_START_TIMEOUT_MS + 5_000L

internal data class ActivationSnapshot(
    val root: ActivationEvidence,
    val injection: ActivationEvidence,
    val portalService: ActivationEvidence,
    val portalRoot: ActivationEvidence,
    val rootInput: ActivationEvidence,
    val pressReader: ActivationEvidence,
    val overlayPermission: ActivationEvidence,
    val thumbTriggerEnabled: Boolean,
    val copyDirectEnabled: Boolean,
    val rootInputError: String,
    val pressReaderError: String,
    val portalEnabled: Boolean? = true,
    val pendingPortalEnabled: Boolean? = null,
) {
    val portalFeature: ActivationState
        get() = activationFeatureState(
            enabled = portalEnabled == true,
            dependencies = listOf(injection, portalService, portalRoot, rootInput),
        )

    val pressFeature: ActivationState
        get() = activationFeatureState(
            enabled = portalEnabled == true,
            dependencies = listOf(
                injection,
                portalService,
                portalRoot,
                rootInput,
                if (thumbTriggerEnabled) pressReader else ActivationEvidence.NOT_APPLICABLE,
            ),
        )

    val overallState: ActivationState
        get() = activationState(
            listOf(
                root,
                injection,
                portalService,
                portalRoot,
                rootInput,
                if (thumbTriggerEnabled) pressReader else ActivationEvidence.NOT_APPLICABLE,
                overlayPermission,
            ),
        )
}

internal data class ActivationMonitorState(
    val snapshot: ActivationSnapshot?,
    val checking: Boolean,
    val refresh: () -> Unit,
    val overlayGranted: Boolean = snapshot?.overlayPermission == ActivationEvidence.READY,
    val portalInstalled: Boolean? = null,
    val aicrInstalled: Boolean? = null,
    val portalChecking: Boolean = false,
) {
    val refreshing: Boolean get() = checking || portalChecking
}

private data class ModuleCheck(
    val root: ModuleActivation.RootAccessOutcome,
    val portalInstalled: Boolean?,
    val aicrInstalled: Boolean?,
)

private fun packageInstalled(context: Context, packageName: String): Boolean? = try {
    context.packageManager.getApplicationInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
} catch (_: RuntimeException) {
    null
}

internal fun openOverlayPermissionSettings(context: Context) {
    val permission = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(permission)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "无法打开悬浮窗设置，请在系统设置中授权", Toast.LENGTH_LONG).show()
    }
}

private fun activationSnapshot(
    context: Context,
    rootOutcome: ModuleActivation.RootAccessOutcome?,
    desiredPortalEnabled: Boolean?,
    portalRestartPending: Boolean,
    portalChecking: Boolean,
    rootRevision: Int,
    expiredAtRevision: Int?,
): ActivationSnapshot {
    val injection = if (desiredPortalEnabled == true || portalRestartPending) {
        ModuleActivation.injectionStatus(context).evidence()
    } else if (desiredPortalEnabled == null) ActivationEvidence.UNKNOWN else ActivationEvidence.NOT_APPLICABLE
    // A saved switch cannot prove which hooks a still-running host actually loaded.
    val portalEnabled = if (portalRestartPending) injection == ActivationEvidence.READY else desiredPortalEnabled
    val hostRoot = if (portalEnabled == true) ModuleActivation.portalRootStatus(context).evidence()
        else ActivationEvidence.NOT_APPLICABLE
    return ActivationSnapshot(
        root = rootOutcome?.evidence() ?: ActivationEvidence.CHECKING,
        injection = if (portalEnabled == false) ActivationEvidence.NOT_APPLICABLE else injection,
        portalService = if (portalEnabled == true) ModuleActivation.portalServiceStatus(context).evidence() else ActivationEvidence.NOT_APPLICABLE,
        portalRoot = when {
            portalChecking && injection == ActivationEvidence.READY -> ActivationEvidence.CHECKING
            portalEnabled == true && expiredAtRevision != null &&
                    (rootRevision == expiredAtRevision || hostRoot == ActivationEvidence.CHECKING) -> ActivationEvidence.UNKNOWN
            else -> hostRoot
        },
        rootInput = if (portalEnabled == true) ModuleActivation.rootInputStatus(context).evidence() else ActivationEvidence.NOT_APPLICABLE,
        pressReader = if (portalEnabled == true && ThumbTriggerSettings.read(context).enabled) {
            ModuleActivation.pressReaderStatus(context).evidence()
        } else {
            ActivationEvidence.NOT_APPLICABLE
        },
        overlayPermission = if (Settings.canDrawOverlays(context)) {
            ActivationEvidence.READY
        } else {
            ActivationEvidence.UNAVAILABLE
        },
        thumbTriggerEnabled = ThumbTriggerSettings.read(context).enabled,
        copyDirectEnabled = IntegrationSettings.isCopyDirectEnabled(context),
        rootInputError = ModuleActivation.rootInputError(context),
        pressReaderError = ModuleActivation.pressReaderError(context),
        portalEnabled = portalEnabled,
        pendingPortalEnabled = desiredPortalEnabled.takeIf { portalRestartPending },
    )
}

@Composable
internal fun rememberActivationMonitor(context: Context, portalEnabled: Boolean? = true,
    portalRestartPending: Boolean = false): ActivationMonitorState {
    var moduleCheck by remember(context) { mutableStateOf<ModuleCheck?>(null) }
    var reportRevision by remember(context) { mutableIntStateOf(0) }
    var hostRootRevision by remember(context) { mutableIntStateOf(0) }
    var overlayGranted by remember(context) { mutableStateOf(Settings.canDrawOverlays(context)) }
    var requestGeneration by remember(context) { mutableIntStateOf(0) }
    var checkedGeneration by remember(context) { mutableIntStateOf(0) }
    var portalFinished by remember(context, portalEnabled, portalRestartPending) { mutableIntStateOf(0) }
    var expiredAtRevision by remember(context, portalEnabled, portalRestartPending, checkedGeneration) {
        mutableStateOf<Int?>(null)
    }
    val portalChecking by remember(context, portalEnabled, portalRestartPending) {
        derivedStateOf {
            (portalEnabled == true || portalRestartPending) && checkedGeneration != 0 &&
                checkedGeneration == requestGeneration && portalFinished != checkedGeneration
        }
    }
    val requests = remember(context) { Channel<Int>(Channel.CONFLATED) }
    val requestRefresh: () -> Unit = remember(requests) {
        {
            overlayGranted = Settings.canDrawOverlays(context)
            requestGeneration += 1
            requests.trySend(requestGeneration)
            Unit
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val recheck by rememberUpdatedState<() -> Unit> { if (!portalChecking) requestRefresh() }

    LaunchedEffect(context, requests) {
        if (requestGeneration == 0) requestRefresh()
        for (generation in requests) {
            val result = withContext(Dispatchers.IO) {
                ModuleCheck(
                    ModuleActivation.checkRootAccess(),
                    packageInstalled(context, "com.miui.contentextension"),
                    packageInstalled(context, "com.xiaomi.aicr"),
                )
            }
            if (generation == requestGeneration) {
                moduleCheck = result
                checkedGeneration = generation
            }
        }
    }

    var startupAttempted by remember(context, portalEnabled) { mutableStateOf(false) }
    LaunchedEffect(context, portalEnabled, portalRestartPending, checkedGeneration, portalChecking) {
        if (!portalChecking) return@LaunchedEffect
        val generation = checkedGeneration
        val needsStart = !portalRestartPending && !startupAttempted && moduleCheck?.portalInstalled != false
                && moduleCheck?.root == ModuleActivation.RootAccessOutcome.GRANTED
                && (ModuleActivation.injectionStatus(context) != ModuleActivation.ReportedStatus.READY
                || ModuleActivation.portalServiceStatus(context) != ModuleActivation.ReportedStatus.READY)
        if (needsStart) {
            startupAttempted = true
            withTimeoutOrNull(PORTAL_START_TIMEOUT_MS) {
                withContext(Dispatchers.IO) { ModuleActivation.requestPortalRuntimeStart(context) }
            }
        }
        val before = hostRootRevision
        if (withContext(Dispatchers.IO) { ModuleActivation.requestPortalRootRecheck(context) }) {
            // A host already starting its own probe can acknowledge before its first report.
            snapshotFlow { reportRevision }.first {
                ModuleActivation.injectionStatus(context) != ModuleActivation.ReportedStatus.READY ||
                    (hostRootRevision != before &&
                        ModuleActivation.portalRootStatus(context) != ModuleActivation.ReportedStatus.CHECKING)
            }
        }
        portalFinished = generation
    }
    DisposableEffect(context, portalEnabled, portalRestartPending, checkedGeneration, portalChecking) {
        val generation = checkedGeneration
        val handler = Handler(Looper.getMainLooper())
        val expire = Runnable {
            expiredAtRevision = hostRootRevision
            portalFinished = generation
        }
        if (portalChecking) handler.postDelayed(expire, PORTAL_ROUND_LIMIT_MS)
        onDispose { handler.removeCallbacks(expire) }
    }

    DisposableEffect(context, requests) {
        val preferences = context.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // Host evidence changes independently of MMGG's permission; never re-probe from a report.
            if (key == ModuleActivation.EXTRA_PORTAL_ROOT_OUTCOME || key == ModuleActivation.EXTRA_ROOT_CONNECTION_ATTEMPT) {
                hostRootRevision++
            }
            reportRevision++
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    DisposableEffect(lifecycleOwner, requests) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && checkedGeneration == requestGeneration) recheck()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snapshot = remember(context, moduleCheck, portalEnabled, portalRestartPending, reportRevision,
        overlayGranted, checkedGeneration, portalChecking, expiredAtRevision) {
        moduleCheck?.let { activationSnapshot(context, it.root, portalEnabled, portalRestartPending,
            portalChecking, hostRootRevision, expiredAtRevision) }
    }
    return ActivationMonitorState(snapshot, checkedGeneration != requestGeneration, recheck, overlayGranted,
        moduleCheck?.portalInstalled, moduleCheck?.aicrInstalled, portalChecking)
}
