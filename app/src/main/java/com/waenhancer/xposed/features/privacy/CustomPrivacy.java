package com.waenhancer.xposed.features.privacy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.AdapterView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;

import androidx.annotation.NonNull;


import com.waenhancer.adapter.CustomPrivacyAdapter;
import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.features.others.MenuHome;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import org.json.JSONObject;
import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import android.widget.ArrayAdapter;
import com.waenhancer.xposed.core.FeatureLoader;

public class CustomPrivacy extends Feature {
    private static final int MENU_ID_CUSTOM_PRIVACY = 0x7EAE0007;
    private static final String CONVERSATIONS_FRAGMENT = "ConversationsFragment";
    private Method chatUserJidMethod;
    private Method groupUserJidMethod;

    public CustomPrivacy(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    public static JSONObject getJSON(String number) {
        if (Objects.equals(Utils.xprefs.getString("custom_privacy_type", "0"), "0") || TextUtils.isEmpty(number))
            return new JSONObject();
        return WppCore.getPrivJSON(number + "_privacy", new JSONObject());
    }

    @Override
    public void doHook() throws Throwable {
        if (Objects.equals(Utils.xprefs.getString("custom_privacy_type", "0"), "0")) return;

        Class<?> ContactInfoActivityClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, ".ContactInfoActivity");
        Class<?> GroupInfoActivityClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, ".GroupChatInfoActivity");
        Class<?> userJidClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.UserJid");
        Class<?> groupJidClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.GroupJid");

        chatUserJidMethod = ReflectionUtils.findMethodUsingFilter(ContactInfoActivityClass, method -> method.getParameterCount() == 0 && userJidClass.isAssignableFrom(method.getReturnType()));
        groupUserJidMethod = ReflectionUtils.findMethodUsingFilter(GroupInfoActivityClass, method -> method.getParameterCount() == 0 && groupJidClass.isAssignableFrom(method.getReturnType()));

        var type = Integer.parseInt(Utils.xprefs.getString("custom_privacy_type", "0"));

