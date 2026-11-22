package com.leaf.hyperdragshare.codex

import com.leaf.hyperdragshare.gestures.config.AppTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GestureHoneycombPreviewTest {
    @Test
    fun builtInCellsShowTheirLabelsAndIconsWithoutAppActivities() {
        val context = RuntimeEnvironment.getApplication()
        listOf(AppTarget.regionOcr() to "全屏识字", AppTarget.moreApps() to "更多应用").forEach { (target, expected) ->
            val (label, icon) = resolveHoneycombCell(context, target)
            assertEquals(expected, label)
            assertNotNull("The saved $expected cell must not be rendered as an empty circle", icon)
        }
    }
}
