package com.waenhancer.api.plugin;

public interface IPlugin {
    void onLoad(IPluginContext context);
    void onUnload();
    String getName();
    String getVersion();
}
