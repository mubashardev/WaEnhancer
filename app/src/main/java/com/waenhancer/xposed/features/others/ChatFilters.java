package com.waenhancer.xposed.features.others;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;

import android.content.SharedPreferences;

public class ChatFilters extends Feature {
    public ChatFilters(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() {
        // Intentionally disabled.
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Chat Filters";
    }
}
