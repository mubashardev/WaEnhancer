package com.waenhancer.xposed.core.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import android.util.Pair;

public class FStatusWpp {

    public static Class<?> TYPE;
    private static Class<?> classFMediaStatus;
    private static Method methodGetStatusByKey;
    private static Field fieldFStatusKey;
    private static Object mStatusStore = null;

    private static volatile Pair<Field, Method> mediaFileAccessor = null;
    private static volatile boolean mediaFileAccessorInitialized = false;

    private final Object fstatus;

    public FStatusWpp(Object fstatus) {
        if (fstatus == null) throw new RuntimeException("Object FStatus is null");
        if (!TYPE.isInstance(fstatus))
            throw new RuntimeException("Object is not a FStatus Instance");
        this.fstatus = fstatus;
    }

    public static void initialize(ClassLoader classLoader) {
        try {
            FStatusKey.initialize(classLoader);
            TYPE = Unobfuscator.loadFStatusClass(classLoader);
            try {
                classFMediaStatus = Unobfuscator.loadFMediaStatusClass(classLoader);
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] Could not load classFMediaStatus: " + t);
            }
            Class<?> fStatusKeyClass = Unobfuscator.loadFStatusKeyClass(classLoader);
            fieldFStatusKey = ReflectionUtils.getFieldByType(TYPE, fStatusKeyClass);
            methodGetStatusByKey = Unobfuscator.loadGetStatusByKey(classLoader);

            XposedBridge.hookAllConstructors(methodGetStatusByKey.getDeclaringClass(), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mStatusStore = param.thisObject;
                }
            });
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    public boolean isMediaFile() {
        return classFMediaStatus != null && classFMediaStatus.isInstance(fstatus);
    }

    @Nullable
    public File getMediaFile() {
        if (!isMediaFile()) return null;
        if (!mediaFileAccessorInitialized) {
            synchronized (FStatusWpp.class) {
                if (!mediaFileAccessorInitialized) {
                    if (classFMediaStatus != null) {
                        for (Field field : classFMediaStatus.getDeclaredFields()) {
                            field.setAccessible(true);
                            for (Method method : field.getType().getDeclaredMethods()) {
                                if (method.getReturnType() == File.class) {
                                    method.setAccessible(true);
                                    mediaFileAccessor = new Pair<>(field, method);
                                    break;
                                }
                            }
                            if (mediaFileAccessor != null) break;
                        }
                    }
                    mediaFileAccessorInitialized = true;
                }
            }
        }
        if (mediaFileAccessor != null) {
            try {
                Object mediaData = mediaFileAccessor.first.get(fstatus);
                if (mediaData != null) {
                    return (File) mediaFileAccessor.second.invoke(mediaData);
                }
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        }
        return null;
    }

    @Nullable
    public static FStatusWpp getFStatusFromFKeyStatus(FStatusKey fStatusKey) {
        try {
            if (mStatusStore == null) {
                mStatusStore = methodGetStatusByKey.getDeclaringClass().getDeclaredConstructors()[0].newInstance();
            }
            Object result = methodGetStatusByKey.invoke(mStatusStore, fStatusKey.thisObject);
            return result != null ? new FStatusWpp(result) : null;
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public FStatusKey getFStatusKey() {
        try {
            return new FStatusKey(fieldFStatusKey.get(fstatus));
        } catch (Exception e) {
            XposedBridge.log(e);
            return null;
        }
    }

    @Nullable
    public FMessageWpp getFMessage() {
        try {
            Object objFMessage = WppCore.getFMessageFromFStatus(fstatus);
            return objFMessage != null ? new FMessageWpp(objFMessage) : null;
        } catch (Exception e) {
            XposedBridge.log(e);
            return null;
        }
    }

    public static class FStatusKey {
        public static Class<?> TYPE;

        public Object thisObject;
        public String messageID;
        public boolean isFromMe;
        public FMessageWpp.UserJid remoteJid;
        public FMessageWpp.UserJid senderJid;
        public FStatusWpp fStatus;

        public static void initialize(ClassLoader classLoader) throws Exception {
            TYPE = Unobfuscator.loadFStatusKeyClass(classLoader);
        }

        public FStatusKey(Object key) {
            this.thisObject = key;
            this.senderJid = new FMessageWpp.UserJid(XposedHelpers.getObjectField(key, "A01"));
            this.messageID = (String) XposedHelpers.getObjectField(key, "A02");
            this.isFromMe = XposedHelpers.getBooleanField(key, "A03");
            this.remoteJid = new FMessageWpp.UserJid(XposedHelpers.getObjectField(key, "A00"));
            this.fStatus = getFStatusFromFKeyStatus(this);
        }

        @NonNull
        @Override
        public String toString() {
            return "FStatusKey{" +
                    "thisObject=" + thisObject +
                    ", messageID='" + messageID + '\'' +
                    ", isFromMe=" + isFromMe +
                    ", remoteJid=" + remoteJid +
                    ", senderJid=" + senderJid +
                    '}';
        }
    }
}
