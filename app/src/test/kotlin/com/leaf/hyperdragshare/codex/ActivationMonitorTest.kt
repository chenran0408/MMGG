package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowPackageManager
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(
    manifest = Config.NONE,
    sdk = [33],
    shadows = [ShadowModuleActivation::class],
)
class ActivationMonitorTest {
    private lateinit var activityController: org.robolectric.android.controller.ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private lateinit var composeView: ComposeView
    private val frames = BroadcastFrameClock()
    private var frameTime = 0L
    private lateinit var recomposer: Recomposer
    private lateinit var composeScope: CoroutineScope
    private val monitor = AtomicReference<ActivationMonitorState?>()
    private val compositions = AtomicInteger()
    private val portalEnabled = mutableStateOf<Boolean?>(true)
    private val portalRestartPending = mutableStateOf(false)
    private val probes = ShadowModuleActivation.probes

    @Before
    fun setUp() {
        ShadowModuleActivation.reset()
        activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = activityController.get()
        val dispatcher = Handler(Looper.getMainLooper()).asCoroutineDispatcher("activation-test")
        composeScope = CoroutineScope(dispatcher + frames + Job())
        recomposer = Recomposer(composeScope.coroutineContext)
        composeScope.launch { recomposer.runRecomposeAndApplyChanges() }
        composeView = ComposeView(activity)
        composeView.setParentCompositionContext(recomposer)
        activity.setContentView(composeView)
        composeView.setContent {
            val state = rememberActivationMonitor(activity, portalEnabled.value, portalRestartPending.value)
            SideEffect {
                monitor.set(state)
                compositions.incrementAndGet()
            }
        }
        settleCompose()
    }

    @After
    fun tearDown() {
        ShadowModuleActivation.releaseAll()
        if (::composeView.isInitialized) {
            runOnUi { composeView.disposeComposition() }
        }
        if (::recomposer.isInitialized) recomposer.cancel()
        if (::composeScope.isInitialized) composeScope.cancel()
        if (::activityController.isInitialized) {
            activityController.pause().stop().destroy()
        }
        ShadowLooper.shadowMainLooper().idle()
        ShadowModuleActivation.reset()
    }

