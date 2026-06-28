package com.waenhancer.services;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.content.SharedPreferences;
import android.content.Intent;
import androidx.preference.PreferenceManager;
import android.net.Uri;
import com.waenhancer.BuildConfig;
import com.waenhancer.xposed.utils.LicenseManager;

public class GhostModeTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean current = prefs.getBoolean("ghostmode_actual", false);
        boolean newValue = !current;
        prefs.edit().putBoolean("ghostmode_actual", newValue).apply();
        
        // Notify ContentProvider of changes so that hooks receive the update immediately
        try {
            getContentResolver().notifyChange(
                Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider/preferences"), 
                null
            );
        } catch (Exception ignored) {}
        
        // Ensure world readability so the Xposed hooks can read the preferences from disk if needed
        try {
            LicenseManager.makePrefsWorldReadable(this);
        } catch (Exception ignored) {}

        // Send manual restart broadcasts to WhatsApp packages
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

        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean ghostmode = prefs.getBoolean("ghostmode_actual", false);
        
        tile.setState(ghostmode ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
