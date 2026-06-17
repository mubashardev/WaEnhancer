package com.waenhancer.xposed.core.plugins;

public class IsolatedParentClassLoader extends ClassLoader {
    private final ClassLoader hostClassLoader;

    public IsolatedParentClassLoader(ClassLoader hostClassLoader) {
        super(ClassLoader.getSystemClassLoader());
        this.hostClassLoader = hostClassLoader;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Enforce boundary rules:
        // 1. Allow com.waex.api.* (API layer) to be resolved from hostClassLoader
        if (name.startsWith("com.waex.api.")) {
            return hostClassLoader.loadClass(name);
        }

        // 2. Block com.waex.pro.* from parent delegation
        if (name.startsWith("com.waex.pro.")) {
            throw new ClassNotFoundException("Blocked delegation of plugin class to parent: " + name);
        }
        
        // 3. Reject any other host developer classes (com.waenhancer.*, com.waex.host.*)
        if (name.startsWith("com.waenhancer.") || name.startsWith("com.waex.host.")) {
            throw new ClassNotFoundException("Blocked access to host class: " + name);
        }

        // 4. Delegate to the platform boot classloader for java.*, android.*, etc.
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
