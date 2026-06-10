package com.waenhancer.xposed.features.others;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.waenhancer.views.dialog.SimpleColorPickerDialog;
import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class TextStatusComposer extends Feature {
    private static final ColorData colorData = new ColorData();

    public TextStatusComposer(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("statuscomposer", false)) return;

        var clazz = WppCore.getTextStatusComposerFragmentClass(classLoader);
        var methodOnCreate = ReflectionUtils.findMethodUsingFilter(clazz, method -> method.getParameterCount() == 2 && method.getParameterTypes()[0] == Bundle.class && method.getParameterTypes()[1] == View.class);
        XposedBridge.hookMethod(methodOnCreate,
                new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        logDebug("afterHookedMethod", "TextStatusComposer");
                        var activity = WppCore.getCurrentActivity();
                        var viewRoot = (View) param.args[1];
                        var pickerColor = viewRoot.findViewById(Utils.getID("color_picker_btn", "id"));
                        var entry = (EditText) viewRoot.findViewById(Utils.getID("entry", "id"));

                        pickerColor.setOnLongClickListener(v -> {
                            var dialog = new SimpleColorPickerDialog(activity, color -> {
                                try {
                                    activity.getWindow().setBackgroundDrawable(new ColorDrawable(color));
                                    viewRoot.findViewById(Utils.getID("background","id")).setBackgroundColor(color);
                                    var controls = viewRoot.findViewById(Utils.getID("controls", "id"));
                                    controls.setBackgroundColor(color);
                                    colorData.backgroundColor = color;
                                } catch (Exception e) {
                                    log(e);
                                }
                            });
                            dialog.show();
                            return true;
                        });

                        var textColor = viewRoot.findViewById(Utils.getID("font_picker_btn", "id"));
                        textColor.setOnLongClickListener(v -> {
                            var dialog = new SimpleColorPickerDialog(activity, color -> {
                                colorData.textColor = color;
                                entry.setTextColor(color);
                            });
                            dialog.show();
                            return true;
                        });
                    }
                });


        var methodsTextStatus = Unobfuscator.loadTextStatusData(classLoader);

        for (var method : methodsTextStatus) {
            logDebug("setColorTextComposer", Unobfuscator.getMethodDescriptor(method));
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    var textData = param.args[0];
                    if (textData == null) return;
                    if (colorData.textColor != -1)
                        XposedHelpers.setObjectField(textData, "textColor", colorData.textColor);
                    if (colorData.backgroundColor != -1)
                        XposedHelpers.setObjectField(textData, "backgroundColor", colorData.backgroundColor);
                    colorData.textColor = -1;
                    colorData.backgroundColor = -1;
                }
            });
        }

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Text Status Composer";
    }

    public static class ColorData {
        public int textColor = -1;
        public int backgroundColor = -1;
    }
}
