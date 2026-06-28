package com.waenhancer.services;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.content.SharedPreferences;
import android.content.Intent;
import androidx.preference.PreferenceManager;
import android.net.Uri;
import com.waenhancer.BuildConfig;
import com.waenhancer.xposed.utils.LicenseManager;

public abstract class BaseTileService extends TileService {

    protected abstract String getPreferenceKey();
    protected abstract boolean getDefaultValue();

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String key = getPreferenceKey();
        
        if (isCustomToggle()) {
            performCustomToggle(prefs, key);
        } else {
            boolean current = prefs.getBoolean(key, getDefaultValue());
            prefs.edit().putBoolean(key, !current).apply();
        }
        
        syncAndRestart();
        updateTileState();
    }

    protected boolean isCustomToggle() {
        return false;
    }

    protected void performCustomToggle(SharedPreferences prefs, String key) {
        // Override for custom toggle behavior (e.g. list preferences)
    }

    protected boolean isTileActive(SharedPreferences prefs) {
        return prefs.getBoolean(getPreferenceKey(), getDefaultValue());
    }

    protected void syncAndRestart() {
        try {
            getContentResolver().notifyChange(
                Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider/preferences"), 
                null
            );
        } catch (Exception ignored) {}
        
        try {
            LicenseManager.makePrefsWorldReadable(this);
        } catch (Exception ignored) {}

        try {
            Intent intentWpp = new Intent(BuildConfig.APPLICATION_ID + ".WHATSAPP.RESTART");
            intentWpp.putExtra("PKG", "com.whatsapp");
            sendBroadcast(intentWpp);
        } catch (Exception ignored) {}

        try {
            Intent intentBusiness = new Intent(BuildConfig.APPLICATION_ID + ".WHATSAPP.RESTART");
            intentBusiness.putExtra("PKG", "com.whatsapp.w4b");
            sendBroadcast(intentBusiness);
        } catch (Exception ignored) {}
    }

    protected void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean active = isTileActive(prefs);
        
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
