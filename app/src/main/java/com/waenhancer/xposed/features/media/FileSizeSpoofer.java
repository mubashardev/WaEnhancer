package com.waenhancer.xposed.features.media;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.waenhancer.xposed.core.Feature;

/**
 * Stub implementation of FileSizeSpoofer in the free/main module.
 * All actual logic has been migrated securely to the pro submodule class com.waenhancer.pro.FileSizeSpooferPro.
 */
public class FileSizeSpoofer extends Feature {

    public FileSizeSpoofer(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        // Stub: all logic migrated to pro submodule.
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "File Size Spoofer";
    }
}