    @Test
    fun staleRootCheckCannotPublishAndPortalReportsUpdateWithoutNewRootRequests() {
        val first = awaitProbe()
        awaitState { it.checking }
        val beforeRefresh = compositions.get()

        runOnUi { (monitor.get() ?: error("monitor was not composed")).refresh() }
        first.complete(ModuleActivation.RootAccessOutcome.UNKNOWN)

        val second = awaitProbe()
        awaitState { it.checking }
        assertTrue("stale first result must not publish a snapshot", monitor.get()?.snapshot == null)
        assertTrue("checking stays true until the newest request completes", monitor.get()?.checking == true)
        assertTrue(compositions.get() >= beforeRefresh)

        second.complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { it.snapshot?.root == ActivationEvidence.READY && !it.checking }

        val prefs = activity.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE)
        awaitState { !it.refreshing && ShadowModuleActivation.portalChecks.get() > 0 }
        val requests = ShadowModuleActivation.portalChecks.get()
        ShadowModuleActivation.portalRoot = ModuleActivation.ReportedStatus.READY
        runOnUi { prefs.edit().putBoolean("test_refresh_signal", true).commit() }
        awaitState { it.snapshot?.portalRoot == ActivationEvidence.READY && !it.checking }
        assertEquals(ActivationEvidence.READY, monitor.get()?.snapshot?.root)
        assertEquals("a host report must not ask the host to recheck itself", requests, ShadowModuleActivation.portalChecks.get())
        assertNull("publishing portal evidence must not prompt for MMGG Root", probes.poll(250, TimeUnit.MILLISECONDS))
    }

    @Test
    fun resumeAfterPauseRequestsFreshSnapshot() {
        val initial = awaitProbe()
        initial.complete(ModuleActivation.RootAccessOutcome.UNKNOWN)
        awaitState { it.snapshot != null && !it.refreshing && ShadowModuleActivation.portalChecks.get() == 1 }

        activityController.pause()
        ShadowLooper.shadowMainLooper().idle()
        activityController.resume()

        val resumed = awaitProbe()
        resumed.complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { it.snapshot?.root == ActivationEvidence.READY && !it.refreshing && ShadowModuleActivation.portalChecks.get() == 2 }
    }

    @Test
    fun repeatChecksJoinHostDispatchAndWaitingUntilHostDies() {
        val ack = CountDownLatch(1)
        ShadowModuleActivation.holdHostReply = ack
        ShadowModuleActivation.deferRootReports = true
        ShadowModuleActivation.portalRoot = ModuleActivation.ReportedStatus.READY
        awaitProbe().complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { it.portalChecking && it.snapshot?.root == ActivationEvidence.READY &&
            ShadowModuleActivation.portalChecks.get() == 1 }
        assertEquals(ActivationEvidence.CHECKING, monitor.get()?.snapshot?.portalRoot)
        runOnUi { monitor.get()!!.refresh() }
        assertNull("an unacknowledged request must not start a second su", probes.poll(80, TimeUnit.MILLISECONDS))

        ack.countDown()
        assertTrue(ShadowModuleActivation.hostAcknowledged.await(1, TimeUnit.SECONDS))
        reportPortalChange()
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(32))
        runOnUi { monitor.get()!!.refresh() }
        assertNull("ACK without new Root evidence must keep the round open", probes.poll(80, TimeUnit.MILLISECONDS))
        ShadowModuleActivation.portalRoot = ModuleActivation.ReportedStatus.CHECKING
        runOnUi {
            activity.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(ModuleActivation.EXTRA_PORTAL_ROOT_OUTCOME, "CHECKING").commit()
        }
        runOnUi { activityController.pause().resume(); monitor.get()!!.refresh() }
        assertNull("an active host check must coalesce resume and tap", probes.poll(80, TimeUnit.MILLISECONDS))
        assertEquals(1, ShadowModuleActivation.portalChecks.get())
        ShadowModuleActivation.injection = ModuleActivation.ReportedStatus.UNKNOWN
        reportPortalChange()
        awaitState { !it.refreshing && it.snapshot?.portalRoot == ActivationEvidence.UNKNOWN }
        assertEquals(ActivationEvidence.READY, monitor.get()?.snapshot?.root)
        runOnUi { monitor.get()!!.refresh() }
        awaitProbe().complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { !it.refreshing }
        assertEquals("a disconnected host must not receive a recheck", 1, ShadowModuleActivation.portalChecks.get())
    }

    @Test
    fun unsupportedHostOrCompletedUnknownProbeAllowsImmediateRetry() {
        ShadowModuleActivation.acceptHostRequest = false
        awaitProbe().complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { it.snapshot?.portalRoot == ActivationEvidence.UNKNOWN && !it.refreshing }
        ShadowModuleActivation.acceptHostRequest = true
        runOnUi { monitor.get()!!.refresh() }
        awaitProbe().complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { it.snapshot?.portalRoot == ActivationEvidence.UNKNOWN && !it.refreshing &&
            ShadowModuleActivation.portalChecks.get() == 1 }
        assertEquals(ActivationEvidence.READY, monitor.get()?.snapshot?.root)
    }

    @Test
    fun oldGrantDoesNotReturnAfterAQuietTimeoutAndLateConfirmationCanRestoreIt() {
        ShadowModuleActivation.portalRoot = ModuleActivation.ReportedStatus.READY
        awaitProbe().complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { !it.refreshing && it.snapshot?.portalRoot == ActivationEvidence.READY }
        ShadowModuleActivation.deferRootReports = true
        runOnUi { monitor.get()!!.refresh() }
        awaitProbe().complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { it.portalChecking && ShadowModuleActivation.portalChecks.get() == 2 }
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(16))
        awaitState { !it.refreshing && it.snapshot?.portalRoot == ActivationEvidence.UNKNOWN }
        reportPortalChange()
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(64))
        assertEquals("unrelated input reports cannot restore the cached grant",
            ActivationEvidence.UNKNOWN, monitor.get()?.snapshot?.portalRoot)
        val prefs = activity.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE)
        runOnUi { prefs.edit().putString(ModuleActivation.EXTRA_ROOT_CONNECTION_ATTEMPT,
            RootConnectionDiagnostics.newAttempt()).commit() }
        awaitState { !it.refreshing && it.snapshot?.portalRoot == ActivationEvidence.READY }
        assertEquals(2, ShadowModuleActivation.portalChecks.get())
    }

    @Test
    fun missingHostCompletionBecomesUnknownAndAllowsAnotherCheck() {
        ShadowModuleActivation.portalRoot = ModuleActivation.ReportedStatus.CHECKING
        awaitProbe().complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { it.portalChecking && ShadowModuleActivation.portalChecks.get() == 1 }
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(16))
        awaitState { !it.refreshing && it.snapshot?.portalRoot == ActivationEvidence.UNKNOWN }
        assertEquals(ActivationEvidence.READY, monitor.get()?.snapshot?.root)
        ShadowModuleActivation.portalRoot = ModuleActivation.ReportedStatus.READY
        runOnUi { monitor.get()!!.refresh() }
        awaitProbe().complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { !it.refreshing && it.snapshot?.portalRoot == ActivationEvidence.READY }
        assertEquals(2, ShadowModuleActivation.portalChecks.get())
    }

    @Test
    fun pendingPortalEnableWaitsForRestartAndCanBeUndoneWithoutStartingTheHost() {
        val pkg = "com.miui.contentextension"
        Shadow.extract<ShadowPackageManager>(activity.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg }
        })
        ShadowModuleActivation.injection = ModuleActivation.ReportedStatus.UNKNOWN
        runOnUi { portalRestartPending.value = true }
        awaitProbe().complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { !it.refreshing && it.snapshot?.pendingPortalEnabled == true }
        assertEquals(true, monitor.get()?.portalInstalled)
        assertEquals(ActivationEvidence.NOT_APPLICABLE, monitor.get()?.snapshot?.portalRoot)
        assertEquals(0, ShadowModuleActivation.portalStarts.get())
        runOnUi {
            portalEnabled.value = false
            portalRestartPending.value = false
        }
        awaitState { it.snapshot?.portalEnabled == false && it.snapshot?.pendingPortalEnabled == null }
        assertEquals(0, ShadowModuleActivation.portalChecks.get())
        assertEquals(0, ShadowModuleActivation.portalStarts.get())
    }

    @Test
    fun pendingDisableKeepsLiveHostEvidenceUntilRestartAppliesIt() {
        runOnUi {
            portalEnabled.value = false
            portalRestartPending.value = true
        }
        ShadowModuleActivation.portalRoot = ModuleActivation.ReportedStatus.READY
        awaitProbe().complete(ModuleActivation.RootAccessOutcome.UNKNOWN)
        awaitState { !it.refreshing && it.snapshot?.pendingPortalEnabled == false }
        assertEquals(ActivationEvidence.READY, monitor.get()?.snapshot?.portalRoot)
        assertEquals(ActivationEvidence.UNKNOWN, monitor.get()?.snapshot?.root)
        assertEquals(1, ShadowModuleActivation.portalChecks.get())
        runOnUi { portalRestartPending.value = false }
        awaitState { it.snapshot?.portalEnabled == false && it.snapshot?.pendingPortalEnabled == null }
        assertEquals(ActivationEvidence.NOT_APPLICABLE, monitor.get()?.snapshot?.portalRoot)
        assertEquals(1, ShadowModuleActivation.portalChecks.get())
    }

    private fun reportPortalChange() {
        val prefs = activity.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE)
        runOnUi { prefs.edit().putInt("host_event", prefs.getInt("host_event", 0) + 1).commit() }
    }

    @Test
    fun disabledPortalDoesNotRequestHostAuthorization() {
        val initial = awaitProbe()
        runOnUi { portalEnabled.value = false }
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(32))
        initial.complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { it.snapshot?.portalEnabled == false && !it.checking }
        assertEquals(ActivationEvidence.NOT_APPLICABLE, monitor.get()?.snapshot?.portalRoot)
        assertEquals(0, ShadowModuleActivation.portalChecks.get())
    }

    @Test
    fun disposingCompositionUnregistersRefreshSourcesAndIgnoresPendingResult() {
        val inFlight = awaitProbe()
        awaitState { it.checking }
        val compositionCount = compositions.get()

        runOnUi {
            (composeView.parent as ViewGroup).removeView(composeView)
            composeView.disposeComposition()
        }
        inFlight.complete(ModuleActivation.RootAccessOutcome.UNKNOWN)
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(100))
        assertEquals("disposed composition receives no state publication", compositionCount, compositions.get())

        val prefs = activity.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE)
        runOnUi {
            prefs.edit().putBoolean("test_after_dispose", true).commit()
            activityController.pause()
            activityController.resume()
        }
        assertNull("disposed listeners must not schedule a Root check", probes.poll(250, TimeUnit.MILLISECONDS))
    }

    private fun awaitProbe(): RootProbe {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            composeFrame()
            probes.poll(20, TimeUnit.MILLISECONDS)?.let { return it }
        }
        throw AssertionError("ModuleActivation.checkRootAccess was not called; last state: ${monitor.get()}")
    }

    private fun awaitState(predicate: (ActivationMonitorState) -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            composeFrame()
            val latest = monitor.get()
            if (latest != null && predicate(latest)) return
        }
        throw AssertionError("activation monitor state did not reach the expected value: ${monitor.get()}")
    }

    private fun settleCompose() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline && monitor.get() == null) {
            composeFrame()
        }
        assertNotNull("ComposeView should compose the monitor", monitor.get())
    }

    private fun composeFrame() {
        Snapshot.sendApplyNotifications()
        ShadowLooper.shadowMainLooper().idle()
        frameTime += 16_000_000L
        frames.sendFrame(frameTime)
        ShadowLooper.shadowMainLooper().idle()
    }

    private fun runOnUi(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            activity.runOnUiThread(action)
            ShadowLooper.shadowMainLooper().idle()
        }
    }
}

