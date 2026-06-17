package com.waex.api.services;

import java.lang.reflect.Member;

public interface IHookService {
    
    interface HookParam {
        Object getThisObject();
        Object[] getArguments();
        Object getResult();
        void setResult(Object result);
        Throwable getThrowable();
        void setThrowable(Throwable throwable);
        boolean hasResult();
    }

    interface HookCallback {
        void before(HookParam param) throws Throwable;
        void after(HookParam param) throws Throwable;
    }

    Object hookMethod(Member method, HookCallback callback);
    Object hookAllConstructors(Class<?> clazz, HookCallback callback);
    Object hookAllMethods(Class<?> clazz, String methodName, HookCallback callback);
}
