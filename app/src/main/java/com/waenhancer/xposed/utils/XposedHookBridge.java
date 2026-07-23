package com.waenhancer.xposed.utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

public class XposedHookBridge {

    public interface HookCallback {
        default void before(HookParam param) throws Throwable {}
        default void after(HookParam param) throws Throwable {}
    }

    public static class HookParam {
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        private boolean hasResult = false;

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
            this.hasResult = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
        }

        public boolean hasResult() {
            return hasResult;
        }
    }

    private static XC_MethodHook wrapCallback(final HookCallback callback) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                HookParam wrapped = new HookParam();
                wrapped.thisObject = param.thisObject;
                wrapped.args = param.args;
                wrapped.result = param.getResult();
                wrapped.throwable = param.getThrowable();

                callback.before(wrapped);

                if (wrapped.hasResult()) {
                    param.setResult(wrapped.result);
                }
                if (wrapped.throwable != null) {
                    param.setThrowable(wrapped.throwable);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                HookParam wrapped = new HookParam();
                wrapped.thisObject = param.thisObject;
                wrapped.args = param.args;
                wrapped.result = param.getResult();
                wrapped.throwable = param.getThrowable();

                callback.after(wrapped);

                if (wrapped.hasResult()) {
                    param.setResult(wrapped.result);
                }
                if (wrapped.throwable != null) {
                    param.setThrowable(wrapped.throwable);
                }
            }
        };
    }

    public static void findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback == null || parameterTypesAndCallback.length == 0) return;
        int numParams = parameterTypesAndCallback.length - 1;
        Object last = parameterTypesAndCallback[numParams];
        if (last instanceof HookCallback) {
            Object[] newParams = new Object[parameterTypesAndCallback.length];
            System.arraycopy(parameterTypesAndCallback, 0, newParams, 0, numParams);
            newParams[numParams] = wrapCallback((HookCallback) last);
            XposedHelpers.findAndHookMethod(clazz, methodName, newParams);
        } else {
            XposedHelpers.findAndHookMethod(clazz, methodName, parameterTypesAndCallback);
        }
    }

    public static void findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback == null || parameterTypesAndCallback.length == 0) return;
        int numParams = parameterTypesAndCallback.length - 1;
        Object last = parameterTypesAndCallback[numParams];
        if (last instanceof HookCallback) {
            Object[] newParams = new Object[parameterTypesAndCallback.length];
            System.arraycopy(parameterTypesAndCallback, 0, newParams, 0, numParams);
            newParams[numParams] = wrapCallback((HookCallback) last);
            XposedHelpers.findAndHookMethod(className, classLoader, methodName, newParams);
        } else {
            XposedHelpers.findAndHookMethod(className, classLoader, methodName, parameterTypesAndCallback);
        }
    }

    public static void findAndHookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback == null || parameterTypesAndCallback.length == 0) return;
        int numParams = parameterTypesAndCallback.length - 1;
        Object last = parameterTypesAndCallback[numParams];
        if (last instanceof HookCallback) {
            Object[] newParams = new Object[parameterTypesAndCallback.length];
            System.arraycopy(parameterTypesAndCallback, 0, newParams, 0, numParams);
            newParams[numParams] = wrapCallback((HookCallback) last);
            XposedHelpers.findAndHookConstructor(clazz, newParams);
        } else {
            XposedHelpers.findAndHookConstructor(clazz, parameterTypesAndCallback);
        }
    }

    public static void findAndHookConstructor(String className, ClassLoader classLoader, Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback == null || parameterTypesAndCallback.length == 0) return;
        int numParams = parameterTypesAndCallback.length - 1;
        Object last = parameterTypesAndCallback[numParams];
        if (last instanceof HookCallback) {
            Object[] newParams = new Object[parameterTypesAndCallback.length];
            System.arraycopy(parameterTypesAndCallback, 0, newParams, 0, numParams);
            newParams[numParams] = wrapCallback((HookCallback) last);
            XposedHelpers.findAndHookConstructor(className, classLoader, newParams);
        } else {
            XposedHelpers.findAndHookConstructor(className, classLoader, parameterTypesAndCallback);
        }
    }

    public static void hookMethod(Member method, HookCallback callback) {
        XposedBridge.hookMethod(method, wrapCallback(callback));
    }

    public static void hookAllConstructors(Class<?> clazz, HookCallback callback) {
        XposedBridge.hookAllConstructors(clazz, wrapCallback(callback));
    }

    public static void hookAllMethods(Class<?> clazz, String methodName, HookCallback callback) {
        XposedBridge.hookAllMethods(clazz, methodName, wrapCallback(callback));
    }

    public static void log(String message) {
        /* Log removed */
    }

    public static void log(Throwable t) {
        /* Log removed */
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        return XposedHelpers.callMethod(obj, methodName, args);
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        return XposedHelpers.callStaticMethod(clazz, methodName, args);
    }

    public static Object getObjectField(Object obj, String fieldName) {
        return XposedHelpers.getObjectField(obj, fieldName);
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        XposedHelpers.setObjectField(obj, fieldName, value);
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        return XposedHelpers.getStaticObjectField(clazz, fieldName);
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        return XposedHelpers.findClass(className, classLoader);
    }

    public static Constructor<?> findConstructorExact(Class<?> clazz, Class<?>... parameterTypes) {
        return XposedHelpers.findConstructorExact(clazz, (Object[]) parameterTypes);
    }

    public static Method findMethodExact(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        return XposedHelpers.findMethodExact(clazz, methodName, (Object[]) parameterTypes);
    }

    public static Object getAdditionalInstanceField(Object obj, String key) {
        return XposedHelpers.getAdditionalInstanceField(obj, key);
    }

    public static void setAdditionalInstanceField(Object obj, String key, Object value) {
        XposedHelpers.setAdditionalInstanceField(obj, key, value);
    }
}