internal class RootProbe {
    private val completed = java.util.concurrent.CountDownLatch(1)
    private val outcome = AtomicReference<ModuleActivation.RootAccessOutcome?>()

    internal fun complete(value: ModuleActivation.RootAccessOutcome) {
        outcome.set(value)
        completed.countDown()
    }

    internal fun awaitOutcome(): ModuleActivation.RootAccessOutcome =
        if (completed.await(5, TimeUnit.SECONDS)) {
            outcome.get() ?: ModuleActivation.RootAccessOutcome.UNKNOWN
        } else {
            ModuleActivation.RootAccessOutcome.UNKNOWN
        }
}

@Implements(value = ModuleActivation::class, isInAndroidSdk = false)
internal class ShadowModuleActivation {
    companion object {
        @JvmField
        val probes = LinkedBlockingQueue<RootProbe>()
        val portalChecks = AtomicInteger()
        val portalStarts = AtomicInteger()
        @Volatile var holdHostReply: CountDownLatch? = null
        @Volatile var hostAcknowledged = CountDownLatch(1)
        @Volatile var deferRootReports = false
        @Volatile var acceptHostRequest = true
        @Volatile var injection = ModuleActivation.ReportedStatus.READY
        @Volatile var portalRoot = ModuleActivation.ReportedStatus.UNKNOWN
        private val activeProbes = java.util.concurrent.ConcurrentHashMap.newKeySet<RootProbe>()

        @JvmStatic
        @Implementation
        fun checkRootAccess(): ModuleActivation.RootAccessOutcome {
            val probe = RootProbe()
            activeProbes.add(probe)
            probes.put(probe)
            return try {
                probe.awaitOutcome()
            } finally {
                activeProbes.remove(probe)
            }
        }

        @JvmStatic
        @Implementation
        fun requestPortalRuntimeStart(context: Context): Boolean {
            portalStarts.incrementAndGet()
            return false
        }

        @JvmStatic @Implementation
        fun requestPortalRootRecheck(context: Context): Boolean {
            if (injection != ModuleActivation.ReportedStatus.READY || !acceptHostRequest) return false
            portalChecks.incrementAndGet()
            holdHostReply?.await(5, TimeUnit.SECONDS)
            hostAcknowledged.countDown()
            if (!deferRootReports) {
                val prefs = context.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(ModuleActivation.EXTRA_PORTAL_ROOT_OUTCOME, "CHECKING").commit()
                if (portalRoot != ModuleActivation.ReportedStatus.CHECKING) {
                    prefs.edit().putString(ModuleActivation.EXTRA_PORTAL_ROOT_OUTCOME, portalRoot.name).commit()
                }
            }
            return true
        }

        @JvmStatic @Implementation
        fun injectionStatus(context: Context) = injection

        @JvmStatic @Implementation
        fun portalServiceStatus(context: Context) = ModuleActivation.ReportedStatus.READY

        @JvmStatic @Implementation
        fun portalRootStatus(context: Context) = if (injection == ModuleActivation.ReportedStatus.READY) portalRoot
            else ModuleActivation.ReportedStatus.UNKNOWN

        @JvmStatic
        @Resetter
        fun reset() {
            releaseAll()
            probes.clear()
            portalChecks.set(0)
            portalStarts.set(0)
            holdHostReply = null
            hostAcknowledged = CountDownLatch(1)
            deferRootReports = false
            acceptHostRequest = true
            injection = ModuleActivation.ReportedStatus.READY
            portalRoot = ModuleActivation.ReportedStatus.UNKNOWN
        }

        fun releaseAll() {
            holdHostReply?.countDown()
            activeProbes.forEach { it.complete(ModuleActivation.RootAccessOutcome.UNKNOWN) }
            while (true) {
                val probe = probes.poll() ?: break
                probe.complete(ModuleActivation.RootAccessOutcome.UNKNOWN)
            }
        }
    }
}
