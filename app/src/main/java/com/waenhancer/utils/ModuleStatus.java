package com.waenhancer.utils;

import androidx.annotation.Keep;

/**
 * Gold Standard "Self-Hook" sentinel class for Xposed module status detection.
 * Following the provided guidelines for reliable module status verification.
 */
@Keep
public class ModuleStatus {
    
    /**
     * This method is hooked by the WppXposed module to return true.
     * If it returns false, the module is either disabled in LSPosed 
     * or the WaEnhancerX app is not in the module's scope.
     * 
     * @return true if the module is active and hooking the current process.
     */
    public static boolean isModuleActive() {
        return false;
    }
}
