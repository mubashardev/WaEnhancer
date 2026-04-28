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

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        if (!(getContext() instanceof Activity activity)) {
            return true;
        }
        Intent intent = new Intent(getContext(), ContactPickerActivity.class);
        intent.putExtra(ContactPickerActivity.EXTRA_KEY, getKey());
        intent.putStringArrayListExtra(ContactPickerActivity.EXTRA_SELECTED_CONTACTS,
                mContacts != null ? new ArrayList<>(mContacts) : new ArrayList<>());
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
