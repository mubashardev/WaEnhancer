package com.waenhancer.xposed.features.others;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Stickers extends Feature {
    public Stickers(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("alertsticker", false)) return;
        var sendStickerMethods = Unobfuscator.loadSendStickerMethods(classLoader);
        for (var method : sendStickerMethods) {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                private Unhook unhooked;

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    unhooked = XposedHelpers.findAndHookMethod(View.class, "setOnClickListener", View.OnClickListener.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            View.OnClickListener mCaptureOnClickListener = (View.OnClickListener) param.args[0];
                            if (mCaptureOnClickListener == null) return;
                            if (!(param.thisObject instanceof ViewGroup)) return;
                            var view = (View) param.thisObject;
                            if (view.findViewById(Utils.getID("sticker", "id")) == null) return;

                            param.args[0] = (View.OnClickListener) v -> {
                                var context = view.getContext();
                                var dialog = new AlertDialogWpp(view.getContext());
                                dialog.setTitle(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.send_sticker));

                                var stickerView = (ImageView) view.findViewById(Utils.getID("sticker", "id"));
                                LinearLayout linearLayout = new LinearLayout(context);
                                linearLayout.setOrientation(LinearLayout.VERTICAL);
                                linearLayout.setGravity(Gravity.CENTER_HORIZONTAL);
                                var padding = Utils.dipToPixels(16);
                                linearLayout.setPadding(padding, padding, padding, padding);
                                var image = new ImageView(context);
                                var size = Utils.dipToPixels(72);
                                var params = new LinearLayout.LayoutParams(size, size);
                                params.bottomMargin = padding;
                                image.setLayoutParams(params);
                                image.setImageDrawable(stickerView.getDrawable());
                                linearLayout.addView(image);

                                TextView text = new TextView(context);
                                text.setText(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.do_you_want_to_send_sticker));
                                text.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                                linearLayout.addView(text);


                                dialog.setView(linearLayout);
                                dialog.setPositiveButton("Send Sticker", (dialog1, which) -> mCaptureOnClickListener.onClick(view));
                                dialog.setNegativeButton("Cancel", null);
                                dialog.show();
                            };
                        }
                    });
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    unhooked.unhook();
                }
            });
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Stickers";
    }
}
