package com.waenhancer.services;

public class StealthStatusViewingTileService extends BaseTileService {
    @Override
    protected String getPreferenceKey() {
        return "hidestatusview";
    }

    @Override
    protected boolean getDefaultValue() {
        return false;
    }
}
