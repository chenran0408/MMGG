package com.leaf.hyperdragshare.gestures.xposed;

/** Small policy boundary for the SystemUI action identity check. */
final class NotificationFreeformActionPolicy {
    static final String ACTION_VIEW_ID = "miui_action";

    private NotificationFreeformActionPolicy() {}

    static boolean matches(boolean enabled, String viewId, CharSequence text,
            CharSequence expectedText) {
        return enabled && ACTION_VIEW_ID.equals(viewId)
                && text != null && expectedText != null
                && expectedText.toString().contentEquals(text);
    }
}
