package com.waex.api.plugin;

import com.waex.api.services.IHookService;
import com.waex.api.services.IObfuscationService;
import com.waex.api.services.IStateService;
import com.waex.api.services.ICoreBridge;
import com.waex.api.services.IStatusMenuListener;

public interface IPluginContext {
    ClassLoader getHostClassLoader();
    android.content.Context getModuleContext();
    ICoreBridge getCoreBridge();
    IStateService getStateService();
    IObfuscationService getObfuscationService();
    IHookService getHookService();
    void registerStatusMenuListener(IStatusMenuListener listener);
}
