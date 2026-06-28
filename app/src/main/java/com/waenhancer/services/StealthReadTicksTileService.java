package com.waenhancer.services;

public class StealthReadTicksTileService extends BaseTileService {
    @Override
    protected String getPreferenceKey() {
        return "hideread";
    }

    @Override
    protected boolean getDefaultValue() {
        return false;
    }
}
