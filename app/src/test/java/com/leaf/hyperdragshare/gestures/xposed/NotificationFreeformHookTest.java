package com.leaf.hyperdragshare.gestures.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Pair;

import java.util.function.BooleanSupplier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static android.graphics.Typeface.BOLD;

@RunWith(RobolectricTestRunner.class)
public final class NotificationFreeformHookTest {
    @Test public void notificationSwitchIsEvaluatedDynamicallyAndFailsClosed() {
        boolean[] enabled = { false };
        BooleanSupplier supplier = () -> enabled[0];
        assertEquals(false, NotificationFreeformHook.isEnabled(supplier));
        enabled[0] = true;
        assertTrue(NotificationFreeformHook.isEnabled(supplier));
        assertEquals(false, NotificationFreeformHook.isEnabled(null));
        assertEquals(false, NotificationFreeformHook.isEnabled(() -> {
            throw new IllegalStateException("unavailable");
        }));
    }

    @Test public void onlyMatchingSystemUiActionIsAccepted() {
        assertTrue(NotificationFreeformActionPolicy.matches(true,
                NotificationFreeformActionPolicy.ACTION_VIEW_ID,
                "小窗", "小窗"));
        assertEquals(false, NotificationFreeformActionPolicy.matches(false,
                NotificationFreeformActionPolicy.ACTION_VIEW_ID,
                "小窗", "小窗"));
        assertEquals(false, NotificationFreeformActionPolicy.matches(true, "other_action",
                "小窗", "小窗"));
        assertEquals(false, NotificationFreeformActionPolicy.matches(true, "miui_action",
                "打开", "小窗"));
        assertEquals(false, NotificationFreeformActionPolicy.matches(true, "miui_action",
                "小窗", null));
    }

    @Test public void mergesXiaomiOptionsAndRetainsOriginalFillIntent() {
        Intent fillIntent = new Intent("original.fill");
        ActivityOptions original = ActivityOptions.makeBasic();
        original.setLaunchDisplayId(3);
        ActivityOptions xiaomi = ActivityOptions.makeBasic();
        Pair<Intent, ActivityOptions> source = new Pair<>(fillIntent, original);

        Pair<?, ?> result = NotificationFreeformHook.mergeOptionsPair(source, xiaomi);

        assertSame(fillIntent, result.first);
        Bundle merged = ((ActivityOptions) result.second).toBundle();
        assertEquals(3, merged.getInt("android.activity.launchDisplayId"));
    }

    @Test public void matchingActionAllowsStyledTextWithTheSameLabel() {
        SpannableString styled = new SpannableString("小窗");
        styled.setSpan(new StyleSpan(BOLD), 0, styled.length(), 0);

        assertTrue(NotificationFreeformActionPolicy.matches(true,
                NotificationFreeformActionPolicy.ACTION_VIEW_ID, styled, "小窗"));
    }

    @Test public void absentOptionsOrUnknownOptionStructureLeavesOriginalPairAlone() {
        Pair<Object, Object> source = new Pair<>(new Object(), new Object());
        assertSame(source, NotificationFreeformHook.mergeOptionsPair(source, null));
        assertSame(source, NotificationFreeformHook.mergeOptionsPair(source, ActivityOptions.makeBasic()));
        assertNull(NotificationFreeformHook.mergeOptionsPair(null, null));
    }
}
