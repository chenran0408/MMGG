package com.leaf.hyperdragshare.compat.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

public final class XposedBridge {
    private XposedBridge() {}

    public static XC_MethodHook.Unhook hookMethod(Method method, XC_MethodHook callback) {
        return LegacyBridge.hook(method, callback);
    }
    public static XC_MethodHook.Unhook hookMethod(Constructor<?> constructor, XC_MethodHook callback) {
        return LegacyBridge.hook(constructor, callback);
    }
    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> clazz, String methodName, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> result = new LinkedHashSet<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) result.add(hookMethod(method, callback));
        }
        return result;
    }
    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> clazz, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> result = new LinkedHashSet<>();
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) result.add(hookMethod(constructor, callback));
        return result;
    }
    public static Object invokeOriginalMethod(Method method, Object receiver, Object[] args) throws Throwable {
        return LegacyBridge.invokeOriginal(method, receiver, args);
    }
    public static Object invokeOriginalMethod(java.lang.reflect.Member member, Object receiver, Object[] args) throws Throwable {
        if (!(member instanceof Method)) throw new IllegalArgumentException("Only methods can be invoked");
        return LegacyBridge.invokeOriginal((Method) member, receiver, args);
    }
    public static void log(String text) { android.util.Log.i("HyperDragShare", text); }
    public static void log(Throwable throwable) { android.util.Log.e("HyperDragShare", String.valueOf(throwable)); }
}
