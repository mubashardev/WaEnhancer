package com.waenhancer.services;

public class AlwaysOnlineTileService extends BaseTileService {
    @Override
    protected String getPreferenceKey() {
        return "always_online";
    }

    @Override
    protected boolean getDefaultValue() {
        return false;
    }
}
