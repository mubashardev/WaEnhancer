package com.waenhancer.services;

public class GhostModeTileService extends BaseTileService {
    @Override
    protected String getPreferenceKey() {
        return "ghostmode_actual";
    }

    @Override
    protected boolean getDefaultValue() {
        return false;
    }
}
