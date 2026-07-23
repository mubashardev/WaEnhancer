package com.waenhancer.services;

import android.content.SharedPreferences;

public class BlockCallsTileService extends BaseTileService {
    @Override
    protected String getPreferenceKey() {
        return "call_privacy";
    }

    @Override
    protected boolean getDefaultValue() {
        return false;
    }

    @Override
    protected boolean isCustomToggle() {
        return true;
    }

    @Override
    protected void performCustomToggle(SharedPreferences prefs, String key) {
        String current = prefs.getString(key, "0");
        String newValue = "0".equals(current) ? "1" : "0";
        prefs.edit().putString(key, newValue).apply();
    }

    @Override
    protected boolean isTileActive(SharedPreferences prefs) {
        String current = prefs.getString(getPreferenceKey(), "0");
        return !"0".equals(current);
    }
}
