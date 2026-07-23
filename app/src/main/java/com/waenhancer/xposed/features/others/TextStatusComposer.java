package com.waenhancer.xposed.features.others;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.waenhancer.R;
import com.waenhancer.views.dialog.SimpleColorPickerDialog;
import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class TextStatusComposer extends Feature {
    private static final ColorData colorData = new ColorData();
    private Class<?> textComposerClass;

    public TextStatusComposer(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        textComposerClass = WppCore.getTextStatusComposerFragmentClass(classLoader);

        try {
            var methodOnCreate = ReflectionUtils.findMethodUsingFilter(textComposerClass, method -> 
                method.getParameterCount() == 2 && 
                ((method.getParameterTypes()[0] == Bundle.class && method.getParameterTypes()[1] == View.class) ||
                 (method.getParameterTypes()[0] == View.class && method.getParameterTypes()[1] == Bundle.class))
            );
            XposedBridge.hookMethod(methodOnCreate, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var activity = WppCore.getCurrentActivity();
                    
                    View tempViewRoot = null;
                    if (param.args[0] instanceof View) {
                        tempViewRoot = (View) param.args[0];
                    } else if (param.args[1] instanceof View) {
                        tempViewRoot = (View) param.args[1];
                    }
                    
                    if (tempViewRoot == null) {
                        /* Log removed */
                        return;
                    }
                    final View viewRoot = tempViewRoot;

                    var pickerColor = viewRoot.findViewById(Utils.getID("color_picker_btn", "id"));
                    var entry = (EditText) viewRoot.findViewById(Utils.getID("entry", "id"));
                    if (pickerColor != null) {
                        pickerColor.setOnLongClickListener(v -> {
                            int currentBgColor = -1;
                            View bgView = viewRoot.findViewById(Utils.getID("background", "id"));
                            if (bgView != null && bgView.getBackground() instanceof ColorDrawable) {
                                currentBgColor = ((ColorDrawable) bgView.getBackground()).getColor();
                            }
                            if (currentBgColor == -1 && activity != null && activity.getWindow() != null) {
                                var decorView = activity.getWindow().getDecorView();
                                if (decorView.getBackground() instanceof ColorDrawable) {
                                    currentBgColor = ((ColorDrawable) decorView.getBackground()).getColor();
                                }
                            }
                            if (currentBgColor == -1 && colorData.backgroundColor != -1) {
                                currentBgColor = colorData.backgroundColor;
                            }

                            var dialog = new SimpleColorPickerDialog(activity, currentBgColor, color -> {
                                try {
                                    activity.getWindow().setBackgroundDrawable(new ColorDrawable(color));
                                    viewRoot.findViewById(Utils.getID("background", "id")).setBackgroundColor(color);
                                    var controls = viewRoot.findViewById(Utils.getID("controls", "id"));
                                    if (controls != null) controls.setBackgroundColor(color);
                                    colorData.backgroundColor = color;
                                } catch (Exception e) {
                                    log(e);
                                }
                            });
                            dialog.show();
                            return true;
                        });
                    }

                    var textColor = viewRoot.findViewById(Utils.getID("font_picker_btn", "id"));
                    if (textColor != null) {
                        textColor.setOnLongClickListener(v -> {
                            int currentTextColor = -1;
                            if (entry != null) {
                                currentTextColor = entry.getCurrentTextColor();
                            }
                            if (currentTextColor == -1 && colorData.textColor != -1) {
                                currentTextColor = colorData.textColor;
                            }

                            var dialog = new SimpleColorPickerDialog(activity, currentTextColor, color -> {
                                colorData.textColor = color;
                                if (entry != null) entry.setTextColor(color);
                            });
                            dialog.show();
                            return true;
                        });
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to hook onViewCreated for " + textComposerClass.getName() + ": " + t.getMessage());
        }

        var methodsTextStatus = Unobfuscator.loadTextStatusData(classLoader);

        for (var method : methodsTextStatus) {
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
