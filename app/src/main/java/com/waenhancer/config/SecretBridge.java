package com.waenhancer.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import com.waenhancer.BuildConfig;

/**
 * Serves a user secret to a hook without that secret living in the world-readable file.
 *
 * <p>Three settings are user secrets that a hook nonetheless needs: the Groq and AssemblyAI API
 * keys used for voice transcription, and the imported keybox used by the bootloader spoofer.
 * They used to sit in the default preference file, which is deliberately world-readable so
 * {@code XSharedPreferences} can serve the hooked process — meaning any application on the
 * device could read them, and the exported provider handed them out on request.</p>
 *
 * <p>They now live in the private store and are fetched on demand through a provider call that
 * validates the calling UID. The value is held only for the duration of the operation that
 * needs it and is never written back to the public file.</p>
 *
 * <p>The legacy fallback reads the public store directly. It exists because the migration is
 * additive: until {@code PreferenceMigration.removeMigratedSecrets} has run, the value is still
 * in the old place, and a hook must keep working throughout. Once removal has happened the
 * fallback finds nothing and the provider path is the only one left.</p>
 */
public final class SecretBridge {

    private static final String METHOD_GET_SECRET = "get_secret";

    private SecretBridge() {
    }

    /**
     * @param context     context of the calling process
     * @param preferences the store visible to this process, used only for the legacy fallback
     * @param key         a key the schema classifies as a secret
     * @return the secret, or the supplied fallback when it is unset or unreachable
     */
    public static String get(Context context, SharedPreferences preferences, String key,
                             String fallback) {
        if (key == null || !PreferenceSchema.isSecret(key)) return fallback;

        String viaProvider = fromProvider(context, key);
        if (viaProvider != null && !viaProvider.isEmpty()) return viaProvider;

        // Migration is additive, so during the transition the value may still be in the store
        // this process can see. After removeMigratedSecrets this yields nothing.
        String legacy = SafePrefs.getString(preferences, key, null);
        return legacy != null && !legacy.isEmpty() ? legacy : fallback;
    }

    private static String fromProvider(Context context, String key) {
        if (context == null) return null;
        try {
            Bundle extras = new Bundle();
            extras.putString("key", key);
            Bundle result = context.getContentResolver().call(
                    Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider"),
                    METHOD_GET_SECRET, null, extras);
            return result == null ? null : result.getString("value");
        } catch (RuntimeException ignored) {
            // An unreachable module must not take down the calling feature.
            return null;
        }
    }
}
