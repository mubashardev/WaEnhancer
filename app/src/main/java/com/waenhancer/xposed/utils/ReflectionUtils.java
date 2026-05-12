package com.waenhancer.xposed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;

@SuppressWarnings("unused")
public class ReflectionUtils {

    private static SharedPreferences cachePrefs;

    /**
     * Initialize the SharedPreferences for caching reflection results
     *
     * @param context Application context
     */
    public static void initCache(Context context) {
        if (cachePrefs == null) {
            cachePrefs = context.getSharedPreferences("UnobfuscatorCache", Context.MODE_PRIVATE);
        }
    }

    public static Map<String, Class<?>> primitiveClasses = Map.of(
            "byte", Byte.TYPE,
            "short", Short.TYPE,
            "int", Integer.TYPE,
            "long", Long.TYPE,
            "float", Float.TYPE,
            "boolean", Boolean.TYPE
    );

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        var primitive = primitiveClasses.get(className);
        if (primitive != null) return primitive;
        return XposedHelpers.findClass(className, classLoader);
    }

    public static Method findMethodUsingFilter(Class<?> clazz, Predicate<Method> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredMethods()).filter(predicate).findFirst();
            if (results.isPresent()) return results.get();
        } while ((clazz = clazz.getSuperclass()) != null);
        throw new RuntimeException("Method not found");
    }

    /**
     * @noinspection SimplifyStreamApiCallChains
     */
    public static Method[] findAllMethodsUsingFilter(Class<?> clazz, Predicate<Method> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredMethods()).filter(predicate).collect(Collectors.toList());
            if (!results.isEmpty()) return results.toArray(new Method[0]);
        } while ((clazz = clazz.getSuperclass()) != null);
        throw new RuntimeException("Method not found");
    }

    public static Field findFieldUsingFilter(Class<?> clazz, Predicate<Field> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredFields()).filter(predicate).findFirst();
            if (results.isPresent()) return results.get();
        } while ((clazz = clazz.getSuperclass()) != null);
        throw new RuntimeException("Field not found");
    }

    /**
     * @noinspection SimplifyStreamApiCallChains
     */
    public static Constructor[] findAllConstructorsUsingFilter(Class<?> clazz, Predicate<Constructor> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredConstructors()).filter(predicate).collect(Collectors.toList());
            if (!results.isEmpty()) return results.toArray(new Constructor[0]);
        } while ((clazz = clazz.getSuperclass()) != null);
        return new Constructor[0];
    }

    public static Constructor findConstructorUsingFilter(Class<?> clazz, Predicate<Constructor> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredConstructors()).filter(predicate).findFirst();
            if (results.isPresent()) return results.get();
        } while ((clazz = clazz.getSuperclass()) != null);
        throw new RuntimeException("Constructor not found");
    }

    /**
     * @noinspection SimplifyStreamApiCallChains
     */
    @NonNull
    public static Field[] findAllFieldsUsingFilter(Class<?> clazz, @NonNull Predicate<Field> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredFields()).filter(predicate).collect(Collectors.toList());
            if (!results.isEmpty()) return results.toArray(new Field[0]);
        } while ((clazz = clazz.getSuperclass()) != null);
        return new Field[0];
    }


    public static Method findMethodUsingFilterIfExists(Class<?> clazz, Predicate<Method> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredMethods()).filter(predicate).findFirst();
            if (results.isPresent()) return results.get();
        } while ((clazz = clazz.getSuperclass()) != null);
        return null;
    }

    public static Field findFieldUsingFilterIfExists(Class<?> clazz, Predicate<Field> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredFields()).filter(predicate).findFirst();
            if (results.isPresent()) return results.get();
        } while ((clazz = clazz.getSuperclass()) != null);
        return null;
    }

    public static boolean isOverridden(Method method) {
        try {
            Class<?> superclass = method.getDeclaringClass().getSuperclass();
            if (superclass == null) return false;
            Method parentMethod = superclass.getMethod(method.getName(), method.getParameterTypes());
            return !parentMethod.equals(method);

        } catch (NoSuchMethodException e) {
            return false;
        }
    }


    public static List<Field> getFieldsByExtendType(Class<?> cls, Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = cls;
        while (current != null) {
            for (Field f : current.getDeclaredFields()) {
                if (type.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    fields.add(f);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    public static List<Field> getFieldsByType(Class<?> cls, Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = cls;
        while (current != null) {
            for (Field f : current.getDeclaredFields()) {
                if (type == f.getType()) {
                    f.setAccessible(true);
                    fields.add(f);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    public static Field getFieldByExtendType(Class<?> cls, String className) {
        if (cls == null) return null;
        if (className == null) return null;
        return getFieldByExtendType(cls, findClass(className, cls.getClassLoader()));
    }

    public static Field getFieldByExtendType(Class<?> cls, Class<?> type) {
        if (cls == null || type == null) return null;

        if (cachePrefs != null) {
            String cacheKey = "field_cache_" + cls.getName() + "_" + type.getName();
            String cachedFieldName = cachePrefs.getString(cacheKey, null);
            if (cachedFieldName != null) {
                try {
                    Field field = XposedHelpers.findField(cls, cachedFieldName);
                    if (type.isAssignableFrom(field.getType())) {
                        return field;
                    }
                } catch (XposedHelpers.ClassNotFoundError | NoSuchFieldError e) {
                    cachePrefs.edit().remove(cacheKey).apply();
                }
            }
        }

        Field field = findFieldUsingFilterIfExists(cls, f -> type.isAssignableFrom(f.getType()));

        if (field != null && cachePrefs != null) {
            String cacheKey = "field_cache_" + cls.getName() + "_" + type.getName();
            cachePrefs.edit().putString(cacheKey, field.getName()).apply();
        }

        return field;
    }

    public static Field getFieldByType(Class<?> cls, String className) {
        if (cls == null) return null;
        if (className == null) return null;
        return getFieldByType(cls, findClass(className, cls.getClassLoader()));
    }


    public static Field getFieldByType(Class<?> cls, Class<?> type) {
        if (cls == null || type == null) return null;

        if (cachePrefs != null) {
            String cacheKey = "field_cache_direct_" + cls.getName() + "_" + type.getName();
            String cachedFieldName = cachePrefs.getString(cacheKey, null);
            if (cachedFieldName != null) {
                try {
                    Field field = XposedHelpers.findField(cls, cachedFieldName);
                    if (type == field.getType()) {
                        return field;
                    }
                } catch (XposedHelpers.ClassNotFoundError | NoSuchFieldError e) {
                    cachePrefs.edit().remove(cacheKey).apply();
                }
            }
        }

        Field field = findFieldUsingFilterIfExists(cls, f -> type == f.getType());

        if (field != null && cachePrefs != null) {
            String cacheKey = "field_cache_direct_" + cls.getName() + "_" + type.getName();
            cachePrefs.edit().putString(cacheKey, field.getName()).apply();
        }

        return field;
    }

    public static Object callMethod(Method method, Object instance, Object... args) {
        try {
            var count = method.getParameterCount();
            if (count != args.length) {
                var newargs = initArray(method.getParameterTypes());
                System.arraycopy(args, 0, newargs, 0, Math.min(args.length, count));
                args = newargs;
            }
            return method.invoke(instance, args);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object[] initArray(Class<?>[] parameterTypes) {
        var args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = getDefaultValue(parameterTypes[i]);
        }
        return args;
    }

    public static Object getDefaultValue(Class<?> paramType) {
        if (paramType == int.class || paramType == Integer.class) {
            return 0;
        } else if (paramType == long.class || paramType == Long.class) {
            return 0L;
        } else if (paramType == double.class || paramType == Double.class) {
            return 0.0;
        } else if (paramType == boolean.class || paramType == Boolean.class) {
            return false;
        }
        return null;
    }

    public static Object getObjectField(Field field, Object thisObject) {
        try {
            return field.get(thisObject);
        } catch (Exception e) {
            return null;
        }
    }

    public static int findIndexOfType(Object[] args, Class<?> type) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) continue;
            if (args[i] instanceof Class) {
                if (type.isAssignableFrom((Class) args[i])) return i;
                continue;
            }
            if (type.isInstance(args[i])) return i;
        }
        return -1;
    }

    public static <T> List<Pair<Integer, T>> findInstancesOfType(Object[] args, Class<T> type) {
        var result = new ArrayList<Pair<Integer, T>>();
        for (int i = 0; i < args.length; i++) {
            var arg = args[i];
            if (arg == null || arg instanceof Class) continue;

            if (type.isInstance(arg)) {
                result.add(new Pair<>(i, type.cast(arg)));
            }
        }
        return result;
    }

    public static <T> List<Pair<Integer, Class<? extends T>>> findClassesOfType(Class<?>[] args, Class<T> type) {
        List<Pair<Integer, Class<? extends T>>> result = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            Class<?> arg = args[i];
            if (type.isAssignableFrom(arg)) {
                //noinspection unchecked
                result.add(new Pair<>(i, (Class<? extends T>) arg));
            }
        }
        return result;
    }

    public static <T> T getArg(Object[] args, Class<T> typeClass, int i) {
        var list = findInstancesOfType(args, typeClass);
        if (list.size() <= i) return null;
        return list.get(i).second;
    }

    public static boolean isCalledFromString(String contains) {
        var trace = Thread.currentThread().getStackTrace();
        for (var element : trace) {
            if (element.toString().contains(contains)) return true;
        }
        return false;
    }

    public static boolean isCalledFromStrings(String... contains) {
        var trace = Thread.currentThread().getStackTrace();
        for (var element : trace) {
            var s = element.toString();
            for (String c : contains) {
                if (s.contains(c)) return true;
            }
        }
        return false;
    }

    public static boolean isClassSimpleNameString(Class<?> aClass, String s) {
        try {
            var cls = aClass;
            do {
                if (cls.getSimpleName().equals(s)) return true;
                if (cls.getName().startsWith("android.widget.") || cls.getName().startsWith("android.view."))
                    return false;
            } while ((cls = cls.getSuperclass()) != null);
        } catch (Exception ignored) {
        }
        return false;
    }

    public synchronized static boolean isCalledFromClass(Class<?> cls) {
        var trace = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : trace) {
            if (stackTraceElement.getClassName().equals(cls.getName()))
                return true;
        }
        return false;
    }

    public synchronized static boolean isCalledFromMethod(Method method) {
        var trace = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : trace) {
            if (stackTraceElement.getClassName().equals(method.getDeclaringClass().getName()) && stackTraceElement.getMethodName().equals(method.getName()))
                return true;
        }
        return false;
    }


    public static void setObjectField(Field field, Object instance, Object value) {
        try {
            field.set(instance, value);
        } catch (Exception ignored) {
        }
    }

    private static Object convertToRealKey(Object unknownKey, ClassLoader classLoader, boolean debug) {
        if (unknownKey == null) return null;
        try {
            Class<?> keyClass = unknownKey.getClass();
            String id = null;
            Boolean fromMe = null;
            Object remoteJid = null;

            // Find id (String field)
            for (Field f : keyClass.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == String.class) {
                    id = (String) f.get(unknownKey);
                    break;
                }
            }

            // Find fromMe (boolean field)
            for (Field f : keyClass.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                    fromMe = f.getBoolean(unknownKey);
                    break;
                }
            }

            // Find remoteJid (first non-primitive object field that is not String, preferably status@broadcast)
            for (Field f : keyClass.getDeclaredFields()) {
                f.setAccessible(true);
                if (!f.getType().isPrimitive() && f.getType() != String.class) {
                    Object val = f.get(unknownKey);
                    if (val != null) {
                        String valStr = val.toString();
                        if ("status@broadcast".equals(valStr)) {
                            remoteJid = val;
                            break;
                        }
                    }
                }
            }

            // Fallback for remoteJid if not exactly status@broadcast
            if (remoteJid == null) {
                for (Field f : keyClass.getDeclaredFields()) {
                    f.setAccessible(true);
                    if (!f.getType().isPrimitive() && f.getType() != String.class) {
                        Object val = f.get(unknownKey);
                        if (val != null) {
                            String valStr = val.toString();
                            if (valStr.contains("@")) {
                                remoteJid = val;
                                break;
                            }
                        }
                    }
                }
            }

            if (debug) {
                ;
            }

            if (id != null && fromMe != null && remoteJid != null) {
                Class<?> realKeyClass = classLoader.loadClass("X.1eC");
                if (realKeyClass != null) {
                    Object realKey = XposedHelpers.newInstance(realKeyClass, remoteJid, id, fromMe);
                    if (debug) {
                        ;
                    }
                    return realKey;
                }
            }
        } catch (Exception e) {
            XposedBridge.log("[WAE-DEBUG] Error converting key to real: " + e);
        }
        return null;
    }

    public static Object findFMessageInObject(Object object, Class<?> fMessageClass, Class<?> keyClass, ClassLoader classLoader) {
        return findFMessageInObject(object, fMessageClass, keyClass, classLoader,
                Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private static Object findFMessageInObject(Object object, Class<?> fMessageClass, Class<?> keyClass,
                                               ClassLoader classLoader, Set<Object> visited, int depth) {
        if (object == null) return null;
        if (depth > 4) return null;
        if (fMessageClass != null && fMessageClass.isInstance(object)) return object;
        if (!visited.add(object)) return null;

        Object containerMatch = findFMessageInContainer(object, fMessageClass, keyClass, classLoader, visited, depth);
        if (containerMatch != null) return containerMatch;

        boolean debug = object.getClass().getName().contains("8hO");
        if (debug) {
            ;
        }

        // 1. Search for a direct field of type fMessageClass
        if (fMessageClass != null) {
            Field field = getFieldByExtendType(object.getClass(), fMessageClass);
            if (field != null) {
                Object val = getObjectField(field, object);
                if (val != null && fMessageClass.isInstance(val)) {
                    if (debug) ;
                    return val;
                }
            }
        }

        // 2. Search for a field of type keyClass
        if (keyClass != null) {
            Field field = getFieldByExtendType(object.getClass(), keyClass);
            if (field != null) {
                Object val = getObjectField(field, object);
                if (val != null && keyClass.isInstance(val)) {
                    if (debug) ;
                    try {
                        Object fmsg = com.waenhancer.xposed.core.WppCore.getFMessageFromKey(val);
                        if (fmsg != null) return fmsg;
                    } catch (Exception ignored) {}
                }
            }
        }

        // 3. Class hierarchy nested search
        Class<?> currentClass = object.getClass();
        while (currentClass != null && currentClass != Object.class) {
            if (debug) {
                XposedBridge.log("[WAE-DEBUG] Scanning class level: " + currentClass.getName());
            }
            List<Field> fields = getCachedDeclaredFields(currentClass);
            for (Field field : fields) {
                if (field.getType().isPrimitive() || field.getType().getName().startsWith("java.") || field.getType().getName().startsWith("android.")) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object nestedObj = field.get(object);
                    if (debug) {
                        ;
                        if (nestedObj != null && nestedObj.getClass().getName().contains("8gx")) {
                            ;
                            for (Field f : getCachedDeclaredFields(nestedObj.getClass())) {
                                f.setAccessible(true);
                                try {
                                    ;
                                } catch (Exception e) {
                                    XposedBridge.log("[WAE-DEBUG]   8gx Field read error: " + f.getName() + " -> " + e);
                                }
                            }
                        }
                    }
                    if (nestedObj != null) {
                        Object nestedContainerMatch = findFMessageInContainer(nestedObj, fMessageClass, keyClass, classLoader, visited, depth + 1);
                        if (nestedContainerMatch != null) {
                            return nestedContainerMatch;
                        }
                        if (fMessageClass != null && fMessageClass.isInstance(nestedObj)) {
                            if (debug) ;
                            return nestedObj;
                        }
                        if (fMessageClass != null) {
                            Field nestedFMsgField = getFieldByExtendType(nestedObj.getClass(), fMessageClass);
                            if (debug) {
                                ;
                            }
                            if (nestedFMsgField != null) {
                                Object val = getObjectField(nestedFMsgField, nestedObj);
                                if (val != null && fMessageClass.isInstance(val)) {
                                    if (debug) ;
                                    return val;
                                }
                            }
                        }
                        // Check fields of nestedObj for any Key (type-based OR toString-based)
                        for (Field nestedKeyField : getCachedDeclaredFields(nestedObj.getClass())) {
                            nestedKeyField.setAccessible(true);
                            try {
                                Object val = nestedKeyField.get(nestedObj);
                                if (val != null) {
                                    boolean isKey = false;
                                    if (keyClass != null && keyClass.isInstance(val)) {
                                        isKey = true;
                                    } else {
                                        String valStr = val.toString();
                                        if (valStr != null && (valStr.startsWith("Key(id=") || valStr.startsWith("Key("))) {
                                            isKey = true;
                                        }
                                    }
                                    if (isKey) {
                                        if (debug) ;
                                        Object targetKey = val;
                                        if (keyClass != null && !keyClass.isInstance(val)) {
                                            targetKey = convertToRealKey(val, classLoader, debug);
                                        }
                                        if (targetKey != null) {
                                            try {
                                                Object fmsg = com.waenhancer.xposed.core.WppCore.getFMessageFromKey(targetKey);
                                                if (fmsg != null) return fmsg;
                                            } catch (Exception e) {
                                                if (debug) XposedBridge.log("[WAE-DEBUG] Error getting message from key: " + e);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        Object recursiveMatch = findFMessageInObject(nestedObj, fMessageClass, keyClass, classLoader, visited, depth + 1);
                        if (recursiveMatch != null) {
                            return recursiveMatch;
                        }
                    }
                } catch (Exception e) {
                    if (debug) XposedBridge.log("[WAE-DEBUG] Error reading field: " + field.getName() + " -> " + e);
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        return null;
    }

    private static Object findFMessageInContainer(Object object, Class<?> fMessageClass, Class<?> keyClass,
                                                  ClassLoader classLoader, Set<Object> visited, int depth) {
        if (object == null || depth > 4) return null;

        if (object instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Object match = findFMessageInObject(item, fMessageClass, keyClass, classLoader, visited, depth + 1);
                if (match != null) return match;
            }
        } else if (object.getClass().isArray() && !object.getClass().getComponentType().isPrimitive()) {
            Object[] array = (Object[]) object;
            for (Object item : array) {
                Object match = findFMessageInObject(item, fMessageClass, keyClass, classLoader, visited, depth + 1);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, List<Field>> declaredFieldsCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static List<Field> getCachedDeclaredFields(Class<?> clazz) {
        if (clazz == null) return Collections.emptyList();
        return declaredFieldsCache.computeIfAbsent(clazz, c -> {
            try {
                return Arrays.asList(c.getDeclaredFields());
            } catch (Throwable t) {
                return Collections.emptyList();
            }
        });
    }

}
