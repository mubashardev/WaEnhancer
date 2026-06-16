package com.waenhancer.api.services;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import com.waenhancer.api.model.UserJidDTO;

public interface IWhatsAppContextService {
    Activity getCurrentActivity();
    Context getApplicationContext();
    SharedPreferences getMainPrefs();
    SharedPreferences getPrivPrefs();
    boolean getPrivBoolean(String key, boolean defaultValue);
    Object createUserJid(String rawJid);
    boolean sendMessage(Object userJidRaw, String message);
    int getIdentifier(String name, String defType);
    int dipToPixels(int dip);
    Object getFMessageFromKey(Object messageKey);
    Class<?> getTextStatusComposerFragmentClass(ClassLoader classLoader);
    Class<?> getVoiceStatusComposerFragmentClass(ClassLoader classLoader);
    void reloadPrefs();
    void showBottomSheet(Context context, View view, String title, String positiveButtonText, Runnable onPositiveClick);

    SharedPreferences getPrefs(String name, int mode);
    SharedPreferences getModulePrefs();
    int getModuleResourceIdentifier(String name, String defType);
    boolean sendMessage(UserJidDTO targetJid, String messageText);
}
