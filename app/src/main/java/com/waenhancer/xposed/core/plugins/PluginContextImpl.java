package com.waenhancer.xposed.core.plugins;

import android.content.Context;
import com.waex.api.plugin.IPluginContext;
import com.waex.api.services.*;
import com.waenhancer.xposed.core.plugins.impl.*;

public class PluginContextImpl implements IPluginContext {

    private final ClassLoader hostClassLoader;
    private final Context moduleContext;
    private final ICoreBridge coreBridge;
    private final IStateService stateService;
    private final IObfuscationService obfuscationService;
    private final IHookService hookService;

    private static final java.util.List<IStatusMenuListener> statusMenuListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static java.util.List<IStatusMenuListener> getStatusMenuListeners() {
        return statusMenuListeners;
    }

    public PluginContextImpl(ClassLoader hostClassLoader, Context moduleContext, android.content.SharedPreferences pref) {
        this.hostClassLoader = hostClassLoader;
        this.moduleContext = moduleContext;
        this.coreBridge = new CoreBridgeImpl(pref);
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
    public ICoreBridge getCoreBridge() {
        return coreBridge;
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

    @Override
    public void registerStatusMenuListener(IStatusMenuListener listener) {
        if (listener != null) {
            statusMenuListeners.add(listener);
        }
    }
}
