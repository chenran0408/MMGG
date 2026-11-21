package com.leaf.hyperdragshare.gestures.xposed;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import com.leaf.hyperdragshare.compat.xposed.XposedHelpers;

/**
 * Stateless classloader probing and reflection summaries used by the freeform
 * hooks to report which HyperOS SystemUI classes and methods are available.
 */
final class FreeformHookProbe {
    private FreeformHookProbe() {
    }

    static void probeSystemUiInterfaces(ClassLoader classLoader, String[] inputControllerCandidates,
                                        String eventHandler, String freeformController,
                                        String freeformPinHandler, String freeformAnimation) {
        probeClass(classLoader, "inputController", inputControllerCandidates);
        probeClass(classLoader, "inputHandler", eventHandler);
        probeClass(classLoader, "freeformController", freeformController);
        probeClass(classLoader, "freeformPinHandler", freeformPinHandler);
        probeClass(classLoader, "freeformAnimation", freeformAnimation);
        Log.i("SystemUI probe complete uptime=" + android.os.SystemClock.uptimeMillis());
    }

    private static void probeClass(ClassLoader classLoader, String role, String... names) {
        for (String name : names) {
            Class<?> type = XposedHelpers.findClassIfExists(name, classLoader);
            if (type == null) {
                Log.i("SystemUI probe " + role + " missing=" + name);
                continue;
            }
            Log.i("SystemUI probe " + role + " present=" + name
                    + " methods=" + methodSummary(type));
            return;
        }
    }

    static String methodSummary(Class<?> type) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        try {
            for (Method method : type.getDeclaredMethods()) {
                names.add(method.getName() + "/" + method.getParameterTypes().length);
            }
        } catch (Throwable error) {
            return "<unavailable:" + error.getClass().getSimpleName() + ">";
        }
        if (names.isEmpty()) return "[]";
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        if (sorted.size() > 40) sorted = sorted.subList(0, 40);
        return sorted.toString();
    }

    static String nestedClassSummary(Class<?> type) {
        try {
            List<String> names = new ArrayList<>();
            for (Class<?> nested : type.getDeclaredClasses()) names.add(nested.getName());
            Collections.sort(names);
            return names.toString();
        } catch (Throwable error) {
            return "<unavailable:" + error.getClass().getSimpleName() + ">";
        }
    }

    static String findEventReceiverMethod(Class<?> controllerClass) {
        try {
            for (Method method : controllerClass.getDeclaredMethods()) {
                if ("createEventReceiver".equals(method.getName())) return method.getName();
            }
            for (Method method : controllerClass.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(java.util.Locale.US);
                if (name.contains("eventreceiver") && name.contains("create")) {
                    return method.getName();
                }
            }
        } catch (Throwable error) {
            Log.e("Cannot inspect input receiver methods class=" + controllerClass.getName(), error);
        }
        return null;
    }

    static String findEventHandlerRegistrationMethod(Class<?> controllerClass,
                                                     Class<?> handlerClass) {
        try {
            for (Method method : controllerClass.getDeclaredMethods()) {
                if ("registerEventHandler".equals(method.getName())
                        && acceptsHandler(method, handlerClass)) return method.getName();
            }
            for (Method method : controllerClass.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(java.util.Locale.US);
                if (name.contains("register") && acceptsHandler(method, handlerClass)) {
                    return method.getName();
                }
            }
        } catch (Throwable error) {
            Log.e("Cannot inspect input handler registration class="
                    + controllerClass.getName(), error);
        }
        return null;
    }

    static boolean acceptsHandler(Method method, Class<?> handlerClass) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return parameterTypes.length == 1
                && parameterTypes[0] != Object.class
                && parameterTypes[0].isAssignableFrom(handlerClass);
    }
}
