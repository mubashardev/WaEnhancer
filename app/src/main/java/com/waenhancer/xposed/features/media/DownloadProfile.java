package com.waenhancer.xposed.features.media;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.waenhancer.R;
import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.WaContactWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.xposed.utils.Utils;
import org.luckypray.dexkit.query.enums.StringMatchType;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class DownloadProfile extends Feature {

    public DownloadProfile(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    private static final int MENU_ID_DOWNLOAD = 0x7EAD0002;

    @Override
    public void doHook() throws Throwable {
        Class<?> profileClass;
        try {
            profileClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "ViewProfilePhoto");
            ;
        } catch (Exception e) {
            XposedBridge.log("WAE: DownloadProfile: ViewProfilePhoto class not found: " + e.getMessage());
            return;
        }

        XC_MethodHook hooker = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (Menu) param.args[0];
                if (menu.findItem(MENU_ID_DOWNLOAD) != null) return;

                var item = menu.add(0, MENU_ID_DOWNLOAD, 0, com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.R.string.download, "Download"));
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                
                // Use DesignUtils.getDrawable to ensure drawable is loaded from module resources
                Drawable icon = DesignUtils.getDrawable(R.drawable.download);
                if (icon != null) {
                    item.setIcon(icon);
                }

                item.setOnMenuItemClickListener(menuItem -> {
                    try {
                        ;
                        Object fieldObj = null;
                        
                        // Strategy 1: Look for WaContactWpp.TYPE in fields of the activity and its superclasses
                        Class<?> current = param.thisObject.getClass();
                        while (current != null && current != Object.class && fieldObj == null) {
                            var field = com.waenhancer.xposed.utils.ReflectionUtils.findFieldUsingFilterIfExists(current, f -> 
                                f.getType() == WaContactWpp.TYPE || f.getType().getName().endsWith(".ContactInfo")
                            );
                            if (field != null) {
                                field.setAccessible(true);
                                fieldObj = field.get(param.thisObject);
                            }
                            current = current.getSuperclass();
                        }

                        if (fieldObj == null) {
                            ;
                            Utils.showToast("Error: Could not find contact information", 1);
                            return true;
                        }

                        var waContact = new WaContactWpp(fieldObj);
                        var userJid = waContact.getUserJid();
                        ;
                        
                        var file = waContact.getProfilePhoto();
                        
                        if (file == null || !file.exists()) {
                            ;
                            file = WppCore.getContactPhotoFile(userJid.getPhoneRawString());
                        }

                        if (file == null || !file.exists()) {
                            ;
                            Utils.showToast("Error: Profile photo not found or not loaded yet", 1);
                            return true;
                        }
                        
                        ;

                        String destPath;
                        try {
                            destPath = Utils.getDestination("Profile Photo");
                        } catch (Exception e) {
                            Utils.showToast(e.toString(), 1);
                            return true;
                        }

                        var name = Utils.generateName(userJid, "jpg");
                        var error = Utils.copyFile(file, destPath, name);
                        if (TextUtils.isEmpty(error)) {
                            Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.saved_to, "Saved to: ") + destPath + name, Toast.LENGTH_LONG);
                        } else {
                            Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.error_when_saving_try_again, "Error when saving: ") + " " + error, Toast.LENGTH_LONG);
                        }
                    } catch (Exception e) {
                        XposedBridge.log(e);
                        Utils.showToast("Error: " + e.getMessage(), 1);
                    }
                    return true;
                });
            }
        };

        XposedHelpers.findAndHookMethod(profileClass, "onCreateOptionsMenu", Menu.class, hooker);
        XposedHelpers.findAndHookMethod(profileClass, "onPrepareOptionsMenu", Menu.class, hooker);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Download Profile Picture";
    }
}
