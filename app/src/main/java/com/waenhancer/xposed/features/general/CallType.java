package com.waenhancer.xposed.features.general;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.BaseBundle;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.components.SharedPreferencesWrapper;
import com.waenhancer.xposed.core.devkit.UnobfuscatorCache;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;

import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.os.Bundle;
import com.waenhancer.xposed.core.FeatureLoader;
import com.waenhancer.xposed.utils.Utils;

public class CallType extends Feature {
    private XC_MethodHook.Unhook hookBundleBoolean;

    public CallType(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void doHook() throws Throwable {

        boolean callTypeEnabled = false;
        try {
            callTypeEnabled = prefs.getBoolean("calltype", false);
        } catch (ClassCastException e) {
            try {
                String strVal = prefs.getString("calltype", "false");
                callTypeEnabled = "true".equalsIgnoreCase(strVal) || "1".equals(strVal);
                prefs.edit().putBoolean("calltype", callTypeEnabled).apply();
            } catch (Exception ignored) {}
        }
        if (!callTypeEnabled) return;

        SharedPreferencesWrapper.addHook((key, value) -> {
            if (Objects.equals(key, "call_confirmation_dialog_count")) {
                return 1;
            }
            return value;
        });


        var callConfirmationFragment = XposedHelpers.findClass("com.whatsapp.calling.fragment.CallConfirmationFragment", classLoader);
        var method = ReflectionUtils.findMethodUsingFilter(callConfirmationFragment, m -> m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(Bundle.class));
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            private boolean isVideoCall;
            private String jid;
            private Dialog newDialog;
            private Unhook hookBundleString;

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                hookBundleString = XposedHelpers.findAndHookMethod(BaseBundle.class, "getString", String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args[0] == "jid") {
                            jid = (String) param.getResult();
                        }
                    }
                });
                hookBundleBoolean = XposedHelpers.findAndHookMethod(BaseBundle.class, "getBoolean", String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args[0] == "is_video_call") {
                            isVideoCall = (boolean) param.getResult();
                        }
                    }
                });
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                hookBundleString.unhook();
                hookBundleBoolean.unhook();
                if (jid == null || isVideoCall) return;
                var origDialog = (Dialog) param.getResult();
                var context = origDialog.getContext();
                var mAlertDialog = new AlertDialogWpp(origDialog.getContext());
                mAlertDialog.setTitle(UnobfuscatorCache.getInstance().getString("selectcalltype"));
                mAlertDialog.setItems(new String[]{FeatureLoader.getModuleString(Utils.getApplication(), R.string.phone_call), FeatureLoader.getModuleString(Utils.getApplication(), R.string.whatsapp_call)}, (dialog, which) -> {
                    newDialog.dismiss();
                    switch (which) {
                        case 0:
                            var intent = new Intent();
                            intent.setAction(Intent.ACTION_DIAL);
                            var userJid = new FMessageWpp.UserJid(jid);
                            intent.setData(Uri.parse("tel:+" + userJid.getPhoneNumber()));
                            context.startActivity(intent);
                            break;
                        case 1:
                            origDialog.show();
                            break;
                    }
                });
                newDialog = mAlertDialog.create();
                param.setResult(newDialog);
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Call Type";
    }
}