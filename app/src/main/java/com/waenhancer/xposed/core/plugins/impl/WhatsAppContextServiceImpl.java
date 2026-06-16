package com.waenhancer.xposed.core.plugins.impl;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import com.waenhancer.api.model.UserJidDTO;
import com.waenhancer.api.services.IWhatsAppContextService;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.utils.Utils;
import com.waenhancer.xposed.utils.XPrefManager;

public class WhatsAppContextServiceImpl implements IWhatsAppContextService {

    private final SharedPreferences mainPrefs;

    public WhatsAppContextServiceImpl(SharedPreferences mainPrefs) {
        this.mainPrefs = mainPrefs;
    }

    @Override
    public Activity getCurrentActivity() {
        return WppCore.getCurrentActivity();
    }

    @Override
    public Context getApplicationContext() {
        return Utils.getApplication();
    }

    @Override
    public SharedPreferences getMainPrefs() {
        return mainPrefs;
    }

    @Override
    public SharedPreferences getPrivPrefs() {
        return WppCore.getPrivPrefs();
    }

    @Override
    public boolean getPrivBoolean(String key, boolean defaultValue) {
        return WppCore.getPrivBoolean(key, defaultValue);
    }

    @Override
    public Object createUserJid(String rawJid) {
        return WppCore.createUserJid(rawJid);
    }

    @Override
    public boolean sendMessage(Object userJidRaw, String message) {
        return WppCore.sendMessage(userJidRaw, message);
    }

    @Override
    public int getIdentifier(String name, String defType) {
        Context app = Utils.getApplication();
        if (app != null) {
            return app.getResources().getIdentifier(name, defType, app.getPackageName());
        }
        return 0;
    }

    @Override
    public int dipToPixels(int dip) {
        return Utils.dipToPixels(dip);
    }

    @Override
    public Object getFMessageFromKey(Object messageKey) {
        return WppCore.getFMessageFromKey(messageKey);
    }

    @Override
    public Class<?> getTextStatusComposerFragmentClass(ClassLoader classLoader) {
        try {
            return WppCore.getTextStatusComposerFragmentClass(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Class<?> getVoiceStatusComposerFragmentClass(ClassLoader classLoader) {
        try {
            return WppCore.getVoiceStatusComposerFragmentClass(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void reloadPrefs() {
        XPrefManager.reload();
    }

    @Override
    public void showBottomSheet(Context context, View view, String title, String positiveButtonText, final Runnable onPositiveClick) {
        new AlertDialogWpp(context)
            .asBottomSheet()
            .setFullHeight(true)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(positiveButtonText, (dialog, which) -> {
                if (onPositiveClick != null) {
                    onPositiveClick.run();
                }
            })
            .show();
    }

    @Override
    public SharedPreferences getPrefs(String name, int mode) {
        Context app = Utils.getApplication();
        return app != null ? app.getSharedPreferences(name, mode) : null;
    }

    @Override
    public SharedPreferences getModulePrefs() {
        return mainPrefs;
    }

    @Override
    public int getModuleResourceIdentifier(String name, String defType) {
        Context app = Utils.getApplication();
        if (app != null) {
            return app.getResources().getIdentifier(name, defType, "com.waenhancer");
        }
        return 0;
    }

    @Override
    public boolean sendMessage(UserJidDTO targetJid, String messageText) {
        if (targetJid == null || targetJid.getRawJid() == null) return false;
        Object rawJid = WppCore.createUserJid(targetJid.getRawJid());
        return WppCore.sendMessage(rawJid, messageText);
    }
}
