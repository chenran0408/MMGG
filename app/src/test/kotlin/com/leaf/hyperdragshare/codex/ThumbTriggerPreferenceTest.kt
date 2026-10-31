package com.leaf.hyperdragshare.codex

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.ComposeView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ThumbTriggerPreferenceTest {
    private lateinit var host: org.robolectric.android.controller.ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private lateinit var view: ComposeView
    private var entries = emptyList<SettingEntry>()
    private var compositions = 0
    private val token = Binder()

    @Before fun setUp() {
        host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = host.get()
        ModuleActivation.clearPortalReport(activity)
        activity.getSharedPreferences("drag_share_settings", Context.MODE_PRIVATE).edit().clear().commit()
        view = ComposeView(activity)
        activity.setContentView(view)
        view.setContent {
            val current = thumbTriggerEntries(activity, DragShareSettings.defaults()) { }
            SideEffect { entries = current; compositions++ }
        }
        await { entries.isNotEmpty() }
    }

    @After fun tearDown() {
        view.disposeComposition()
        host.pause().stop().destroy()
        ModuleActivation.clearPortalReport(activity)
        ShadowLooper.shadowMainLooper().idle()
    }

    @Test fun unknownStatusDoesNotClaimRootDenialOrBlockSavingTheSwitch() {
        val entry = entries.first { it.id == "press.enabled" }
        assertTrue(entry.enabled)
        assertNull(entry.prerequisite)
        assertFalse(entry.summary.contains("授予 Root"))
        assertTrue(entry.summary.contains("待确认"))
    }

    @Test fun authorizationReportRefreshesVisiblePageWithoutLeavingIt() {
        report(ModuleActivation.RootAccessOutcome.DENIED)
        await { entries.first().summary.contains("Root") }
        report(ModuleActivation.RootAccessOutcome.GRANTED)
        await { entries.first().summary == "静止按住取词，移动取消" }
        assertNull(entries.first().prerequisite)
        report(ModuleActivation.RootAccessOutcome.UNKNOWN)
        await { entries.first().summary.contains("暂时无法确认") }
        assertFalse(entries.first().summary.contains("授予 Root"))
    }

    @Test fun disposedPageDoesNotReceiveLateStatusReports() {
        (view.parent as ViewGroup).removeView(view)
        view.disposeComposition()
        val count = compositions
        report(ModuleActivation.RootAccessOutcome.GRANTED)
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(100))
        assertEquals(count, compositions)
    }

    private fun report(outcome: ModuleActivation.RootAccessOutcome) {
        val data = Bundle().apply {
            putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE.toLong())
            putInt(ModuleActivation.EXTRA_PROCESS_ID, 42)
            putBinder(ModuleActivation.EXTRA_LIFETIME_TOKEN, token)
            putString(ModuleActivation.EXTRA_PORTAL_ROOT_OUTCOME, outcome.name)
        }
        ModuleActivation.recordInjected(activity, data)
    }

    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < until) {
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(16))
            if (condition()) return
        }
        fail("Settings did not reflect the current Portal report: $entries")
    }
}
