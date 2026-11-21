package com.leaf.hyperdragshare.compat.xposed;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;

public class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public Member method;
        public Object result;
        public Throwable throwable;
        private boolean returnEarly;

        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; this.throwable = null; this.returnEarly = true; }
        public Throwable getThrowable() { return throwable; }
        public void setThrowable(Throwable throwable) { this.throwable = throwable; this.returnEarly = true; }
        public boolean hasThrowable() { return throwable != null; }
        public boolean isReturnEarly() { return returnEarly; }
        public Executable getExecutable() { return method instanceof Executable ? (Executable) method : null; }
    }

    static void invokeBefore(XC_MethodHook hook, MethodHookParam param) throws Throwable {
        hook.beforeHookedMethod(param);
    }
    static void invokeAfter(XC_MethodHook hook, MethodHookParam param) throws Throwable {
        hook.afterHookedMethod(param);
    }

    public static class Unhook {
        private final Runnable action;
        public Unhook() { this(null); }
        Unhook(Runnable action) { this.action = action; }
        public void unhook() { if (action != null) action.run(); }
    }
}
