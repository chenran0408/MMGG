package com.leaf.hyperdragshare.codex;

import android.app.Service;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** The known sidebar interface across SecurityCenter builds. Check it before changing any layout. */
record SidebarHostContract(Class<?> wrapper, Method open, Method search, Method measure, Method destroy) {
    static SidebarHostContract resolve(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> turbo = Class.forName("com.miui.gamebooster.windowmanager.newbox.TurboLayout", false, loader);
        Class<?> size = Class.forName("com.miui.gamebooster.windowmanager.newbox.y2", false, loader);
        Class<?> service = Class.forName("com.miui.gamebooster.service.DockWindowManagerService", false, loader);
        Class<?> wrapper = instanceMethod(turbo, Object.class, "getSidebarWrapper").getReturnType();
        return verify(turbo, wrapper, size, service);
    }

    static SidebarHostContract verify(Class<?> turbo, Class<?> wrapper, Class<?> size, Class<?> service)
            throws ReflectiveOperationException {
        turbo.asSubclass(RelativeLayout.class);
        service.asSubclass(Service.class);
        instanceMethod(wrapper, FrameLayout.class, "B");
        instanceMethod(wrapper, turbo, "C");
        instanceMethod(wrapper, boolean.class, "H");
        instanceMethod(wrapper, View.class, "w");
        Class<?> animation = instanceMethod(wrapper, Object.class, "o").getReturnType();
        instanceMethod(animation, void.class, "O0", boolean.class);
        instanceMethod(turbo, wrapper, "getSidebarWrapper");
        instanceMethod(turbo, View.class, "getAppsLayout");
        instanceMethod(turbo, View.class, "getBoxView");
        Class<?> dock = instanceMethod(turbo, FrameLayout.class, "getDockLayout").getReturnType();
        instanceMethod(dock, void.class, "J");
        for (String name : new String[]{"D", "F", "G"}) instanceMethod(turbo, boolean.class, name);
        instanceMethod(turbo.getDeclaredField("j").getType(), int.class, "c");
        var transitioning = turbo.getDeclaredField("s");
        if (transitioning.getType() != boolean.class || Modifier.isStatic(transitioning.getModifiers())) {
            throw new NoSuchFieldException(turbo.getName() + ".s must be a boolean");
        }
        Method measure = size.getDeclaredMethod("c");
        if (!Modifier.isStatic(measure.getModifiers()) || measure.getReturnType() != int.class) {
            throw new NoSuchMethodException(size.getName() + ".c must be static and return an int");
        }
        return new SidebarHostContract(wrapper, instanceMethod(wrapper, void.class, "W", boolean.class),
                instanceMethod(turbo, void.class, "e0"), measure, instanceMethod(service, void.class, "onDestroy"));
    }

    private static Method instanceMethod(Class<?> owner, Class<?> result, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameters);
        if (Modifier.isStatic(method.getModifiers()) || !result.isAssignableFrom(method.getReturnType())) {
            throw new NoSuchMethodException(owner.getName() + "." + name + " has an incompatible signature");
        }
        return method;
    }
}
