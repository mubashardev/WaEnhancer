package com.waenhancer.xposed.core.plugins;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class IsolatedParentClassLoader extends ClassLoader {
    private final ClassLoader hostClassLoader;
    private final ClassLoader moduleClassLoader;

    public IsolatedParentClassLoader(ClassLoader hostClassLoader) {
        super(ClassLoader.getSystemClassLoader());
        this.hostClassLoader = hostClassLoader;
        this.moduleClassLoader = IsolatedParentClassLoader.class.getClassLoader();
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Enforce boundary rules:
        // 1. Allow com.waex.api.* (API layer) and com.waenhancer.* to be resolved from moduleClassLoader
        if (name.startsWith("com.waex.api.") || name.startsWith("com.waenhancer.")) {
            if (moduleClassLoader != null) {
                return moduleClassLoader.loadClass(name);
            }
        }

        // 2. Allow de.robv.android.xposed.* (Xposed Framework) to be resolved robustly
        if (name.startsWith("de.robv.android.xposed.")) {
            if (name.equals("de.robv.android.xposed.XposedBridge")) {
                return XposedBridge.class;
            }
            if (name.equals("de.robv.android.xposed.XC_MethodHook")) {
                return XC_MethodHook.class;
            }
            if (name.equals("de.robv.android.xposed.XposedHelpers")) {
                return XposedHelpers.class;
            }
            if (name.equals("de.robv.android.xposed.XC_MethodHook$MethodHookParam")) {
                return XC_MethodHook.MethodHookParam.class;
            }
            if (name.equals("de.robv.android.xposed.XC_MethodHook$Unhook")) {
                return XC_MethodHook.Unhook.class;
            }
            if (name.equals("de.robv.android.xposed.XSharedPreferences")) {
                return XSharedPreferences.class;
            }

            /* Log removed */
            try {
                Class<?> clazz = Class.forName(name, false, null);
                return clazz;
            } catch (Throwable ignored) {}
            if (moduleClassLoader != null) {
                try {
                    return moduleClassLoader.loadClass(name);
                } catch (Throwable ignored) {}
                try {
                    ClassLoader parent = moduleClassLoader.getParent();
                    if (parent != null) {
                        return parent.loadClass(name);
                    }
                } catch (Throwable ignored) {}
            }
            try {
                ClassLoader xposedLoader = XposedBridge.class.getClassLoader();
                if (xposedLoader != null) {
                    return xposedLoader.loadClass(name);
                }
            } catch (Throwable ignored) {}
            try {
                ClassLoader threadLoader = Thread.currentThread().getContextClassLoader();
                if (threadLoader != null) {
                    return threadLoader.loadClass(name);
                }
            } catch (Throwable ignored) {}
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {}
            /* Log removed */
        }

        // 3. Block com.waex.helper.* from parent delegation
        if (name.startsWith("com.waex.helper.")) {
            throw new ClassNotFoundException("Blocked delegation of plugin class to parent: " + name);
        }
        
        // 4. Reject any other host developer classes (com.waex.host.*)
        if (name.startsWith("com.waex.host.")) {
            throw new ClassNotFoundException("Blocked access to host class: " + name);
        }

        // 5. Delegate to the platform boot classloader for java.*, android.*, etc.
        try {
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException e) {
            // Fallback for other system/support libraries (like android/androidx platform classes loaded by the host)
            // strictly excluding any host developer packages
            if (!name.startsWith("com.waenhancer.") && !name.startsWith("com.waex.host.")) {
                return hostClassLoader.loadClass(name);
            }
            throw e;
        }
    }
}