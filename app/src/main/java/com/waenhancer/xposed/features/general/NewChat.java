package com.waenhancer.xposed.features.general;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

import com.waenhancer.xposed.utils.XResManager;

public class NewChat extends Feature {
    public NewChat(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() {
        var homeActivity = WppCore.getHomeActivityClass(classLoader);
        var action = prefs.getBoolean("buttonaction", true);

        if (!prefs.getBoolean("newchat", true)) return;

        XposedHelpers.findAndHookMethod(homeActivity, "onCreateOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var activity = (Activity) param.thisObject;
                var menu = (Menu) param.args[0];
                var item = menu.add(0, 0, 0, com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.R.string.new_chat, "New Chat"));
                var drawable = DesignUtils.getDrawableByName("vec_ic_chat_add");
                if (drawable == null) {
                    try {
                        if (XResManager.moduleResources != null) {
                            drawable = XResManager.moduleResources.getDrawable(R.drawable.ic_contacts, null);
                        }
                    } catch (Exception ignored) {}
                }

                if (drawable != null) {
                    drawable.setTint(action ? DesignUtils.getPrimaryTextColor() : 0xff8696a0);
                    item.setIcon(drawable);
                }

                if (action) {
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                }

                item.setOnMenuItemClickListener(item1 -> {

                    var view = new LinearLayout(activity);
                    view.setGravity(Gravity.CENTER);
                    view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
                    var edt = new EditText(view.getContext());
                    edt.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
                    edt.setMaxLines(1);
                    edt.setInputType(InputType.TYPE_CLASS_PHONE);
                    edt.setTransformationMethod(null);
                    edt.setHint(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.number_with_country_code, "Number with country code"));
                    view.addView(edt);

                    new AlertDialogWpp(activity)
                            .setTitle(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.new_chat, "New Chat"))
                            .setView(view)
                            .setPositiveButton(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.message, "Message"), (dialog, which) -> {
                                var number = edt.getText().toString();
                                var numberFomatted = number.replaceAll("[+\\-()/\\s]", "");
                                var intent = new Intent(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse("https://wa.me/" + numberFomatted));
                                intent.setPackage(Utils.getApplication().getPackageName());
                                activity.startActivity(intent);
                            })
                            .setNegativeButton(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.cancel, "Cancel"), null)
                            .show();

                    return true;
                });
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "New Chat";
    }
}
