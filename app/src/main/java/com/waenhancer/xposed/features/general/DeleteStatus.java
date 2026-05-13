package com.waenhancer.xposed.features.general;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.features.listeners.MenuStatusListener;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Field;
import java.util.List;

import android.content.SharedPreferences;

public class DeleteStatus extends Feature {


    public DeleteStatus(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var fragmentloader = Unobfuscator.loadFragmentLoader(classLoader);
        var showDialogStatus = Unobfuscator.loadShowDialogStatusMethod(classLoader);
        Class<?> StatusDeleteDialogFragmentClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, ".StatusDeleteDialogFragment");
        Field fieldBundle = ReflectionUtils.getFieldByType(fragmentloader, Bundle.class);

        var item = new MenuStatusListener.OnMenuItemStatusListener() {

            @Override
            public MenuItem addMenu(Menu menu, List<FMessageWpp> fMessageList, int currentIndex) {
                if (menu.findItem(R.string.delete_for_me) != null) return null;
                var fMessage = fMessageList.get(currentIndex);
                if (fMessage.getKey().isFromMe) return null;
                return menu.add(0, R.string.delete_for_me, 0, com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.R.string.delete_for_me, "Delete for me"));
            }

            @Override
            public void onClick(MenuItem item, Object fragmentInstance, List<FMessageWpp> fMessageList, int currentIndex) {
                try {
                    var fMessage = fMessageList.get(currentIndex);
                    var status = StatusDeleteDialogFragmentClass.newInstance();
                    var key = fMessage.getKey();
                    var bundle = getBundle(key);
                    WppCore.setPrivBoolean(key.messageID + "_delpass", true);
                    fieldBundle.set(status, bundle);
                    showDialogStatus.invoke(null, status, fragmentInstance);
                } catch (Exception e) {
                    logDebug(e);
                }
            }
        };
        MenuStatusListener.registerStatusListener(item);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Delete Status";
    }

    @NonNull
    private static Bundle getBundle(FMessageWpp.Key key) {
        var bundle = new Bundle();
        bundle.putString("fMessageKeyJid", key.remoteJid.getUserRawString());
        bundle.putBoolean("fMessageKeyFromMe", key.isFromMe);
        bundle.putString("fMessageKeyId", key.messageID);
        return bundle;
    }
}
