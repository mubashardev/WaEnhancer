package com.waenhancer.preference;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.waenhancer.R;
import com.waenhancer.activities.ContactPickerActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ContactPickerPreference extends Preference implements Preference.OnPreferenceClickListener {

    public static final int REQUEST_CONTACT_PICKER = 0xff2515;
    private CharSequence summaryOff;
    private CharSequence summaryOn;
    private ArrayList<String> mContacts;
    private int maxSelection = -1;

    public ContactPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }


    public ContactPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public ContactPickerPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    public static final java.util.Map<String, java.lang.ref.WeakReference<ContactPickerPreference>> activePreferences = new java.util.concurrent.ConcurrentHashMap<>();

    public static void updatePreferenceValue(String key, ArrayList<String> contacts) {
        java.lang.ref.WeakReference<ContactPickerPreference> ref = activePreferences.get(key);
        if (ref != null) {
            ContactPickerPreference pref = ref.get();
            if (pref != null) {
                pref.setContacts(contacts);
            }
        }
    }

    public void setMaxSelection(int max) {
        this.maxSelection = max;
    }

    public void setContacts(ArrayList<String> contacts) {
        mContacts = contacts;
        var prefs = getSharedPreferences() != null
                ? getSharedPreferences()
                : PreferenceManager.getDefaultSharedPreferences(getContext());
        prefs.edit().putString(getKey(), String.valueOf(mContacts)).apply();
        if (mContacts != null && !mContacts.isEmpty()) {
            setSummary(String.format(String.valueOf(summaryOn), mContacts.size()));
        } else {
            setSummary(String.valueOf(summaryOff));
        }
    }

    private Activity getActivityContext() {
        Context context = getContext();
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        Activity activity = getActivityContext();
        if (activity == null) {
            return true;
        }

        if (activity.getPackageName().contains("whatsapp")) {
            try {
                Class<?> statusDistributionClass = com.waenhancer.xposed.features.others.ActivityController.getStatusDistributionClass();
                if (statusDistributionClass != null) {
                    Intent intent = new Intent();
                    intent.setClassName(activity.getPackageName(),
                            "com.whatsapp.status.audienceselector.StatusTemporalRecipientsActivity");
                    intent.putExtra("contact_mode", true);
                    intent.putExtra("is_black_list", false);

                    ArrayList<Object> listContacts = new ArrayList<>();
                    if (mContacts != null) {
                        for (String contact : mContacts) {
                            try {
                                String jidStr = contact;
                                if (!jidStr.contains("@")) {
                                    jidStr = jidStr + "@s.whatsapp.net";
                                }
                                Object jid = com.waenhancer.xposed.core.WppCore.createUserJid(jidStr);
                                if (jid != null) {
                                    listContacts.add(jid);
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    java.lang.reflect.Constructor<?> constructor = com.waenhancer.xposed.utils.ReflectionUtils.findConstructorUsingFilter(statusDistributionClass,
                            c -> c.getParameterCount() > 5);
                    Object[] params = com.waenhancer.xposed.utils.ReflectionUtils.initArray(constructor.getParameterTypes());
                    var lists = com.waenhancer.xposed.utils.ReflectionUtils.findClassesOfType(constructor.getParameterTypes(), java.util.List.class);
                    for (int i = 0; i < lists.size(); i++) {
                        params[lists.get(i).first] = new ArrayList<>();
                    }
                    params[lists.get(0).first] = listContacts;
                    android.os.Parcelable instance = (android.os.Parcelable) constructor.newInstance(params);
                    intent.putExtra("status_distribution", instance);

                    com.waenhancer.xposed.features.others.ActivityController.setPickingKey(getKey());

                    activity.startActivityForResult(intent, REQUEST_CONTACT_PICKER);
                    return true;
                }
            } catch (Exception e) {
                de.robv.android.xposed.XposedBridge.log("[WaEnhancerX] Failed to launch WhatsApp contact picker: " + e.getMessage());
            }
        }

        Intent intent = new Intent(getContext(), ContactPickerActivity.class);
        intent.putExtra(ContactPickerActivity.EXTRA_KEY, getKey());
        intent.putStringArrayListExtra(ContactPickerActivity.EXTRA_SELECTED_CONTACTS,
                mContacts != null ? new ArrayList<>(mContacts) : new ArrayList<>());
        intent.putExtra("limit", maxSelection);
        activity.startActivityForResult(intent, REQUEST_CONTACT_PICKER);
        return true;
    }

    private void init(Context context, AttributeSet attrs) {
        setOnPreferenceClickListener(this);
        var typedArray = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.ContactPickerPreference,
                0, 0
        );
        summaryOff = typedArray.getText(R.styleable.ContactPickerPreference_summaryOff);
        summaryOn = typedArray.getText(R.styleable.ContactPickerPreference_summaryOn);
        maxSelection = typedArray.getInt(R.styleable.ContactPickerPreference_maxSelection, -1);
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String namesString = prefs.getString(getKey(), "");
        if (namesString.length() > 2) {
            mContacts = Arrays.stream(namesString.substring(1, namesString.length() - 1).split(", ")).map(item -> item.trim()).collect(Collectors.toCollection(ArrayList::new));
        }
        if (mContacts != null && !mContacts.isEmpty()) {
            setSummary(String.format(String.valueOf(summaryOn), mContacts.size()));
        } else {
            setSummary(String.valueOf(summaryOff));
        }

        if (getKey() != null) {
            activePreferences.put(getKey(), new java.lang.ref.WeakReference<>(this));
        }
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CONTACT_PICKER && resultCode == Activity.RESULT_OK) {
            mContacts = data.getStringArrayListExtra("contacts");
            var prefs = getSharedPreferences() != null
                    ? getSharedPreferences()
                    : PreferenceManager.getDefaultSharedPreferences(getContext());
            prefs.edit().putString(getKey(), String.valueOf(mContacts)).apply();
            if (mContacts != null && !mContacts.isEmpty()) {
                setSummary(String.format(String.valueOf(summaryOn), mContacts.size()));
            } else {
                setSummary(String.valueOf(summaryOff));
            }
        }
    }
}
