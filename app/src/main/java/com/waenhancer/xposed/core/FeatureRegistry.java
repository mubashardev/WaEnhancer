package com.waenhancer.xposed.core;

import android.app.Activity;
import android.content.SharedPreferences;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XposedBridge;

/**
 * FeatureRegistry - Manages lazy loading of features
 *
 * Features can be registered with triggers that cause them to load on-demand
 * rather than at WhatsApp startup. This significantly improves startup performance
 * and reduces memory usage for features that users rarely use.
 *
 * Usage:
 * 1. Mark feature as lazy in FeatureLoader.plugins() using registerLazyFeature()
 * 2. Feature will only load when its trigger is activated
 * 3. Triggers can be: activity lifecycle, method call, or manual activation
 */
public class FeatureRegistry {

    private static final String TAG = "FeatureRegistry";

    // Registry of lazy features - maps feature class to its registration
    private static final Map<String, LazyFeatureRegistration> lazyFeatures = new ConcurrentHashMap<>();

    // Track which features have been loaded
    private static final Set<String> loadedFeatures = ConcurrentHashMap.newKeySet();

    // Lock for thread-safe feature loading
    private static final Object loadLock = new Object();

    /**
     * Represents a lazy feature that can be loaded on-demand
     */
    public static class LazyFeatureRegistration {
        public final String featureName;
        public final Class<? extends Feature> featureClass;
        public final TriggerType triggerType;
        public final String triggerAction; // activity name, method name, etc.
        public final boolean showInSettings;

        public LazyFeatureRegistration(String featureName, Class<? extends Feature> featureClass,
                                        TriggerType triggerType, String triggerAction, boolean showInSettings) {
            this.featureName = featureName;
            this.featureClass = featureClass;
            this.triggerType = triggerType;
            this.triggerAction = triggerAction;
            this.showInSettings = showInSettings;
        }
    }

    /**
     * Types of triggers that can activate lazy features
     */
    public enum TriggerType {
        ACTIVITY_RESUMED,      // When specific activity becomes visible
        ACTIVITY_CREATED,      // When specific activity is created
        METHOD_CALLED,         // When specific method is invoked
        MANUAL,                // Manual activation via settings
        STATUS_VIEW,           // When user views status
        MESSAGE_DELETED,       // When a message is deleted
        CALL_STARTED,          // When a call starts
        CONVERSATION_OPENED   // When conversation is opened
    }

    /**
     * Register a feature as lazy - it will only load when triggered
     *
     * @param featureName Display name for the feature
     * @param featureClass The feature class
     * @param triggerType What triggers the feature to load
     * @param triggerAction The specific action that triggers (e.g., activity name)
     * @param showInSettings Whether to show in lazy features list in settings
     */
    public static void registerLazyFeature(String featureName, Class<? extends Feature> featureClass,
                                            TriggerType triggerType, String triggerAction, boolean showInSettings) {
        lazyFeatures.put(featureClass.getSimpleName(),
                new LazyFeatureRegistration(featureName, featureClass, triggerType, triggerAction, showInSettings));
        XposedBridge.log("[FeatureRegistry] Registered lazy feature: " + featureName + " (trigger: " + triggerType + ")");
    }

    /**
     * Register a simple lazy feature with just a name and class
     */
    public static void registerLazyFeature(String featureName, Class<? extends Feature> featureClass) {
        registerLazyFeature(featureName, featureClass, TriggerType.MANUAL, null, true);
    }

    /**
     * Check if a feature is registered as lazy
     */
    public static boolean isLazyFeature(String featureName) {
        return lazyFeatures.containsKey(featureName);
    }

    /**
     * Check if a feature has already been loaded
     */
    public static boolean isLoaded(String featureName) {
        return loadedFeatures.contains(featureName);
    }

    /**
     * Activate a lazy feature based on trigger
     *
     * @param triggerType The type of trigger
     * @param triggerAction The specific action (e.g., activity name)
     * @param loader ClassLoader for feature instantiation
     * @param pref SharedPreferences for the feature
     * @return true if feature was loaded, false if no matching lazy feature found
     */
    public static boolean activateFeature(TriggerType triggerType, String triggerAction,
                                           ClassLoader loader, SharedPreferences pref) {
        // Find all lazy features that match this trigger
        for (Map.Entry<String, LazyFeatureRegistration> entry : lazyFeatures.entrySet()) {
            LazyFeatureRegistration reg = entry.getValue();

            // Skip if already loaded
            if (loadedFeatures.contains(entry.getKey())) {
                continue;
            }

            // Check if this trigger matches
            if (reg.triggerType == triggerType &&
                (triggerAction == null || triggerAction.equals(reg.triggerAction) || reg.triggerAction == null)) {

                loadFeature(reg, loader, pref);
                return true;
            }
        }
        return false;
    }

    /**
     * Manually activate a lazy feature by name
     */
    public static boolean activateFeatureByName(String featureName, ClassLoader loader, SharedPreferences pref) {
        LazyFeatureRegistration reg = lazyFeatures.get(featureName);
        if (reg != null && !loadedFeatures.contains(featureName)) {
            loadFeature(reg, loader, pref);
            return true;
        }
        return false;
    }

    /**
     * Load a lazy feature
     */
    private static void loadFeature(LazyFeatureRegistration reg, ClassLoader loader, SharedPreferences pref) {
        synchronized (loadLock) {
            String key = reg.featureClass.getSimpleName();
            if (loadedFeatures.contains(key)) {
                return;
            }

            long startTime = System.currentTimeMillis();
            try {
                Constructor<? extends Feature> constructor = reg.featureClass.getConstructor(
                        ClassLoader.class, SharedPreferences.class);
                Feature feature = constructor.newInstance(loader, pref);
                feature.doHook();

                loadedFeatures.add(key);
                long loadTime = System.currentTimeMillis() - startTime;
                XposedBridge.log("[FeatureRegistry] Lazy loaded: " + reg.featureName + " in " + loadTime + "ms");

            } catch (Throwable e) {
                XposedBridge.log("[FeatureRegistry] Failed to lazy load " + reg.featureName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Get list of all lazy features for settings display
     */
    public static Map<String, LazyFeatureRegistration> getLazyFeatures() {
        return new HashMap<>(lazyFeatures);
    }

    /**
     * Get count of loaded features
     */
    public static int getLoadedCount() {
        return loadedFeatures.size();
    }

    /**
     * Get count of registered lazy features
     */
    public static int getRegisteredCount() {
        return lazyFeatures.size();
    }

    /**
     * Clear loaded status (useful for testing or reset)
     */
    public static void reset() {
        loadedFeatures.clear();
    }
}