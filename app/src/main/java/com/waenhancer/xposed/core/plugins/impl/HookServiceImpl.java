package com.waenhancer.xposed.core.plugins.impl;

import com.waenhancer.api.services.IHookService;
import java.lang.reflect.Member;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HookServiceImpl implements IHookService {

    private static class HookParamImpl implements HookParam {
        private final XC_MethodHook.MethodHookParam param;

        public HookParamImpl(XC_MethodHook.MethodHookParam param) {
            this.param = param;
        }

        @Override
        public Object getThisObject() {
            return param.thisObject;
        }

        @Override
        public Object[] getArguments() {
            return param.args;
        }

        @Override
        public Object getResult() {
            return param.getResult();
        }

        @Override
        public void setResult(Object result) {
            param.setResult(result);
        }

        @Override
        public Throwable getThrowable() {
            return param.getThrowable();
        }

        @Override
        public void setThrowable(Throwable throwable) {
            param.setThrowable(throwable);
        }

        @Override
        public boolean hasResult() {
            try {
                java.lang.reflect.Field field = param.getClass().getDeclaredField("returnEarly");
                field.setAccessible(true);
                return (boolean) field.get(param);
            } catch (Throwable t) {
                return param.getResult() != null || param.getThrowable() != null;
            }
        }
    }

    private static XC_MethodHook wrapCallback(final HookCallback callback) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                callback.before(new HookParamImpl(param));
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                callback.after(new HookParamImpl(param));
            }
        };
    }

    @Override
    public Object hookMethod(Member method, HookCallback callback) {
        return XposedBridge.hookMethod(method, wrapCallback(callback));
    }

    @Override
    public Object hookAllConstructors(Class<?> clazz, HookCallback callback) {
        return XposedBridge.hookAllConstructors(clazz, wrapCallback(callback));
    }

    @Override
    public Object hookAllMethods(Class<?> clazz, String methodName, HookCallback callback) {
        return XposedBridge.hookAllMethods(clazz, methodName, wrapCallback(callback));
    }
}
