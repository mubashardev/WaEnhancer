package com.waenhancer.xposed.features.customization;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.SharedPreferences;

public class SeparateGroup extends Feature {
    public static final int CHATS = 200;
    public static final int STATUS = 300;
    public static final int GROUPS = 500;
    public static final ArrayList<Integer> tabs = new ArrayList<>();
    public static final HashMap<Integer, Object> tabInstances = new HashMap<>();

    public SeparateGroup(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() {
        // Intentionally disabled.
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Separate Group";
    }
}
