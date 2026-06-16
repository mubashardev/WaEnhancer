package com.waenhancer.xposed.core.plugins.impl;

import com.waenhancer.api.services.IObfuscationService;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class ObfuscationServiceImpl implements IObfuscationService {

    private final ClassLoader hostClassLoader;

    public ObfuscationServiceImpl(ClassLoader hostClassLoader) {
        this.hostClassLoader = hostClassLoader;
    }

    @Override
    public Class<?> loadClass(String className) {
        try {
            return hostClassLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public Method loadMethod(String className, String methodName, Class<?>... parameterTypes) {
        try {
            Class<?> clazz = loadClass(className);
            if (clazz != null) {
                return clazz.getDeclaredMethod(methodName, parameterTypes);
            }
        } catch (NoSuchMethodException ignored) {}
        return null;
    }

    @Override
    public Field loadField(String className, String fieldName) {
        try {
            Class<?> clazz = loadClass(className);
            if (clazz != null) {
                return clazz.getDeclaredField(fieldName);
            }
        } catch (NoSuchFieldException ignored) {}
        return null;
    }

    @Override
    public Method findMethodByStringSignature(String signature) {
        return null;
    }

    @Override
    public Class<?> findClassByUniqueStrings(String... strings) {
        try {
            return Unobfuscator.findFirstClassUsingStrings(hostClassLoader, org.luckypray.dexkit.query.enums.StringMatchType.Contains, strings);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Object getDexKitInstance() {
        return Unobfuscator.getDexKit();
    }

    @Override
    public Method loadGhostModeMethod(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadGhostModeMethod(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Method loadPropsBooleanMethod(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadPropsBooleanMethod(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Class<?> loadVideoViewContainerClass(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadVideoViewContainerClass(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Class<?> loadImageVewContainerClass(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadImageVewContainerClass(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Class<?> loadConversationRowClass(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadConversationRowClass(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Class<?> loadFMessageClass(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadFMessageClass(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Class<?> loadAbstractMediaMessageClass(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadAbstractMediaMessageClass(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Method loadPageOnViewCreatedMethod(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadPageOnViewCreatedMethod(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Method loadPausePlaybackMethod(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadPausePlaybackMethod(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Method loadResumePlaybackMethod(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadResumePlaybackMethod(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Method loadStatusReplyMethod(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadStatusReplyMethod(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Method loadGetPageControllerMethod(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadGetPageControllerMethod(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Class<?> loadVideoPlayerClass(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadVideoPlayerClass(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Class<?> findFirstClassUsingName(ClassLoader classLoader, String matchType, String name) {
        try {
            org.luckypray.dexkit.query.enums.StringMatchType match = org.luckypray.dexkit.query.enums.StringMatchType.valueOf(matchType);
            return Unobfuscator.findFirstClassUsingName(classLoader, match, name);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Class<?> findFirstClassUsingStrings(ClassLoader classLoader, String[] strings) {
        try {
            return Unobfuscator.findFirstClassUsingStrings(classLoader, org.luckypray.dexkit.query.enums.StringMatchType.Contains, strings);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Object getDexKit(ClassLoader classLoader) {
        return Unobfuscator.getDexKit();
    }

    @Override
    public List<String> findCallers(ClassLoader classLoader, Method method) {
        try {
            return Unobfuscator.findCallers(method);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Method findVoiceTransitionMethod(ClassLoader classLoader, Class<?> fragmentClass, String recorderTypeName) {
        return Unobfuscator.findVoiceTransitionMethod(classLoader, fragmentClass, recorderTypeName);
    }

    @Override
    public Method findVoiceSendTriggerMethod(ClassLoader classLoader, Class<?> fragmentClass, String recorderTypeName) {
        return Unobfuscator.findVoiceSendTriggerMethod(classLoader, fragmentClass, recorderTypeName);
    }

    @Override
    public Field findVoiceDurationField(ClassLoader classLoader, Method setDurationMethod) {
        return Unobfuscator.findVoiceDurationField(classLoader, setDurationMethod);
    }

    @Override
    public Method findVoiceGetPreviewStateMethod(ClassLoader classLoader, Class<?> delegateClass, String recorderTypeName, Class<?> stateSuperClass) {
        return Unobfuscator.findVoiceGetPreviewStateMethod(classLoader, delegateClass, recorderTypeName, stateSuperClass);
    }

    @Override
    public Class<?> loadFStatusClass(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadFStatusClass(classLoader);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Class<?> loadFStatusKeyClass(ClassLoader classLoader) {
        try {
            return Unobfuscator.loadFStatusKeyClass(classLoader);
        } catch (Exception e) {
            return null;
        }
    }
}
