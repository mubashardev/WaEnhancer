package com.waenhancer.config;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

/**
 * The two preference files the module keeps, and the rule for which one a key belongs to.
 *
 * <p><strong>public_config</strong> is the default preference file. It is deliberately the file
 * that already exists: {@code WppXposed} hooks {@code getDefaultSharedPreferencesMode} to make
 * it world-readable so {@code XSharedPreferences} can read it from the WhatsApp process, and
 * every hook reads it today. Relocating the public keys to a newly named file would change, in
 * a single step, the one path that currently works across the process boundary — the highest
 * risk of configuration loss in this project. The plan puts preservation of data and
 * compatibility above every other goal, so the existing file keeps its role and is simply
 * named for what it is.</p>
 *
 * <p><strong>private_config</strong> is a new {@code MODE_PRIVATE} file, reachable only by the
 * module's own UID. It holds everything the hooked process must not be able to read from disk:
 * user secrets, local caches and internal runtime state. A secret that a hook genuinely needs
 * is served on request through a UID-validated provider call rather than by being placed in
 * the world-readable file.</p>
 */
public final class PreferenceStores {

    /** Name of the private store. Not the default preference file. */
    public static final String PRIVATE_NAME = "private_config";

    private PreferenceStores() {
    }

    /** The world-readable store the hooked WhatsApp process reads. */
    public static SharedPreferences publicStore(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /** The module-private store. Never world-readable, never read across the process boundary. */
    public static SharedPreferences privateStore(Context context) {
        return context.getSharedPreferences(PRIVATE_NAME, Context.MODE_PRIVATE);
    }

    /** Which store a key belongs to according to the schema; public when the key is unknown. */
    public static SharedPreferences storeFor(Context context, String key) {
        PreferenceSchema.Entry entry = PreferenceSchema.entry(key);
        if (entry != null && entry.store == PreferenceSchema.Store.PRIVATE) {
            return privateStore(context);
        }
        return publicStore(context);
    }

    /**
     * Reads a key from the store the schema assigns it, falling back to the other store while
     * the migration keeps values in both places.
     *
     * <p>The fallback is what makes the migration reversible: values are copied into the
     * private store without being removed from the public one, so a downgrade to a build that
     * only knows the public store still finds everything it needs.</p>
     */
    public static Object read(Context context, String key) {
        PreferenceSchema.Entry entry = PreferenceSchema.entry(key);
        boolean privateFirst = entry != null && entry.store == PreferenceSchema.Store.PRIVATE;
        SharedPreferences first = privateFirst ? privateStore(context) : publicStore(context);
        SharedPreferences second = privateFirst ? publicStore(context) : privateStore(context);
        Object value = first.getAll().get(key);
        return value != null ? value : second.getAll().get(key);
    }
}
