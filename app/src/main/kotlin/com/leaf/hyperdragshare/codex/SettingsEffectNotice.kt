package com.leaf.hyperdragshare.codex

import android.content.Context
import android.widget.Toast
import com.leaf.hyperdragshare.gestures.config.ConfigStore

internal typealias DragShareSettingsMutation =
    DragShareSettings.Builder.(current: DragShareSettings) -> Unit

/**
 * Pure descriptions of when saved settings take effect and which consumers read
 * them. Keep the order aligned with the user-facing notice order below.
 */
internal enum class SettingsEffectNotice(
    val message: String,
) {
    THEME("主题已更新，新打开的浮窗将使用此主题。"),
    LOGGING("日志设置已生效。"),
    GESTURE_EXCLUSIONS("应用避让名单在下次取词或手势时生效。"),
    ROOT_INPUT("下次长按时生效。"),
    PORTAL_SESSION("下次取词或选图时生效。"),
}

/** Classifies settings changes without saving, rendering, or consulting Android APIs. */
internal fun classifySettingsEffectNotices(
    previous: DragShareSettings,
    next: DragShareSettings,
): List<SettingsEffectNotice> = buildList {
    if (previous.colorMode != next.colorMode) add(SettingsEffectNotice.THEME)

    if (previous.logLevel != next.logLevel
        || previous.logDestination != next.logDestination
    ) {
        add(SettingsEffectNotice.LOGGING)
    }

    if (previous.pauseAutomaticInGames != next.pauseAutomaticInGames
        || previous.manualGamePackages != next.manualGamePackages
        || previous.accessibilityBlacklistedPackages != next.accessibilityBlacklistedPackages
    ) {
        add(SettingsEffectNotice.GESTURE_EXCLUSIONS)
    }

    if (previous.twoFingerLongPressTimeoutMillis != next.twoFingerLongPressTimeoutMillis
        || previous.disableSingleFingerInLandscape != next.disableSingleFingerInLandscape
    ) {
        add(SettingsEffectNotice.ROOT_INPUT)
    }

    if (previous.contentCaptureMode != next.contentCaptureMode
        || previous.uiStyle != next.uiStyle
        || previous.edgeTriggerDp != next.edgeTriggerDp
        || previous.scrollSpeedDpPerSecond != next.scrollSpeedDpPerSecond
        || previous.blockBackgroundScroll != next.blockBackgroundScroll
        || previous.textSharingEnabled != next.textSharingEnabled
        || previous.imageSharingEnabled != next.imageSharingEnabled
        || previous.simpleMenuPosition != next.simpleMenuPosition
        || previous.simpleMenuOpacityPercent != next.simpleMenuOpacityPercent
        || previous.simpleMenuCornerRadiusDp != next.simpleMenuCornerRadiusDp
        || previous.simpleMenuEdgeDistanceDp != next.simpleMenuEdgeDistanceDp
        || previous.iconOpacityPercent != next.iconOpacityPercent
        || previous.modernBlurRadiusDp != next.modernBlurRadiusDp
        || previous.modernGlassOpacityPercent != next.modernGlassOpacityPercent
        || previous.closeMenuWhenPointerLeaves != next.closeMenuWhenPointerLeaves
        || previous.hiddenTargetKeys != next.hiddenTargetKeys
        || previous.targetOrder != next.targetOrder
        || previous.accessibilityLandscapeRecognitionEnabled != next.accessibilityLandscapeRecognitionEnabled
        || previous.accessibilityLongPressTimeoutMillis != next.accessibilityLongPressTimeoutMillis
        || previous.accessibilityRecognitionSensitivityPercent != next.accessibilityRecognitionSensitivityPercent
    ) {
        add(SettingsEffectNotice.PORTAL_SESSION)
    }
}

internal fun updateDragShareSettingsWithNotice(
    context: Context,
    current: DragShareSettings,
    mutation: DragShareSettingsMutation,
): DragShareSettings {
    val builder = current.toBuilder()
    builder.mutation(current)
    val next = builder.build()
    saveDragShareSettingsWithNotice(context, current, next)
    return next
}

/** Saves portal settings and tells the user when the real consumers will read them. */
internal fun saveDragShareSettingsWithNotice(
    context: Context,
    previous: DragShareSettings,
    next: DragShareSettings,
) {
    next.saveLocal(context)

    val notices = classifySettingsEffectNotices(previous, next)
    val messages = if (notices.isEmpty()) {
        listOf("设置已保存")
    } else {
        notices.map(SettingsEffectNotice::message)
    }
    Toast.makeText(
        context,
        messages.joinToString(" "),
        if (messages.size > 1 || messages.first().length > 45) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
    ).show()
}

/** Writes one live gesture preference through ConfigStore and reports its actual host scope. */
internal fun saveGestureBooleanSetting(
    context: Context,
    store: ConfigStore,
    key: String,
    value: Boolean,
) {
    store.putBoolean(key, value)
    showGestureConfigSaved(context)
}

internal fun saveGestureIntegerSetting(
    context: Context,
    store: ConfigStore,
    key: String,
    value: Int,
) {
    store.putInt(key, value)
    showGestureConfigSaved(context)
}

internal fun showGestureConfigSaved(context: Context) {
    Toast.makeText(
        context,
        "已保存，下次手势生效",
        Toast.LENGTH_SHORT,
    ).show()
}
