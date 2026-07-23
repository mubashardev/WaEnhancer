package com.waenhancer.services;

public class HideDeliveredTileService extends BaseTileService {
    @Override
    protected String getPreferenceKey() {
        return "hidereceipt";
    }

    @Override
    protected boolean getDefaultValue() {
        return false;
    }
}
