package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

private const val ACTIVATION_SETTLE_CHECKS = 20

private const val ACTIVATION_SETTLE_INTERVAL_MS = 100L

private const val PORTAL_START_TIMEOUT_MS = 10_000L

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
    val portalEnabled: Boolean = true,
) {
    val portalFeature: ActivationState
        get() = activationFeatureState(
            enabled = portalEnabled,
            dependencies = listOf(injection, portalService, portalRoot, rootInput),
        )

    val pressFeature: ActivationState
        get() = activationFeatureState(
            enabled = portalEnabled,
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
)

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
    portalEnabled: Boolean,
): ActivationSnapshot = ActivationSnapshot(
    root = rootOutcome?.evidence() ?: ActivationEvidence.CHECKING,
    injection = if (portalEnabled) ModuleActivation.injectionStatus(context).evidence() else ActivationEvidence.NOT_APPLICABLE,
    portalService = if (portalEnabled) ModuleActivation.portalServiceStatus(context).evidence() else ActivationEvidence.NOT_APPLICABLE,
    portalRoot = if (portalEnabled) ModuleActivation.portalRootStatus(context).evidence() else ActivationEvidence.NOT_APPLICABLE,
    rootInput = if (portalEnabled) ModuleActivation.rootInputStatus(context).evidence() else ActivationEvidence.NOT_APPLICABLE,
    pressReader = if (portalEnabled && ThumbTriggerSettings.read(context).enabled) {
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
)

@Composable
internal fun rememberActivationMonitor(context: Context, portalEnabled: Boolean? = true): ActivationMonitorState {
    var snapshot by remember(context) { mutableStateOf<ActivationSnapshot?>(null) }
    var checking by remember(context) { mutableStateOf(false) }
    var overlayGranted by remember(context) { mutableStateOf(Settings.canDrawOverlays(context)) }
    var requestGeneration by remember(context) { mutableIntStateOf(0) }
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

    LaunchedEffect(context, requests, portalEnabled) {
        var startupAttempted = false
        requestRefresh()
        for (generation in requests) {
            checking = true
            val rootOutcome = withContext(Dispatchers.IO) { ModuleActivation.checkRootAccess() }
            if (portalEnabled == true && !startupAttempted) {
                startupAttempted = true
                val beforeStart = activationSnapshot(context, rootOutcome, true)
                if (rootOutcome == ModuleActivation.RootAccessOutcome.GRANTED
                    && (beforeStart.injection != ActivationEvidence.READY
                        || beforeStart.portalService != ActivationEvidence.READY)
                ) {
                    withTimeoutOrNull(PORTAL_START_TIMEOUT_MS) {
                        withContext(Dispatchers.IO) {
                            ModuleActivation.requestPortalRuntimeStart(context)
                        }
                    }
                    for (attempt in 0 until ACTIVATION_SETTLE_CHECKS) {
                        val current = activationSnapshot(context, rootOutcome, true)
                        if (current.injection == ActivationEvidence.READY
                            && current.portalService == ActivationEvidence.READY
                        ) break
                        delay(ACTIVATION_SETTLE_INTERVAL_MS)
                    }
                }
            }
            if (generation == requestGeneration) {
                snapshot = activationSnapshot(context, rootOutcome, portalEnabled != false)
                checking = false
            }
        }
    }

    DisposableEffect(context, requests) {
        val preferences = context.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            requestRefresh()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    DisposableEffect(lifecycleOwner, requests) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) requestRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val visibleSnapshot = snapshot?.copy(overlayPermission =
        if (overlayGranted) ActivationEvidence.READY else ActivationEvidence.UNAVAILABLE)
    return ActivationMonitorState(visibleSnapshot, checking, requestRefresh, overlayGranted)
}
