package com.waenhancer.xposed.features.customization;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;

import android.content.SharedPreferences;

public class FilterGroups extends Feature {
    public FilterGroups(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() {
        // Intentionally disabled.
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Filter Groups";
    }
}
