package com.waenhancer.xposed.features.privacy;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.features.listeners.ConversationItemListener;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.xposed.utils.Utils;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class TagMessage extends Feature {
    public TagMessage(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {

        Method method = Unobfuscator.loadForwardTagMethod(classLoader);
        /* Log removed */
        Class<?> forwardClass = Unobfuscator.loadForwardClassMethod(classLoader);
        /* Log removed */

        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("hidetag", false)) return;
                Object argObj = param.args[0];
                if (argObj instanceof Number) {
                    long arg = ((Number) argObj).longValue();
                    if (arg > 0) {
                        if (ReflectionUtils.isCalledFromClass(forwardClass)) {
                            if (argObj instanceof Integer) {
                                param.args[0] = 0;
                            } else if (argObj instanceof Long) {
                                param.args[0] = 0L;
                            }
                        }
                    }
                }
            }
        });

        if (prefs.getBoolean("broadcast_tag", false)) {
            hookBroadcastView();
        }
    }

    private void hookBroadcastView() throws Exception {

        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup) {
                if (fMessage.getKey().isFromMe) return;
                var dateTextView = (TextView) viewGroup.findViewById(Utils.getID("date", "id"));
                if (dateTextView == null) return;
                var dateWrapper = (ViewGroup) dateTextView.getParent();
                int id = Utils.getID("broadcast_icon", "id");
                View res = dateWrapper.findViewById(id);
                if (fMessage.isBroadcast() && res == null) {
                    var broadcast = new ImageView(dateWrapper.getContext());
                    broadcast.setId(id);
                    broadcast.setImageDrawable(DesignUtils.getDrawableByName("broadcast_status_icon"));
                    dateWrapper.addView(broadcast, 0);
                } else if (res != null) {
                    dateWrapper.removeView(res);
                }
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Tag Message";
    }
}
