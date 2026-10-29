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
    THEME("已保存：应用主题立即更新，新建的浮窗也会使用该主题。无需重启。"),
    LOGGING("已保存：日志等级和保存位置已即时应用。无需重启。"),
    GESTURE_EXCLUSIONS(
        "已保存：传送门下次读取避让名单；系统界面（SystemUI）在后续手势读取更新后的名单。无需重启。",
    ),
    ROOT_INPUT("已保存：根输入（Root）会在下一次按压确认时读取双指长按设置。"),
    PORTAL_SESSION("已保存：传送门在下一次取词或选图时读取；当前操作不变。无需重启作用域。"),
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

    if (previous.twoFingerLongPressEnabled != next.twoFingerLongPressEnabled
        || previous.twoFingerLongPressTimeoutMillis != next.twoFingerLongPressTimeoutMillis
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
        listOf("设置已保存。")
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
        "已保存；系统界面（SystemUI）会在后续手势读取新设置，无需重启。",
        Toast.LENGTH_SHORT,
    ).show()
}
