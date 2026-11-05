package com.leaf.hyperdragshare.compat.xposed;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public final class XposedHelpers {
    private XposedHelpers() {}
    public static class ClassNotFoundError extends Error { public ClassNotFoundError(Throwable cause) { super(cause); } }
    public static class InvocationTargetError extends Error { public InvocationTargetError(Throwable cause) { super(cause); } }

    public static Class<?> findClass(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader != null ? loader : ClassLoader.getSystemClassLoader()); }
        catch (ClassNotFoundException e) { throw new ClassNotFoundError(e); }
    }
    public static Class<?> findClassIfExists(String name, ClassLoader loader) {
        try { return findClass(name, loader); } catch (ClassNotFoundError e) { return null; }
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String name, Object... parameterTypesAndCallback) {
        XC_MethodHook callback = callback(parameterTypesAndCallback);
        Class<?>[] types = resolveTypes(parameterTypesAndCallback, callback, clazz.getClassLoader());
        Method method = findMethod(clazz, name, types);
        if (method == null) throw new NoSuchMethodError(clazz.getName() + "#" + name);
        return XposedBridge.hookMethod(method, callback);
    }
    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader loader, String name, Object... parameterTypesAndCallback) {
        return findAndHookMethod(findClass(className, loader), name, parameterTypesAndCallback);
    }
    private static XC_MethodHook callback(Object[] values) {
        if (values.length == 0 || !(values[values.length - 1] instanceof XC_MethodHook)) throw new IllegalArgumentException("last argument must be callback");
        return (XC_MethodHook) values[values.length - 1];
    }
    private static Class<?>[] resolveTypes(Object[] values, XC_MethodHook callback, ClassLoader loader) {
        Class<?>[] types = new Class<?>[values.length - 1];
        for (int i = 0; i < types.length; i++) {
            Object value = values[i];
            types[i] = value instanceof Class<?> ? (Class<?>) value : findClass(String.valueOf(value), loader);
        }
        return types;
    }
    private static Method findMethod(Class<?> clazz, String name, Class<?>[] types) {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            try { Method method = current.getDeclaredMethod(name, types); method.setAccessible(true); return method; }
            catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    public static Object callMethod(Object object, String name, Object... args) { return invoke(findBestMethod(object.getClass(), name, args), object, args); }
    public static Object callStaticMethod(Class<?> clazz, String name, Object... args) { return invoke(findBestMethod(clazz, name, args), null, args); }
    private static Object invoke(Method method, Object receiver, Object[] args) {
        if (method == null) throw new NoSuchMethodError();
        try { method.setAccessible(true); return method.invoke(receiver, args); }
        catch (Throwable e) { Throwable cause = e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null ? e.getCause() : e; throw new InvocationTargetError(cause); }
    }
    private static Method findBestMethod(Class<?> clazz, String name, Object[] args) {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                Class<?>[] types = method.getParameterTypes(); boolean match = true;
                for (int i = 0; i < types.length; i++) if (args[i] != null && !wrap(types[i]).isAssignableFrom(args[i].getClass())) { match = false; break; }
                if (match) return method;
            }
        }
        return null;
    }
    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class; if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class; if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class; if (type == float.class) return Float.class;
        if (type == double.class) return Double.class; if (type == char.class) return Character.class;
        return type;
    }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try { Field f = current.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
    public static Object getObjectField(Object object, String name) { return getField(object, name); }
    public static boolean getBooleanField(Object object, String name) { return (Boolean) getField(object, name); }
    public static int getIntField(Object object, String name) { return ((Number) getField(object, name)).intValue(); }
    public static long getLongField(Object object, String name) { return ((Number) getField(object, name)).longValue(); }
    public static float getFloatField(Object object, String name) { return ((Number) getField(object, name)).floatValue(); }
    private static Object getField(Object object, String name) { try { return field(object.getClass(), name).get(object); } catch (Throwable e) { throw new RuntimeException(e); } }
    public static void setBooleanField(Object object, String name, boolean value) { setField(object, name, value); }
    public static void setObjectField(Object object, String name, Object value) { setField(object, name, value); }
    private static void setField(Object object, String name, Object value) { try { field(object.getClass(), name).set(object, value); } catch (Throwable e) { throw new RuntimeException(e); } }
    public static Object getStaticObjectField(Class<?> clazz, String name) { try { return field(clazz, name).get(null); } catch (Throwable e) { throw new RuntimeException(e); } }
    public static void setStaticObjectField(Class<?> clazz, String name, Object value) { try { field(clazz, name).set(null, value); } catch (Throwable e) { throw new RuntimeException(e); } }
}
