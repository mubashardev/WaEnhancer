package com.waenhancer.xposed.core.plugins.impl;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.graphics.drawable.Drawable;
import com.waex.api.model.UserJidDTO;
import com.waex.api.services.ICoreBridge;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.utils.Utils;
import com.waenhancer.xposed.utils.XPrefManager;

public class CoreBridgeImpl implements ICoreBridge {

    private final SharedPreferences mainPrefs;

    public CoreBridgeImpl(SharedPreferences mainPrefs) {
        this.mainPrefs = mainPrefs;
    }

    private Context getModuleContext() {
        Context app = Utils.getApplication();
        if (app != null) {
            try {
                return app.createPackageContext("com.waenhancer", Context.CONTEXT_IGNORE_SECURITY);
            } catch (Throwable ignored) {}
        }
        return null;
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

    @Override
    public Drawable getDrawable(int resId) {
        Context app = Utils.getApplication();
        if (app != null) {
            return androidx.core.content.ContextCompat.getDrawable(app, resId);
        }
        return null;
    }

    @Override
    public Drawable getModuleDrawable(String name) {
        Context modCtx = getModuleContext();
        if (modCtx != null) {
            int resId = modCtx.getResources().getIdentifier(name, "drawable", "com.waenhancer");
            if (resId != 0) {
                return modCtx.getDrawable(resId);
            }
        }
        return null;
    }

    @Override
    public String getModuleString(String name) {
        Context modCtx = getModuleContext();
        if (modCtx != null) {
            int resId = modCtx.getResources().getIdentifier(name, "string", "com.waenhancer");
            if (resId != 0) {
                return modCtx.getString(resId);
            }
        }
        return null;
    }

    @Override
    public boolean isBootloaderSpooferActive() {
        Context app = Utils.getApplication();
        if (app != null) {
            try {
                return app.getPackageManager().hasSystemFeature("com.waenhancer.spoofer.active_check");
            } catch (Throwable ignored) {}
        }
        return false;
    }
}
