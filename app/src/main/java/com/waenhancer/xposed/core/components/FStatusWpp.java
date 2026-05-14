package com.waenhancer.xposed.core.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class FStatusWpp {

    public static Class<?> TYPE;
    private static Method methodGetStatusByKey;
    private static Field fieldFStatusKey;
    private static Object mStatusStore = null;

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
