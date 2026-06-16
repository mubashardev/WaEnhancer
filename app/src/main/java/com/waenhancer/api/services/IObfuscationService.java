package com.waenhancer.api.services;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public interface IObfuscationService {
    Class<?> loadClass(String className);
    Method loadMethod(String className, String methodName, Class<?>... parameterTypes);
    Field loadField(String className, String fieldName);
    Method findMethodByStringSignature(String signature);
    Class<?> findClassByUniqueStrings(String... strings);
    Object getDexKitInstance();

    Method loadGhostModeMethod(ClassLoader classLoader);
    Method loadPropsBooleanMethod(ClassLoader classLoader);
    Class<?> loadVideoViewContainerClass(ClassLoader classLoader);
    Class<?> loadImageVewContainerClass(ClassLoader classLoader);
    Class<?> loadConversationRowClass(ClassLoader classLoader);
    Class<?> loadFMessageClass(ClassLoader classLoader);
    Class<?> loadAbstractMediaMessageClass(ClassLoader classLoader);
    Method loadPageOnViewCreatedMethod(ClassLoader classLoader);
    Method loadPausePlaybackMethod(ClassLoader classLoader);
    Method loadResumePlaybackMethod(ClassLoader classLoader);
    Method loadStatusReplyMethod(ClassLoader classLoader);
    Method loadGetPageControllerMethod(ClassLoader classLoader);
    Class<?> loadVideoPlayerClass(ClassLoader classLoader);
    Class<?> findFirstClassUsingName(ClassLoader classLoader, String matchType, String name);
    Class<?> findFirstClassUsingStrings(ClassLoader classLoader, String[] strings);
    Object getDexKit(ClassLoader classLoader);
    List<String> findCallers(ClassLoader classLoader, Method method);

    java.lang.reflect.Method findVoiceTransitionMethod(ClassLoader classLoader, Class<?> fragmentClass, String recorderTypeName);
    java.lang.reflect.Method findVoiceSendTriggerMethod(ClassLoader classLoader, Class<?> fragmentClass, String recorderTypeName);
    java.lang.reflect.Field findVoiceDurationField(ClassLoader classLoader, java.lang.reflect.Method setDurationMethod);
    java.lang.reflect.Method findVoiceGetPreviewStateMethod(ClassLoader classLoader, Class<?> delegateClass, String recorderTypeName, Class<?> stateSuperClass);
    Class<?> loadFStatusClass(ClassLoader classLoader);
    Class<?> loadFStatusKeyClass(ClassLoader classLoader);
}
