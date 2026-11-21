package com.leaf.hyperdragshare.gestures.xposed;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class FreeformHookProbeTest {
    public static class SampleController {
        public Object createEventReceiver() {
            return null;
        }

        public void registerEventHandler(SampleHandler handler) {
        }

        public void unrelated(int value) {
        }
    }

    public static class SampleHandler {
    }

    public static class Nested {
        public static class Inner {
        }
    }

    @Test public void methodSummaryListsNameAndAritySorted() {
        String summary = FreeformHookProbe.methodSummary(SampleController.class);
        assertTrue(summary.contains("createEventReceiver/0"));
        assertTrue(summary.contains("registerEventHandler/1"));
        assertTrue(summary.contains("unrelated/1"));
        int create = summary.indexOf("createEventReceiver/0");
        int register = summary.indexOf("registerEventHandler/1");
        int unrelated = summary.indexOf("unrelated/1");
        assertTrue(create < register && register < unrelated);
    }

    @Test public void nestedClassSummaryListsNestedNames() {
        String summary = FreeformHookProbe.nestedClassSummary(Nested.class);
        assertTrue(summary.contains(Nested.Inner.class.getName()));
    }

    @Test public void acceptsHandlerRequiresSingleAssignableNonObjectParameter() throws Exception {
        Method register = SampleController.class.getDeclaredMethod(
                "registerEventHandler", SampleHandler.class);
        Method unrelated = SampleController.class.getDeclaredMethod("unrelated", int.class);
        assertTrue(FreeformHookProbe.acceptsHandler(register, SampleHandler.class));
        assertFalse(FreeformHookProbe.acceptsHandler(unrelated, SampleHandler.class));
    }

    @Test public void receiverMethodPrefersExactName() {
        assertEquals("createEventReceiver",
                FreeformHookProbe.findEventReceiverMethod(SampleController.class));
    }

    @Test public void registrationMethodPrefersExactName() {
        assertEquals("registerEventHandler",
                FreeformHookProbe.findEventHandlerRegistrationMethod(
                        SampleController.class, SampleHandler.class));
    }

    @Test public void missingMethodsReturnNull() {
        assertNull(FreeformHookProbe.findEventHandlerRegistrationMethod(
                SampleHandler.class, SampleHandler.class));
    }
}
