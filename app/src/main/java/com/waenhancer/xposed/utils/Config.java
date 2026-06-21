package com.waenhancer.xposed.utils;

/**
 * Centralized Configuration Management for WaEnhancerX.
 * Contains all base server URLs and relative API endpoints to avoid hardcoding.
 */
public final class Config {

    /**
     * Base URL for the licensing and update server (Cloudflare Worker).
     * Fetched dynamically from C++ native layer to prevent JADX/decompiler string folding.
     */
    public static String getBaseUrl() {
        try {
            Class<?> secClazz = Class.forName("com.waex.pro.utils.SecurityNative");
            return (String) secClazz.getMethod("getBaseUrl").invoke(null);
        } catch (Throwable t) {
            // Secure fallback in case native library is not yet loaded in context
            return new String(new char[]{
                'h','t','t','p','s',':','/','/','a','p','i','.','w','a','e','x','.','w','o','r','k','e','r','s','.','d','e','v'
            });
        }
    }

    /**
     * API endpoint to link a device to a license key for the first time.
     */
    public static final String LINK_ENDPOINT = getBaseUrl() + "/api/v1/link";

    /**
     * API endpoint to re-verify license keys on every app start (silent check).
     */
    public static final String VERIFY_ENDPOINT = getBaseUrl() + "/api/v1/verify";

    /**
     * API endpoint to unlink a device from a license key directly from the app.
     */
    public static final String UNLINK_ENDPOINT = getBaseUrl() + "/api/v1/unlink";

    /**
     * API endpoint to check for application updates.
     */
    public static final String UPDATE_CHECK_ENDPOINT = getBaseUrl() + "/api/v1/update";

    // Private constructor to prevent instantiation
    private Config() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
