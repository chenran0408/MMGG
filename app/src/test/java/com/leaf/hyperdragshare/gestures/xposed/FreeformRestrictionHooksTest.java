package com.leaf.hyperdragshare.gestures.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BooleanSupplier;

import org.junit.Test;

public final class FreeformRestrictionHooksTest {
    @Test public void referenceCountMatchesXiaomiHookEvidence() {
        assertEquals(128, FreeformRestrictionHooks.REFERENCE_MAX_FREEFORM_STACK_COUNT);
    }

    @Test public void restrictionSwitchesFailOpenWhenDisabledOrUnavailable() {
        assertFalse(FreeformRestrictionHooks.isEnabled(() -> false));
        assertTrue(FreeformRestrictionHooks.isEnabled(() -> true));
        assertFalse(FreeformRestrictionHooks.isEnabled(null));
        assertFalse(FreeformRestrictionHooks.isEnabled(() -> {
            throw new IllegalStateException("unavailable");
        }));
    }

    @Test public void blacklistContractOnlyAcceptsCollectionReturnTypes() throws Exception {
        assertTrue(FreeformRestrictionHooks.supportsBlacklist(
                Contract.class.getDeclaredMethod("arrayList")));
        assertTrue(FreeformRestrictionHooks.supportsBlacklist(
                Contract.class.getDeclaredMethod("collection")));
        assertFalse(FreeformRestrictionHooks.supportsBlacklist(
                Contract.class.getDeclaredMethod("object")));
    }

    @Test public void booleanContractRequiresZeroArguments() throws Exception {
        assertTrue(FreeformRestrictionHooks.supportsBoolean(
                Contract.class.getDeclaredMethod("flag")));
        assertFalse(FreeformRestrictionHooks.supportsBoolean(
                Contract.class.getDeclaredMethod("flagWithArgument", boolean.class)));
    }

    @Test public void countContractRequiresExactArgumentsAndIntegerResult() throws Exception {
        assertTrue(FreeformRestrictionHooks.supportsInt(
                Contract.class.getDeclaredMethod("maxCount", String.class, ActivityStack.class),
                ActivityStack.class));
        assertFalse(FreeformRestrictionHooks.supportsInt(
                Contract.class.getDeclaredMethod("wrongCount", Object.class, ActivityStack.class),
                ActivityStack.class));
        assertFalse(FreeformRestrictionHooks.supportsInt(
                Contract.class.getDeclaredMethod("wrongResult", String.class, ActivityStack.class),
                ActivityStack.class));
    }

    private static final class Contract {
        public static ArrayList<String> arrayList() { return new ArrayList<>(); }
        public static Collection<String> collection() { return new ArrayList<>(); }
        public static Object object() { return null; }
        public static boolean flag() { return false; }
        public static boolean flagWithArgument(boolean ignored) { return ignored; }
        public static int maxCount(String packageName, ActivityStack stack) { return 0; }
        public static int wrongCount(Object packageName, ActivityStack stack) { return 0; }
        public static long wrongResult(String packageName, ActivityStack stack) { return 0L; }
    }

    private static final class ActivityStack {}
}