        if (type == 1) {
            var hooker = new WppCore.ActivityChangeState() {
                @SuppressLint("ResourceType")
                @Override
                public void onChange(Activity activity, ChangeType type) {
                    try {
                        if (type != ChangeType.STARTED) return;
                        if (!ContactInfoActivityClass.isInstance(activity) && !GroupInfoActivityClass.isInstance(activity))
                            return;
                        if (activity.findViewById(0x7f0a9999) != null) return;
                        int id = Utils.getID("contact_info_security_card_layout", "id");
                        ViewGroup infoLayout = activity.getWindow().findViewById(id);
                        Drawable icon = DesignUtils.getDrawable(R.drawable.ic_privacy);
                        View itemView = createItemView(activity, FeatureLoader.getModuleString(activity, R.string.custom_privacy, "Custom Privacy"), FeatureLoader.getModuleString(activity, R.string.custom_privacy_sum, "Enable/Disable Custom Privacy"), icon);
                        itemView.setId(0x7f0a9999);
                        itemView.setOnClickListener((v) -> showPrivacyDialog(activity, ContactInfoActivityClass.isInstance(activity)));
                        infoLayout.addView(itemView);
                    } catch (Throwable e) {
                        logDebug(e);
                        Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
                    }
                }
            };
            WppCore.addListenerActivity(hooker);
        } else if (type == 2) {
            var hooker = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var menu = (Menu) param.args[0];
                    var activity = (Activity) param.thisObject;
                    var customPrivacy = menu.add(0, 0, 0, FeatureLoader.getModuleString(activity, com.waenhancer.R.string.custom_privacy, "Custom Privacy"));
                    customPrivacy.setIcon(DesignUtils.getDrawable(R.drawable.ic_privacy));
                    customPrivacy.setOnMenuItemClickListener(item -> {
                        showPrivacyDialog(activity, ContactInfoActivityClass.isInstance(activity));
                        return true;
                    });
                }
            };
            XposedHelpers.findAndHookMethod(ContactInfoActivityClass, "onCreateOptionsMenu", Menu.class, hooker);
            XposedHelpers.findAndHookMethod(GroupInfoActivityClass, "onCreateOptionsMenu", Menu.class, hooker);
        }

        if (type == 0) return;

        var icon = DesignUtils.resizeDrawable(DesignUtils.getDrawable(R.drawable.ic_privacy), Utils.dipToPixels(24), Utils.dipToPixels(24));
        if (icon != null) icon.setTint(0xff8696a0);
        MenuHome.menuItems.add((menu, activity) -> {
            if (menu.findItem(MENU_ID_CUSTOM_PRIVACY) != null) return;
            String title = FeatureLoader.getModuleString(activity, com.waenhancer.R.string.custom_privacy, "Custom Privacy");
            if (title == null || title.isEmpty()) {
                title = "Custom Privacy";
            }
            menu.add(0, MENU_ID_CUSTOM_PRIVACY, 0, title).setIcon(icon).setOnMenuItemClickListener(item -> {
                showCustomPrivacyList(activity, ContactInfoActivityClass, GroupInfoActivityClass);
                return true;
            });
        });
    }


    private View createItemView(Activity activity, String title, String summary, Drawable icon) {
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        mainLayout.setOrientation(LinearLayout.HORIZONTAL);
        mainLayout.setPadding(16, 16, 16, 16);

        ImageView imageView = new ImageView(activity);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                Utils.dipToPixels(20),
                Utils.dipToPixels(20)
        );
        imageParams.setMargins(Utils.dipToPixels(20), 0, Utils.dipToPixels(16), Utils.dipToPixels(20));
        imageView.setLayoutParams(imageParams);
        icon.setTint(0xff8696a0);
        imageView.setImageDrawable(icon);

        LinearLayout textContainer = new LinearLayout(activity);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        containerParams.setMarginStart(16);
        textContainer.setLayoutParams(containerParams);
        textContainer.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(activity);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleView.setLayoutParams(titleParams);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        titleView.setText(title);
        titleView.setTextColor(DesignUtils.getPrimaryTextColor());

        TextView summaryView = new TextView(activity);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        summaryParams.setMarginStart(4);
        summaryView.setLayoutParams(summaryParams);
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        summaryView.setText(summary);

        textContainer.addView(titleView);
        textContainer.addView(summaryView);

        mainLayout.addView(imageView);
        mainLayout.addView(textContainer);

        return mainLayout;
    }

    private void showCustomPrivacyList(Activity activity, Class<?> contactClass, Class<?> groupClass) {

        SharedPreferences pprefs = WppCore.getPrivPrefs();
        var maps = pprefs.getAll();
        ArrayList<CustomPrivacyAdapter.Item> list = new ArrayList<>();
        for (var key : maps.keySet()) {
            if (key.endsWith("_privacy")) {
                var number = key.replace("_privacy", "");
                var userJid = new FMessageWpp.UserJid(number + (number.length() > 14 ? "@g.us" : "@s.whatsapp.net"));

                var contactName = WppCore.getContactName(userJid);

                if (TextUtils.isEmpty(contactName)) {
                    contactName = number;
                }
                CustomPrivacyAdapter.Item item = new CustomPrivacyAdapter.Item();
                item.name = contactName;
                item.number = number;
                item.key = key;
                list.add(item);
            }
        }

        if (list.isEmpty()) {
            Utils.showToast(FeatureLoader.getModuleString(activity, R.string.no_contact_with_custom_privacy, "No contact with custom privacy!"), Toast.LENGTH_SHORT);
            return;
        }

        AlertDialogWpp builder = new AlertDialogWpp(activity);
        builder.setTitle(FeatureLoader.getModuleString(activity, R.string.custom_privacy, "Custom Privacy"));
        ListView listView = new ListView(activity);
        listView.setAdapter(new CustomPrivacyAdapter(activity, pprefs, list, contactClass, groupClass));
        builder.setView(listView);
        builder.show();
    }


    private void showPrivacyDialog(Activity activity, boolean isChat) {
        var userJid = getUserJid(activity, isChat);
        if (userJid.isNull()) return;
        AlertDialogWpp builder = createPrivacyDialog(activity, userJid.getPhoneNumber());
        builder.show();
    }

    private FMessageWpp.UserJid getUserJid(Activity activity, boolean isChat) {
        if (isChat) {
            return new FMessageWpp.UserJid(ReflectionUtils.callMethod(chatUserJidMethod, activity));
        } else {
            return new FMessageWpp.UserJid(ReflectionUtils.callMethod(groupUserJidMethod, activity));
        }
    }

    private int getAppScopedAlwaysTypingCount(SharedPreferences pprefs, String currentNumber) {
        int count = 0;
        try {
            var maps = pprefs.getAll();
            for (var key : maps.keySet()) {
                if (key.endsWith("_privacy")) {
                    var number = key.replace("_privacy", "");
                    if (number.equals(currentNumber)) {
                        continue;
                    }
                    String jsonStr = pprefs.getString(key, null);
                    if (jsonStr != null) {
                        JSONObject json = new JSONObject(jsonStr);
                        if (json.optInt("AlwaysTyping", 0) == 2) {
                            count++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return count;
    }

    private AlertDialogWpp createPrivacyDialog(Activity activity, String number) {
        AlertDialogWpp builder = new AlertDialogWpp(activity);
        builder.setFullHeight(true);
        builder.setTitle(FeatureLoader.getModuleString(activity, R.string.custom_privacy, "Custom Privacy"));

        String[] items = {
                FeatureLoader.getModuleString(activity, R.string.hideread, "Hide Blue Ticks"),
                FeatureLoader.getModuleString(activity, R.string.hidestatusview, "Hide Status View"),
                FeatureLoader.getModuleString(activity, R.string.hidereceipt, "Hide Delivered"),
                FeatureLoader.getModuleString(activity, R.string.ghostmode, "Hide Typing"),
                FeatureLoader.getModuleString(activity, R.string.ghostmode_r, "Hide Recording Audio"),
                FeatureLoader.getModuleString(activity, R.string.block_call, "Block Call")
        };

        String[] itemsKeys = {
                "HideSeen", "HideViewStatus", "HideReceipt", "HideTyping", "HideRecording", "BlockCall"
        };

        boolean[] checkedItems = loadPreferences(number, itemsKeys);

        // Load AlwaysTyping selection
        JSONObject json = CustomPrivacy.getJSON(number);
        int alwaysTypingVal = json.optInt("AlwaysTyping", 0);
        int alwaysTypingTypeVal = json.optInt("AlwaysTypingType", 0);
        final int[] previousSelection = { alwaysTypingVal };

        // Create Custom View for dropdown list
        LinearLayout customLayout = new LinearLayout(activity);
        customLayout.setOrientation(LinearLayout.VERTICAL);
        customLayout.setPadding(Utils.dipToPixels(20), Utils.dipToPixels(10), Utils.dipToPixels(20), Utils.dipToPixels(10));

        TextView labelView = new TextView(activity);
        labelView.setText(FeatureLoader.getModuleString(activity, R.string.always_typing_mode, "Always Typing Mode"));
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        labelView.setTextColor(DesignUtils.getPrimaryTextColor());
        labelView.setPadding(0, 0, 0, Utils.dipToPixels(8));
        customLayout.addView(labelView);

        Spinner spinner = new Spinner(activity);
        String[] options = {
                FeatureLoader.getModuleString(activity, R.string.always_typing_off, "Off"),
                FeatureLoader.getModuleString(activity, R.string.always_typing_chat, "When in their chat (Safe & Active)"),
                FeatureLoader.getModuleString(activity, R.string.always_typing_app, "Always when using WhatsApp [Max 2 contacts]")
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_item,
                options
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(alwaysTypingVal);
        customLayout.addView(spinner);

        TextView typeLabelView = new TextView(activity);
        typeLabelView.setText(FeatureLoader.getModuleString(activity, R.string.always_typing_type, "Simulated Status Kind"));
        typeLabelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        typeLabelView.setTextColor(DesignUtils.getPrimaryTextColor());
        typeLabelView.setPadding(0, Utils.dipToPixels(12), 0, Utils.dipToPixels(8));
        typeLabelView.setVisibility(alwaysTypingVal == 0 ? View.GONE : View.VISIBLE);
        customLayout.addView(typeLabelView);

        Spinner typeSpinner = new Spinner(activity);
        String[] typeOptions = {
                FeatureLoader.getModuleString(activity, R.string.status_always_typing, "Always Typing"),
                FeatureLoader.getModuleString(activity, R.string.status_always_recording, "Always Recording Voice")
        };
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_item,
                typeOptions
        );
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);
        typeSpinner.setSelection(alwaysTypingTypeVal);
        typeSpinner.setVisibility(alwaysTypingVal == 0 ? View.GONE : View.VISIBLE);
        customLayout.addView(typeSpinner);

        TextView warningView = new TextView(activity);
        warningView.setText(FeatureLoader.getModuleString(activity, R.string.always_typing_warning, "⚠️ Limit to at most 1–2 contacts to protect your account from server-side spam filters."));
        warningView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        warningView.setTextColor(0xFFD32F2F); // Red warning color
        warningView.setPadding(0, Utils.dipToPixels(4), 0, 0);
        warningView.setVisibility(alwaysTypingVal == 2 ? View.VISIBLE : View.GONE);
        customLayout.addView(warningView);

        TextView delayNoteView = new TextView(activity);
        delayNoteView.setText(FeatureLoader.getModuleString(activity, R.string.always_typing_delay_note, "ℹ️ It will add random delays on the homepage to avoid being marked as spam by WhatsApp."));
        delayNoteView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        delayNoteView.setTextColor(0xFF757575); // Gray note color
        delayNoteView.setPadding(0, Utils.dipToPixels(4), 0, 0);
        delayNoteView.setVisibility(alwaysTypingVal == 2 ? View.VISIBLE : View.GONE);
        customLayout.addView(delayNoteView);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    typeLabelView.setVisibility(View.GONE);
                    typeSpinner.setVisibility(View.GONE);
                    warningView.setVisibility(View.GONE);
                    delayNoteView.setVisibility(View.GONE);
                } else {
                    typeLabelView.setVisibility(View.VISIBLE);
                    typeSpinner.setVisibility(View.VISIBLE);
                    if (position == 2) {
                        SharedPreferences pprefs = WppCore.getPrivPrefs();
                        int currentCount = getAppScopedAlwaysTypingCount(pprefs, number);
                        if (currentCount >= 2) {
                            AlertDialogWpp limitBuilder = new AlertDialogWpp(activity);
                            limitBuilder.setTitle(FeatureLoader.getModuleString(activity, R.string.always_typing_dialog_title, "Protect Your Account"));
                            limitBuilder.setMessage(FeatureLoader.getModuleString(activity, R.string.always_typing_dialog_desc, "To prevent WhatsApp servers from flagging your account for concurrent typing patterns, Always Typing (App-Scoped) can only be enabled for up to 2 contacts at a time. Please disable it for another contact first."));
                            limitBuilder.setPositiveButton("OK", null);
                            limitBuilder.show();

                            spinner.setSelection(previousSelection[0]);
                            return;
                        }
                        warningView.setVisibility(View.VISIBLE);
                        delayNoteView.setVisibility(View.VISIBLE);
                    } else {
                        warningView.setVisibility(View.GONE);
                        delayNoteView.setVisibility(View.GONE);
                    }
                }
                previousSelection[0] = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        builder.setView(customLayout);
        builder.setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked);
        builder.setPositiveButton("OK", (dialog, which) -> savePreferences(number, itemsKeys, checkedItems, spinner.getSelectedItemPosition(), typeSpinner.getSelectedItemPosition()));
        builder.setNegativeButton(FeatureLoader.getModuleString(activity, R.string.cancel, "Cancel"), null);

        return builder;
    }

    private boolean[] loadPreferences(String number, String[] itemsKeys) {
        boolean[] checkedItems = new boolean[itemsKeys.length];
        JSONObject json = CustomPrivacy.getJSON(number);

        for (int i = 0; i < itemsKeys.length; i++) {
            String globalKey = getGlobalKey(itemsKeys[i]);
            checkedItems[i] = json.optBoolean(itemsKeys[i], getDefaultPreference(globalKey));
        }

        return checkedItems;
    }

    private String getGlobalKey(String itemKey) {
        return switch (itemKey) {
            case "HideSeen" -> "hideread";
            case "HideViewStatus" -> "hidestatusview";
            case "HideReceipt" -> "hidereceipt";
            case "HideTyping" -> "ghostmode_t";
            case "HideRecording" -> "ghostmode_r";
            case "BlockCall" -> "call_privacy";
            default -> "";
        };
    }

    private boolean getDefaultPreference(String globalKey) {
        if (globalKey.equals("call_privacy")) {
            return Objects.equals(prefs.getString(globalKey, "0"), "1");
        } else {
            return prefs.getBoolean(globalKey, false);
        }
    }

    private void savePreferences(String number, String[] itemsKeys, boolean[] checkedItems, int alwaysTypingVal, int alwaysTypingTypeVal) {
        try {
            JSONObject jsonObject = new JSONObject();
            for (int i = 0; i < itemsKeys.length; i++) {
                String globalKey = getGlobalKey(itemsKeys[i]);
                if (globalKey.equals("call_privacy")) {
                    if (Objects.equals(prefs.getString(globalKey, "0"), "1") != checkedItems[i])
                        jsonObject.put(itemsKeys[i], checkedItems[i]);
                } else {
                    if (prefs.getBoolean(globalKey, false) != checkedItems[i])
                        jsonObject.put(itemsKeys[i], checkedItems[i]);
                }
            }
            jsonObject.put("AlwaysTyping", alwaysTypingVal);
            jsonObject.put("AlwaysTypingType", alwaysTypingTypeVal);
            WppCore.setPrivJSON(number + "_privacy", jsonObject);
        } catch (Exception e) {
            Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Custom Privacy";
    }
}