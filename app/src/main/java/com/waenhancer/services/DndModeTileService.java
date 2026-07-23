package com.waenhancer.services;

public class DndModeTileService extends BaseTileService {
    @Override
    protected String getPreferenceKey() {
        return "dndmode_actual";
    }

    @Override
    protected boolean getDefaultValue() {
        return false;
    }
}
