package com.waenhancer.services;

public class ProximitySensorSwitchTileService extends BaseTileService {
    @Override
    protected String getPreferenceKey() {
        return "disable_sensor_proximity";
    }

    @Override
    protected boolean getDefaultValue() {
        return false;
    }
}
