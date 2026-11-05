package com.leaf.hyperdragshare.compat.xposed;

import android.content.pm.ApplicationInfo;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Adapts the existing legacy callback surface to libxposed API 102. */
public final class LegacyBridge {
    private static volatile XposedModule module;
    private static final ThreadLocal<ClassLoader> classLoader = new ThreadLocal<>();
    private LegacyBridge() {}

    public static void bind(XposedModule value, String packageName, String processName,
                            ClassLoader loader, ApplicationInfo appInfo) {
        module = value;
        classLoader.set(loader);
        com.leaf.hyperdragshare.compat.xposed.callbacks.XC_LoadPackage.LoadPackageParam param =
                new com.leaf.hyperdragshare.compat.xposed.callbacks.XC_LoadPackage.LoadPackageParam(packageName, processName, loader, appInfo);
        try { new com.leaf.hyperdragshare.codex.MainHook().handleLoadPackage(param); }
        catch (Throwable error) { value.log(XposedInterface.PRIORITY_DEFAULT, "HyperDragShare", "package hook failed", error); }
    }
    static XC_MethodHook.Unhook hook(Executable executable, XC_MethodHook callback) {
        XposedModule current = module;
        if (current == null) throw new IllegalStateException("libxposed module is not bound");
        XposedInterface.HookHandle handle = current.hook(executable).intercept(chain -> {
            XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
            param.thisObject = chain.getThisObject();
            List<Object> chainArgs = chain.getArgs();
            param.args = chainArgs == null ? new Object[0] : chainArgs.toArray();
            param.method = executable;
            try {
                XC_MethodHook.invokeBefore(callback, param);
                if (!param.isReturnEarly()) {
                    try { param.result = chain.proceed(param.args); }
                    catch (Throwable error) { param.throwable = error; }
                }
                XC_MethodHook.invokeAfter(callback, param);
                if (param.throwable != null) throw param.throwable;
                return param.result;
            } catch (Throwable error) {
                if (error instanceof InvocationTargetException && error.getCause() != null) throw error.getCause();
                throw error;
            }
        });
        return new XC_MethodHook.Unhook(handle::unhook);
    }
    static Object invokeOriginal(Method method, Object receiver, Object[] args) throws Throwable {
        XposedModule current = module;
        if (current == null) return method.invoke(receiver, args);
        try { return current.getInvoker(method).invoke(receiver, args); }
        catch (InvocationTargetException e) { throw e.getCause() == null ? e : e.getCause(); }
    }
}
