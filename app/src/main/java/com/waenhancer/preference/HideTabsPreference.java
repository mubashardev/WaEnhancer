package com.waenhancer.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashSet;
import java.util.Set;
import androidx.appcompat.app.AlertDialog;

public class HideTabsPreference extends MultiSelectListPreference {

    public HideTabsPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public HideTabsPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public HideTabsPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        Context context = getContext();
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context);
        }

        final boolean disableMetaAI = prefs.getBoolean("metaai", false);
        final Set<String> currentValues = new HashSet<>(getValues());

        // Force Meta AI (1000) to be checked if Meta AI functions are disabled
        if (disableMetaAI) {
            currentValues.add("1000");
            persistStringSet(currentValues);
        }

        final CharSequence[] originalEntries = getEntries();
        final CharSequence[] originalValues = getEntryValues();

        if (originalEntries == null || originalValues == null) {
            super.onClick();
            return;
        }

        final CharSequence[] displayEntries = new CharSequence[originalEntries.length];
        final boolean[] checkedItems = new boolean[originalEntries.length];
        int tempMetaAiIndex = -1;

        for (int i = 0; i < originalEntries.length; i++) {
            String value = originalValues[i].toString();
            checkedItems[i] = currentValues.contains(value);

            if ("1000".equals(value)) {
                tempMetaAiIndex = i;
                if (disableMetaAI) {
                    displayEntries[i] = originalEntries[i] + " (Disabled by user)";
                    checkedItems[i] = true;
                } else {
                    displayEntries[i] = originalEntries[i];
                }
            } else {
                displayEntries[i] = originalEntries[i];
            }
        }

        final int metaAiIndex = tempMetaAiIndex;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(getTitle());
        builder.setMultiChoiceItems(displayEntries, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                if (disableMetaAI && which == metaAiIndex) {
                    // Prevent unchecking by resetting the checked state in the Dialog's ListView
                    AlertDialog alertDialog = (AlertDialog) dialog;
                    alertDialog.getListView().setItemChecked(metaAiIndex, true);
                    checkedItems[metaAiIndex] = true;
                    return;
                }
                checkedItems[which] = isChecked;
            }
        });

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Set<String> newValues = new HashSet<>();
                for (int i = 0; i < originalValues.length; i++) {
                    if (checkedItems[i]) {
                        newValues.add(originalValues[i].toString());
                    }
                }
                if (disableMetaAI) {
                    newValues.add("1000");
                }
                if (callChangeListener(newValues)) {
                    setValues(newValues);
                }
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }
}