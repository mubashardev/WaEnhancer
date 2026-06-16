package com.waenhancer.api.plugin;

import com.waenhancer.api.services.IHookService;
import com.waenhancer.api.services.IObfuscationService;
import com.waenhancer.api.services.IStateService;
import com.waenhancer.api.services.IWhatsAppContextService;

public interface IPluginContext {
    ClassLoader getHostClassLoader();
    android.content.Context getModuleContext();
    IWhatsAppContextService getWhatsAppContextService();
    IStateService getStateService();
    IObfuscationService getObfuscationService();
    IHookService getHookService();
}
