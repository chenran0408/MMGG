package com.leaf.hyperdragshare.codex

import android.content.Context
import android.os.Bundle
import com.leaf.hyperdragshare.gestures.config.ConfigContract
import com.leaf.hyperdragshare.gestures.config.ConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import java.time.Duration
import java.util.LinkedHashSet

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SettingsEffectNoticeTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun themeChangeReportsImmediateAppAndNextOverlayEffect() {
        val previous = DragShareSettings.defaults()
        val next = changedSettings {
            putInt("color_mode", DragShareSettings.COLOR_DARK)
        }

        saveDragShareSettingsWithNotice(context, previous, next)

        assertToastEquals("主题已更新，新打开的浮窗将使用此主题。")
        assertEquals(DragShareSettings.COLOR_DARK, DragShareSettings.readLocal(context).colorMode)
    }

    @Test
    fun loggingChangeReportsImmediateEffectInsteadOfPortalSessionEffect() {
        val previous = DragShareSettings.defaults()
        val next = changedSettings {
            putInt("log_level", DragShareSettings.LOG_LEVEL_DEBUG)
            putInt("log_destination", DragShareSettings.LOG_DESTINATION_FILE)
        }

        saveDragShareSettingsWithNotice(context, previous, next)

        assertToastEquals("日志设置已生效。")
    }

    @Test
    fun portalSettingReportsNextSessionWithoutRestart() {
        val previous = DragShareSettings.defaults()
        val next = changedSettings {
            putInt("simple_menu_position", DragShareSettings.SIMPLE_MENU_POSITION_TOP)
        }

        saveDragShareSettingsWithNotice(context, previous, next)

        assertToastEquals("下次取词或选图时生效。")
    }

    @Test
    fun blacklistChangeNamesBothOperationsThatReadTheList() {
        val previous = DragShareSettings.defaults()
        val next = changedSettings {
            putStringArrayList("accessibility_blacklisted_packages", arrayListOf("example.game"))
        }

        saveDragShareSettingsWithNotice(context, previous, next)

        assertToastEquals("应用避让名单在下次取词或手势时生效。")
    }

    @Test
    fun configStoreWriteReportsNextGestureWithoutClaimingRuntimeConfirmation() {
        val store = ConfigStore(context)

        saveGestureBooleanSetting(context, store, ConfigContract.KEY_ENABLED, true)

        // ConfigStore coalesces writes for 120 ms before persisting and notifying hosts.
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(120))
        assertTrue(store.preferences().getBoolean(ConfigContract.KEY_ENABLED, false))
        assertToastEquals("已保存，下次手势生效")
    }

    @Test
    fun consecutiveUiMutationsPreserveScalarCollectionsAndNotices() {
        val original = DragShareSettings.defaults().toBuilder()
            .setHiddenTargetKeys(linkedSetOf("target.initial-hidden"))
            .setTargetOrder(listOf("target.first", "target.second"))
            .setAccessibilityBlacklistedPackages(linkedSetOf("app.initial-blocked"))
            .setManualGamePackages(linkedSetOf("app.initial-game"))
            .build()
        val previous = DragShareSettings.readLocal(context)
        try {
            original.saveLocal(context)
            var current = DragShareSettings.readLocal(context)
            val persist: (DragShareSettingsMutation) -> DragShareSettings = { mutation ->
                updateDragShareSettingsWithNotice(context, current, mutation).also {
                    current = it
                }
            }

            // Separate calls model callbacks from the settings pages.
            persist { setColorMode(DragShareSettings.COLOR_DARK) }
            assertToastEquals("主题已更新，新打开的浮窗将使用此主题。")
            persist { setEdgeTriggerDp(123) }
            persist { setTwoFingerLongPressTimeoutMillis(900) }
            assertToastEquals("下次长按时生效。")
            persist { setTwoFingerLongPressTimeoutMillis(900) }

            persist { settings ->
                val hidden = LinkedHashSet(settings.hiddenTargetKeys)
                hidden.add("target.added-hidden")
                setHiddenTargetKeys(hidden)
            }
            persist { settings ->
                val hidden = LinkedHashSet(settings.hiddenTargetKeys)
                hidden.remove("target.initial-hidden")
                setHiddenTargetKeys(hidden)
            }
            persist { setTargetOrder(listOf("target.second", "target.first")) }

            persist { settings ->
                val packages = LinkedHashSet(settings.accessibilityBlacklistedPackages)
                packages.add("app.added-blocked")
                setAccessibilityBlacklistedPackages(packages)
            }
            assertToastEquals("应用避让名单在下次取词或手势时生效。")
            persist { settings ->
                val packages = LinkedHashSet(settings.accessibilityBlacklistedPackages)
                packages.remove("app.initial-blocked")
                setAccessibilityBlacklistedPackages(packages)
            }
            persist { settings ->
                val packages = LinkedHashSet(settings.manualGamePackages)
                packages.add("app.added-game")
                setManualGamePackages(packages)
            }
            assertToastEquals("应用避让名单在下次取词或手势时生效。")

            val saved = DragShareSettings.readLocal(context)
            assertEquals(DragShareSettings.COLOR_DARK, current.colorMode)
            assertEquals(DragShareSettings.COLOR_DARK, saved.colorMode)
            assertEquals(123, saved.edgeTriggerDp)
            assertEquals(900, saved.twoFingerLongPressTimeoutMillis)
            assertEquals(setOf("target.added-hidden"), saved.hiddenTargetKeys)
            assertEquals(listOf("target.second", "target.first"), saved.targetOrder)
            assertEquals(setOf("app.added-blocked"), saved.accessibilityBlacklistedPackages)
            assertEquals(setOf("app.initial-game", "app.added-game"), saved.manualGamePackages)
            assertToastEquals("应用避让名单在下次取词或手势时生效。")
        } finally {
            previous.saveLocal(context)
        }
    }

    private fun changedSettings(mutate: Bundle.() -> Unit): DragShareSettings {
        val bundle = DragShareSettings.defaults().toBundle()
        mutate(bundle)
        return DragShareSettings.fromBundle(bundle)
    }

    @Test
    fun savingSeveralSettingsReportsImmediateAndNextOperationSeparately() {
        val previous = DragShareSettings.defaults()
        val next = previous.toBuilder()
            .setLogLevel(DragShareSettings.LOG_LEVEL_DEBUG)
            .setTwoFingerLongPressTimeoutMillis(900)
            .build()

        saveDragShareSettingsWithNotice(context, previous, next)

        assertToastEquals("日志设置已生效。 下次长按时生效。")
        val saved = DragShareSettings.readLocal(context)
        assertEquals(DragShareSettings.LOG_LEVEL_DEBUG, saved.logLevel)
        assertEquals(900, saved.twoFingerLongPressTimeoutMillis)
    }

    private fun assertToastEquals(text: String) {
        ShadowLooper.shadowMainLooper().idle()
        assertEquals(text, ShadowToast.getTextOfLatestToast())
    }
}
