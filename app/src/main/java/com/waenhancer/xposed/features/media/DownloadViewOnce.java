package com.waenhancer.xposed.features.media;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class DownloadViewOnce extends Feature {
    private static final int MENU_ID_DOWNLOAD = 0x7EAD0003;

    public DownloadViewOnce(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    private static void downloadFile(FMessageWpp.UserJid userJid, File file) throws Exception {
        var dest = Utils.getDestination("View Once");
        var fileExtension = file.getAbsolutePath().substring(file.getAbsolutePath().lastIndexOf(".") + 1);
        var name = Utils.generateName(userJid, fileExtension);
        var error = Utils.copyFile(file, dest, name);
        if (TextUtils.isEmpty(error)) {
            Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.saved_to) + dest, Toast.LENGTH_LONG);
        } else {
            Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.error_when_saving_try_again) + ":" + error, Toast.LENGTH_LONG);
        }
    }

    @Override
    public void doHook() throws Throwable {
        if (prefs.getBoolean("downloadviewonce", false)) {

            var menuMethod = Unobfuscator.loadViewOnceDownloadMenuMethod(classLoader);
            // Media Activity
            XposedBridge.hookMethod(menuMethod, new XC_MethodHook() {
                @Override
                @SuppressLint("DiscouragedApi")
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (DEBUG) ;
                    
                    Object fmessageObj = null;
                    var fields = ReflectionUtils.getFieldsByExtendType(param.thisObject.getClass(), FMessageWpp.TYPE);
                    if (!fields.isEmpty()) {
                        for (var field : fields) {
                            fmessageObj = field.get(param.thisObject);
                            if (fmessageObj != null) break;
                        }
                    }
                    
                    if (fmessageObj == null) {
                        var keyFields = ReflectionUtils.getFieldsByExtendType(param.thisObject.getClass(), FMessageWpp.Key.TYPE);
                        if (!keyFields.isEmpty()) {
                            for (var field : keyFields) {
                                var keyObj = field.get(param.thisObject);
                                if (keyObj != null) {
                                    fmessageObj = WppCore.getFMessageFromKey(keyObj);
                                    if (fmessageObj != null) break;
                                }
                            }
                        }
                    }
                    
                    if (fmessageObj == null) {
                        if (DEBUG) ;
                        return;
                    }
                    
                    FMessageWpp fMessage = new FMessageWpp(fmessageObj);
                    if (DEBUG) ;

                    // check media is view once
                    if (!fMessage.isViewOnce()) return;
                    Menu menu = (Menu) param.args[0];
                    
                    // Guard against duplicate entries
                    if (menu.findItem(MENU_ID_DOWNLOAD) != null) return;
                    
                    MenuItem item = menu.add(0, MENU_ID_DOWNLOAD, 0, com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.R.string.download, "Download")).setIcon(R.drawable.download);
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                    item.setOnMenuItemClickListener(item1 -> {
                        try {
                            var file = fMessage.getMediaFile();
                            if (file == null) {
                                Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.download_not_available), 1);
                                return true;
                            }
                            downloadFile(fMessage.getKey().remoteJid, file);
                        } catch (Exception e) {
                            Utils.showToast(e.getMessage(), Toast.LENGTH_LONG);
                        }
                        return true;
                    });
                }


            });
            // View Once Activity
            XposedHelpers.findAndHookMethod(WppCore.getViewOnceViewerActivityClass(classLoader), "onCreateOptionsMenu", classLoader.loadClass("android.view.Menu"),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (DEBUG) ;
                            
                            Menu menu = (Menu) param.args[0];
                            
                            // Guard against duplicate entries
                            if (menu.findItem(MENU_ID_DOWNLOAD) != null) return;
                            
                            MenuItem item = menu.add(0, MENU_ID_DOWNLOAD, 0, com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.R.string.download, "Download")).setIcon(R.drawable.download);
                            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                            item.setOnMenuItemClickListener(item1 -> {
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        var keyClass = FMessageWpp.Key.TYPE;
                                        var fieldType = ReflectionUtils.getFieldByType(param.thisObject.getClass(), keyClass);
                                        var keyMessageObj = ReflectionUtils.getObjectField(fieldType, param.thisObject);
                                        if (keyMessageObj == null) {
                                            if (DEBUG) ;
                                            return;
                                        }
                                        var fmessage = new FMessageWpp.Key(keyMessageObj).getFMessage();
                                        if (fmessage == null) {
                                            if (DEBUG) ;
                                            return;
                                        }
                                        var file = fmessage.getMediaFile();
                                        if (file == null) {
                                            Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.download_not_available), 1);
                                            return;
                                        }
                                        var userJid = fmessage.getKey().remoteJid;
                                        downloadFile(userJid, file);
                                    } catch (Exception e) {
                                        XposedBridge.log("[WAE] DownloadViewOnce Error: " + e.getMessage());
                                        Utils.showToast(e.getMessage(), Toast.LENGTH_LONG);
                                    }
                                });
                                return true;
                            });

                        }
                    });
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Download View Once";
    }
}
