package com.waenhancer.xposed.features.listeners;

import android.view.View;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.WaContactWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

import java.util.HashSet;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Field;

public class ContactItemListener extends Feature {

    public static HashSet<OnContactItemListener> contactListeners = new HashSet<>();

    private static Field cachedViewField;

    public ContactItemListener(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        var onChangeStatus = Unobfuscator.loadOnChangeStatus(classLoader);
        /* Log removed */
        var field1 = Unobfuscator.loadViewHolderField1(classLoader);
        /* Log removed */
        var absViewHolderClass = Unobfuscator.loadAbsViewHolder(classLoader);
        cachedViewField = ReflectionUtils.findFieldUsingFilter(absViewHolderClass, field -> field.getType() == View.class);
        cachedViewField.setAccessible(true);

        XposedBridge.hookMethod(onChangeStatus, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var viewHolder = field1.get(param.thisObject);
                var object = param.args[0];
                var waContact = new WaContactWpp(object);
                var view = (View) cachedViewField.get(viewHolder);
                var userJid = waContact.getUserJid();
                if (userJid.isNull()) return;

                for (OnContactItemListener listener : contactListeners) {
                    listener.onBind(waContact, view);
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Contact Item Listener";
    }

    public abstract static class OnContactItemListener {
        /**
         * Called when a contact item is bound in the RecyclerView
         *
         * @param waContact The user contact
         * @param view    The view associated with the item
         */
        public abstract void onBind(WaContactWpp waContact, View view);
    }
}