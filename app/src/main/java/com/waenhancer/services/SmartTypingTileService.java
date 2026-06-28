package com.waenhancer.services;

public class SmartTypingTileService extends BaseTileService {
    @Override
    protected String getPreferenceKey() {
        return "always_typing_global";
    }

    @Override
    protected boolean getDefaultValue() {
        return false;
    }
}
