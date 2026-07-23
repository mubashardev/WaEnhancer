package com.waex.api.plugin;

public interface IPlugin {
    void load();
    void attachContext(IPluginContext context);
    void init();
    void execute();
    void onUnload();
    String getName();
    String getVersion();
    void registerCapabilities(ICapabilityRegistry registry);
}
