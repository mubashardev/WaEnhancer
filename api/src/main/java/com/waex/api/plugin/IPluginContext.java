package com.waex.api.plugin;

import com.waex.api.services.IHookService;
import com.waex.api.services.IObfuscationService;
import com.waex.api.services.IStateService;
import com.waex.api.services.ICoreBridge;
import com.waex.api.services.IStatusMenuListener;
import android.content.Context;

public interface IPluginContext {
    ClassLoader getHostClassLoader();
    Context getModuleContext();
    ICoreBridge getCoreBridge();
    IStateService getStateService();
    IObfuscationService getObfuscationService();
    IHookService getHookService();
    void registerStatusMenuListener(IStatusMenuListener listener);
}