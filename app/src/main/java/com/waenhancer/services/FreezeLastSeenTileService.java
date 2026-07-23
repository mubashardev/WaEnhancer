package com.waenhancer.services;

public class FreezeLastSeenTileService extends BaseTileService {
    @Override
    protected String getPreferenceKey() {
        return "freeze_last_seen_actual";
    }

    @Override
    protected boolean getDefaultValue() {
        return false;
    }
}
