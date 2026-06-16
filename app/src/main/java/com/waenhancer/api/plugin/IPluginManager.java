package com.waenhancer.api.plugin;

import java.util.Collection;

public interface IPluginManager {
    void registerPlugin(IPlugin plugin);
    void unregisterPlugin(String name);
    Collection<IPlugin> getLoadedPlugins();
    IPlugin getPlugin(String name);
}
