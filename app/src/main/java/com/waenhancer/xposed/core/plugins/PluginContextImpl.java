package com.waenhancer.xposed.core.plugins;

import android.content.Context;
import com.waenhancer.api.plugin.IPluginContext;
import com.waenhancer.api.services.*;
import com.waenhancer.xposed.core.plugins.impl.*;

public class PluginContextImpl implements IPluginContext {

    private final ClassLoader hostClassLoader;
    private final Context moduleContext;
    private final IWhatsAppContextService whatsAppContextService;
    private final IStateService stateService;
    private final IObfuscationService obfuscationService;
    private final IHookService hookService;

    public PluginContextImpl(ClassLoader hostClassLoader, Context moduleContext, android.content.SharedPreferences pref) {
        this.hostClassLoader = hostClassLoader;
        this.moduleContext = moduleContext;
        this.whatsAppContextService = new WhatsAppContextServiceImpl(pref);
        this.stateService = new StateServiceImpl();
        this.obfuscationService = new ObfuscationServiceImpl(hostClassLoader);
        this.hookService = new HookServiceImpl();
    }

    @Override
    public ClassLoader getHostClassLoader() {
        return hostClassLoader;
    }

    @Override
    public Context getModuleContext() {
        return moduleContext;
    }

    @Override
    public IWhatsAppContextService getWhatsAppContextService() {
        return whatsAppContextService;
    }

    @Override
    public IStateService getStateService() {
        return stateService;
    }

    @Override
    public IObfuscationService getObfuscationService() {
        return obfuscationService;
    }

    @Override
    public IHookService getHookService() {
        return hookService;
    }
}
