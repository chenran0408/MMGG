package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsEffectNoticePolicyTest {
    private val defaults = DragShareSettings.defaults()

    @Test
    fun unchangedSettingsProduceNoNotices() {
        assertEquals(emptyList<SettingsEffectNotice>(), classifySettingsEffectNotices(defaults, defaults))
    }

    @Test
    fun eachChangeCategoryProducesOnlyItsOwnNotice() {
        assertEquals(
            listOf(SettingsEffectNotice.THEME),
            classifySettingsEffectNotices(defaults, changed { setColorMode(DragShareSettings.COLOR_DARK) }),
        )
        assertEquals(
            listOf(SettingsEffectNotice.LOGGING),
            classifySettingsEffectNotices(defaults, changed { setLogLevel(DragShareSettings.LOG_LEVEL_DEBUG) }),
        )
        assertEquals(
            listOf(SettingsEffectNotice.GESTURE_EXCLUSIONS),
            classifySettingsEffectNotices(defaults, changed { setManualGamePackages(setOf("example.game")) }),
        )
        assertEquals(
            listOf(SettingsEffectNotice.ROOT_INPUT),
            classifySettingsEffectNotices(defaults, changed { setTwoFingerLongPressEnabled(true) }),
        )
        assertEquals(
            listOf(SettingsEffectNotice.PORTAL_SESSION),
            classifySettingsEffectNotices(defaults, changed {
                setSimpleMenuPosition(DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM)
            }),
        )
    }

    @Test
    fun combinedChangesKeepUserFacingOrderAndMessages() {
        val next = changed {
            setColorMode(DragShareSettings.COLOR_DARK)
            setLogDestination(DragShareSettings.LOG_DESTINATION_FILE)
            setAccessibilityBlacklistedPackages(setOf("example.game"))
            setTwoFingerLongPressTimeoutMillis(900)
            setSimpleMenuPosition(DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM)
        }

        val notices = classifySettingsEffectNotices(defaults, next)

        assertEquals(
            listOf(
                SettingsEffectNotice.THEME,
                SettingsEffectNotice.LOGGING,
                SettingsEffectNotice.GESTURE_EXCLUSIONS,
                SettingsEffectNotice.ROOT_INPUT,
                SettingsEffectNotice.PORTAL_SESSION,
            ),
            notices,
        )
        assertEquals(notices.map { it.message }, notices.map(SettingsEffectNotice::message))
    }

    private fun changed(mutate: DragShareSettings.Builder.() -> Unit): DragShareSettings =
        defaults.toBuilder().apply(mutate).build()
}
