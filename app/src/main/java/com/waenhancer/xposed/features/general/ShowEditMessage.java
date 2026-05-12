package com.waenhancer.xposed.features.general;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;

import com.waenhancer.adapter.MessageAdapter;
import com.waenhancer.views.NoScrollListView;
import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.db.MessageHistory;
import com.waenhancer.xposed.core.db.MessageStore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.features.listeners.ConversationItemListener;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import java.util.ArrayList;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ShowEditMessage extends Feature {

    public ShowEditMessage(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("antieditmessages", false)) return;

        var onMessageEdit = Unobfuscator.loadMessageEditMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onMessageEdit));

        var callerMessageEditMethod = Unobfuscator.loadCallerMessageEditMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(callerMessageEditMethod));

        var getEditMessage = Unobfuscator.loadGetEditMessageMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(getEditMessage));

        XposedBridge.hookMethod(onMessageEdit, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var editMessage = getEditMessage.invoke(null, param.args[0]);
                if (editMessage == null) return;
                var invoked = callerMessageEditMethod.invoke(null, param.args[0]);
                long timestamp = XposedHelpers.getLongField(invoked, "A00");
                var fMessage = new FMessageWpp(param.args[0]);
                var key = fMessage.getKey();
                if (key == null || key.messageID == null) return;
                String messageKey = key.messageID;
                long id = fMessage.getRowId();
                var origMessage = MessageStore.getInstance().getCurrentMessageByID(id);
                String newMessage = fMessage.getMessageStr();
                if (newMessage == null) {
                    var methods = ReflectionUtils.findAllMethodsUsingFilter(param.args[0].getClass(), method -> method.getReturnType() == String.class && ReflectionUtils.isOverridden(method));
                    for (var method : methods) {
                        newMessage = (String) method.invoke(param.args[0]);
                        if (newMessage != null) break;
                    }
                    if (newMessage == null) return;
                }
                try {
                    var message = MessageHistory.getInstance().getMessages(messageKey);
                    if (message == null) {
                        MessageHistory.getInstance().insertMessage(messageKey, origMessage, 0);
                    }
                    MessageHistory.getInstance().insertMessage(messageKey, newMessage, timestamp);
                } catch (Exception e) {
                    logDebug(e);
                }
            }
        });

        var strEmoji = "\uD83D\uDCDD";

        ConversationItemListener.conversationListeners.add(
                new ConversationItemListener.OnConversationItemListener() {

                    @Override
                    public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup) {
                        var key = fMessage.getKey();
                        if (key == null || key.messageID == null) {
                            return;
                        }

                        var messages = MessageHistory.getInstance().getMessages(key.messageID);
                        boolean hasHistory = (messages != null && !messages.isEmpty());

                        var dateView = (TextView) viewGroup.findViewById(Utils.getID("date", "id"));
                        var nativeLabel = findEditHistoryAnchor(viewGroup);

                        // We only show/bind edit history if a native edited label is present, or if we have locally captured edit history
                        if (nativeLabel != null || hasHistory) {
                            ;
                            dumpTextViews(viewGroup);
                            
                            // Hide any previous custom injected Edited views first to handle fresh layouts
                            if (dateView != null) {
                                ViewGroup parent = (ViewGroup) dateView.getParent();
                                if (parent != null) {
                                    View injected = parent.findViewById(17784004);
                                    if (injected != null) {
                                        injected.setVisibility(View.GONE);
                                    }
                                }
                            }

                            if (nativeLabel != null) {
                                ;
                                bindHistoryClick(nativeLabel, key.messageID, strEmoji, false);

                                // Separate Clicks: We have a dedicated "Edited" label, so remove edit click listener from dateView to keep them completely separate!
                                if (dateView != null) {
                                    Utils.setViewClickListener(dateView, "show_edit_message", null);
                                }
                            } else {
                                ;
                                if (dateView != null) {
                                    TextView injectedView = injectEditHistoryView(viewGroup, dateView, strEmoji);
                                    if (injectedView != null) {
                                        bindHistoryClick(injectedView, key.messageID, strEmoji, false);
                                    }
                                    
                                    // Separate Clicks: Since we injected a separate view, dateView should have no edit click listener
                                    Utils.setViewClickListener(dateView, "show_edit_message", null);
                                }
                            }
                        } else {
                            // Recycle Cleanup: Clear listeners and hide custom injected views on non-edited rows to prevent click carries or leaks!
                            if (nativeLabel != null) {
                                Utils.setViewClickListener(nativeLabel, "show_edit_message", null);
                            }
                            if (dateView != null) {
                                Utils.setViewClickListener(dateView, "show_edit_message", null);
                                ViewGroup parent = (ViewGroup) dateView.getParent();
                                if (parent != null) {
                                    View injected = parent.findViewById(17784004);
                                    if (injected != null) {
                                        injected.setVisibility(View.GONE);
                                    }
                                }
                            }
                        }
                    }

                    private void dumpTextViews(View view) {
                        if (view == null) return;
                        if (view instanceof TextView) {
                            TextView tv = (TextView) view;
                            String resName = "none";
                            try {
                                if (tv.getId() != View.NO_ID) {
                                    resName = tv.getResources().getResourceEntryName(tv.getId());
                                }
                            } catch (Exception ignored) {}
                            de.robv.android.xposed.XposedBridge.log("[WAE] ShowEditMessage ID-DUMP: id=" + tv.getId() + ", resName=" + resName + ", text='" + tv.getText() + "'");
                        } else if (view instanceof ViewGroup) {
                            ViewGroup vg = (ViewGroup) view;
                            for (int i = 0; i < vg.getChildCount(); i++) {
                                dumpTextViews(vg.getChildAt(i));
                            }
                        }
                    }
                }
        );

    }

    private void bindHistoryClick(TextView textView, String messageKey, String indicator, boolean appendIndicator) {
        if (textView == null) return;
        textView.getPaint().setUnderlineText(true);
        Utils.setViewClickListener(textView, "show_edit_message", v -> {
            try {
                var messages = MessageHistory.getInstance().getMessages(messageKey);
                if (messages == null || messages.isEmpty()) {
                    Utils.showToast("No edit history captured for this message", 0);
                } else {
                    showBottomDialog(messages);
                }
            } catch (Exception exception0) {
                logDebug(exception0);
            }
        });
    }

    private TextView injectEditHistoryView(ViewGroup viewGroup, TextView dateView, String indicator) {
        if (dateView == null) return null;
        ViewGroup parent = (ViewGroup) dateView.getParent();
        if (parent == null) return null;

        int injectId = 17784004; // Custom unique ID for injected separate view
        TextView existing = (TextView) parent.findViewById(injectId);
        if (existing != null) {
            existing.setVisibility(View.VISIBLE);
            return existing;
        }

        TextView editView = new TextView(dateView.getContext());
        editView.setId(injectId);
        editView.setText(" " + indicator + " " + com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.message_original, "Edited"));
        editView.setTextSize(11.0f);
        editView.setTextColor(DesignUtils.getUnSeenColor());
        editView.getPaint().setUnderlineText(true);
        editView.setAlpha(0.85f);
        editView.setPadding(4, 0, 4, 0);

        ViewGroup.LayoutParams lp = dateView.getLayoutParams();
        if (lp != null) {
            if (lp instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams origLp = (LinearLayout.LayoutParams) lp;
                LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                newLp.gravity = origLp.gravity;
                newLp.setMargins(origLp.leftMargin, origLp.topMargin, origLp.rightMargin, origLp.bottomMargin);
                editView.setLayoutParams(newLp);
            } else {
                editView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
        }

        if (parent instanceof LinearLayout) {
            LinearLayout linearLayout = (LinearLayout) parent;
            int index = linearLayout.indexOfChild(dateView);
            if (linearLayout.getOrientation() == LinearLayout.HORIZONTAL) {
                linearLayout.addView(editView, index + 1);
            } else {
                linearLayout.addView(editView);
            }
        } else {
            parent.addView(editView);
        }

        return editView;
    }

    private TextView findEditHistoryAnchor(ViewGroup root) {
        int editLabelId = Utils.getID("edit_label", "id");
        if (editLabelId != 0) {
            View view = root.findViewById(editLabelId);
            if (view instanceof TextView) {
                return (TextView) view;
            }
        }
        return findTextViewByResourceName(root, "edit");
    }

    private TextView findTextViewByResourceName(View view, String token) {
        if (view instanceof TextView && hasResourceName(view, token)) {
            return (TextView) view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView child = findTextViewByResourceName(group.getChildAt(i), token);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private boolean hasResourceName(View view, String token) {
        int id = view.getId();
        if (id == View.NO_ID) {
            return false;
        }
        try {
            String entryName = view.getResources().getResourceEntryName(id);
            return entryName != null && entryName.contains(token);
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressLint("SetTextI18n")
    private void showBottomDialog(ArrayList<MessageHistory.MessageItem> messages) {
        Objects.requireNonNull(WppCore.getCurrentConversation()).runOnUiThread(() -> {
            var ctx = (Context) WppCore.getCurrentConversation();

            var dialog = new AlertDialogWpp(ctx);
            dialog.setTitle(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.edited_history, "Edit History"));

            var adapter = new MessageAdapter(ctx, messages);
            ListView listView = new NoScrollListView(ctx);
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            listView.setLayoutParams(layoutParams2);
            listView.setAdapter(adapter);

            dialog.setView(listView);
            dialog.setPositiveButton("OK", (dialogInterface, which) -> dialogInterface.dismiss());
            dialog.show();
        });
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Show Edit Message";
    }

}
