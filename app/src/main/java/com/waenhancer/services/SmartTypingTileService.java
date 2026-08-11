package com.waenhancer.services;

public class SmartTypingTileService extends BaseTileService {
    @Override
    protected String getPreferenceKey() {
        return "waex_sim_enabled";
    }

    @Override
    protected boolean getDefaultValue() {
        return false;
    }
}
