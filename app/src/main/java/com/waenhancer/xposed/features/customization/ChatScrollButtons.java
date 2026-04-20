package com.waenhancer.xposed.features.customization;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ChatScrollButtons extends Feature {

    public ChatScrollButtons(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        // Check if feature is enabled
        if (!prefs.getBoolean("go_to_first_message", true)) return;
        
        try {
            Class<?> conversationClass = XposedHelpers.findClass("com.whatsapp.Conversation", this.classLoader);
            
            // Hook onCreateOptionsMenu to add menu item
            XposedBridge.hookAllMethods(conversationClass, "onCreateOptionsMenu", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Menu menu = (Menu) param.args[0];
                    Activity activity = (Activity) param.thisObject;
                    
                    // Add "Go to First Message" menu item
                    MenuItem goToFirstItem = menu.add(0, 1001, 0, "Go to First Message");
                    goToFirstItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                    goToFirstItem.setOnMenuItemClickListener(item -> {
                        scrollToTop(activity);
                        return true;
                    });
                }
            });
        } catch (Exception e) {
            XposedBridge.log("ChatScrollButtons hook failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void scrollToTop(Activity activity) {
        try {
            View rootView = activity.getWindow().getDecorView();
            
            // Find the conversation root layout
            ViewGroup conversationRootLayout = rootView.findViewById(Utils.getID("conversation_root_layout", "id"));
            if (conversationRootLayout == null) {
                XposedBridge.log("ChatScrollButtons: conversation_root_layout not found");
                return;
            }

            // Find the message list view (ListView with android.R.id.list)
            ListView messagesList = findMessagesList(conversationRootLayout);
            if (messagesList == null) {
                XposedBridge.log("ChatScrollButtons: messagesList not found");
                return;
            }

            if (messagesList.getCount() > 0) {
                messagesList.smoothScrollToPosition(0);
            }
        } catch (Exception e) {
            XposedBridge.log("ChatScrollButtons scroll error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ListView findMessagesList(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ListView) {
                ListView listView = (ListView) child;
                if (listView.getId() == android.R.id.list) {
                    return listView;
                }
            } else if (child instanceof ViewGroup) {
                ListView found = findMessagesList((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Chat Scroll Buttons";
    }
}
