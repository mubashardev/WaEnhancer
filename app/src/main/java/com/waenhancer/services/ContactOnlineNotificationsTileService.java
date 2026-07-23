package com.waenhancer.services;

public class ContactOnlineNotificationsTileService extends BaseTileService {
    @Override
    protected String getPreferenceKey() {
        return "show_toast_on_contact_online";
    }

    @Override
    protected boolean getDefaultValue() {
        return false;
    }
}
