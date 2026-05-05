package com.waenhancer.xposed.utils;

import android.content.res.Resources;
import android.content.res.XModuleResources;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Manages bidirectional resource ID mapping between the module and host process.
 * Now supports ON-DEMAND mapping for maximum performance.
 */
public class XResManager {
    public static XModuleResources moduleResources;
    public static Resources hostResources;

    public static final Map<Integer, Integer> moduleToHostIdMap = new ConcurrentHashMap<>();
    public static final Map<Integer, Integer> hostToModuleIdMap = new ConcurrentHashMap<>();
    public static final java.util.Set<Integer> validModuleIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /**
     * Translates a module resource ID (0x7f...) to a host process resource ID (0x80...).
     * Performs on-demand mapping if the ID hasn't been mapped yet.
     */
    public static int getHostId(int moduleId) {
        if (moduleId == 0 || (moduleId & 0xFF000000) != 0x7F000000) {
            return moduleId;
        }

        // Fast check: is it one of our resources?
        if (!validModuleIds.isEmpty() && !validModuleIds.contains(moduleId)) {
            return moduleId;
        }

        Integer hostId = moduleToHostIdMap.get(moduleId);
        if (hostId != null) {
            return hostId;
        }

        // On-demand mapping
        if (hostResources != null && moduleResources != null) {
            try {
                if (!(hostResources instanceof android.content.res.XResources)) {
                    // XposedBridge.log("[WAE] XResManager: hostResources is not XResources yet!");
                    return moduleId;
                }
                // Use reflection since hostResources is android.content.res.Resources at compile time
                // but usually android.content.res.XResources at runtime in LSPosed.
                Integer newHostId = (Integer) XposedHelpers.callMethod(hostResources, "addResource", moduleResources, moduleId);
                if (newHostId != null && newHostId != 0) {
                    moduleToHostIdMap.put(moduleId, newHostId);
                    hostToModuleIdMap.put(newHostId, moduleId);
                    return newHostId;
                }
            } catch (Throwable t) {
                XposedBridge.log("[WAE] XResManager: addResource failed: " + t.getMessage());
            }
        }

        return moduleId;
    }

    public static int getModuleId(int hostId) {
        Integer moduleId = hostToModuleIdMap.get(hostId);
        return moduleId != null ? moduleId : hostId;
    }
}
