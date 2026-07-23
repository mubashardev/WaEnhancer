package com.waenhancer.xposed.core.components;

import com.waenhancer.xposed.core.devkit.Unobfuscator;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.lang.reflect.Array;

public class ProtocolTreeNodeWpp {
    public final Object mInstance;

    public static Class<?> TYPE;
    private static Field fieldTag;
    private static Field fieldData;
    private static Field fieldChildren;
    private static Field fieldAttributes;
    private static Constructor<?> constructorFull;

    public ProtocolTreeNodeWpp(Object instance) {
        if (instance == null || !TYPE.isInstance(instance)) {
            throw new RuntimeException("object is not a ProtocolTreeNode");
        }
        this.mInstance = instance;
    }

    public static void initialize(ClassLoader classLoader) {
        try {
            TYPE = Unobfuscator.loadProtocolTreeNodeClass(classLoader);
            Class<?> keyValueClass = Unobfuscator.loadKeyValueClass(classLoader);
            KeyValueWpp.initialize(keyValueClass);

            for (Field f : TYPE.getDeclaredFields()) {
                if (f.getType() == String.class) {
                    fieldTag = f;
                    fieldTag.setAccessible(true);
                    break;
                }
            }

            for (Field f : TYPE.getDeclaredFields()) {
                if (f.getType() == byte[].class) {
                    fieldData = f;
                    fieldData.setAccessible(true);
                    break;
                }
            }

            Class<?> selfArrayType = Array.newInstance(TYPE, 0).getClass();
            for (Field f : TYPE.getDeclaredFields()) {
                if (f.getType() == selfArrayType) {
                    fieldChildren = f;
                    fieldChildren.setAccessible(true);
                    break;
                }
            }

            Class<?> keyValueArrayType = Array.newInstance(keyValueClass, 0).getClass();
            for (Field f : TYPE.getDeclaredFields()) {
                if (f.getType() == keyValueArrayType) {
                    fieldAttributes = f;
                    fieldAttributes.setAccessible(true);
                    break;
                }
            }

            constructorFull = TYPE.getDeclaredConstructor(
                    String.class,
                    byte[].class,
                    keyValueArrayType,
                    selfArrayType
            );
            constructorFull.setAccessible(true);

        } catch (Exception e) {
            XposedBridge.log("ProtocolTreeNodeWpp Init Error: " + e.getMessage());
        }
    }

    public static ProtocolTreeNodeWpp create(
            String tag,
            byte[] data,
            List<KeyValueWpp> attributes,
            List<ProtocolTreeNodeWpp> children
    ) {
        try {
            Object attrArray = null;
            if (attributes != null && !attributes.isEmpty()) {
                Object[] arr = (Object[]) Array.newInstance(KeyValueWpp.TYPE, attributes.size());
                for (int i = 0; i < attributes.size(); i++) {
                    arr[i] = attributes.get(i).mInstance;
                }
                attrArray = arr;
            }

            Object childArray = null;
            if (children != null && !children.isEmpty()) {
                Object[] arr = (Object[]) Array.newInstance(TYPE, children.size());
                for (int i = 0; i < children.size(); i++) {
                    arr[i] = children.get(i).mInstance;
                }
                childArray = arr;
            }

            Object instance = constructorFull.newInstance(tag, data, attrArray, childArray);
            return new ProtocolTreeNodeWpp(instance);
        } catch (Exception e) {
            XposedBridge.log(e);
            return null;
        }
    }

    public String getTag() {
        try {
            return (String) fieldTag.get(mInstance);
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] getData() {
        try {
            return (byte[]) fieldData.get(mInstance);
        } catch (Exception e) {
            return null;
        }
    }

    public List<ProtocolTreeNodeWpp> getChildren() {
        try {
            Object[] arr = (Object[]) fieldChildren.get(mInstance);
            if (arr == null) return Collections.emptyList();
            List<ProtocolTreeNodeWpp> list = new ArrayList<>();
            for (Object obj : arr) {
                if (obj != null) {
                    list.add(new ProtocolTreeNodeWpp(obj));
                }
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<KeyValueWpp> getAttributes() {
        try {
            Object[] arr = (Object[]) fieldAttributes.get(mInstance);
            if (arr == null) return Collections.emptyList();
            List<KeyValueWpp> list = new ArrayList<>();
            for (Object obj : arr) {
                if (obj != null) {
                    list.add(new KeyValueWpp(obj));
                }
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public void addKeyValue(String key, String value) {
        try {
            List<KeyValueWpp> attrs = getAttributes();
            KeyValueWpp newKv = KeyValueWpp.create(key, value);
            if (newKv == null) return;

            Object[] newArray = (Object[]) Array.newInstance(KeyValueWpp.TYPE, attrs.size() + 1);
            for (int i = 0; i < attrs.size(); i++) {
                newArray[i] = attrs.get(i).mInstance;
            }
            newArray[attrs.size()] = newKv.mInstance;
            fieldAttributes.set(mInstance, newArray);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    public void removeAllKeyValuesByKey(String key) {
        try {
            List<KeyValueWpp> attrs = getAttributes();
            List<Object> filtered = new ArrayList<>();
            for (KeyValueWpp attr : attrs) {
                if (!key.equals(attr.getKey())) {
                    filtered.add(attr.mInstance);
                }
            }

            Object[] newArray = (Object[]) Array.newInstance(KeyValueWpp.TYPE, filtered.size());
            for (int i = 0; i < filtered.size(); i++) {
                newArray[i] = filtered.get(i);
            }
            fieldAttributes.set(mInstance, newArray);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    public void removeKeyValue(KeyValueWpp keyValueToRemove) {
        try {
            List<KeyValueWpp> attrs = getAttributes();
            List<Object> filtered = new ArrayList<>();
            for (KeyValueWpp attr : attrs) {
                if (attr.mInstance != keyValueToRemove.mInstance) {
                    filtered.add(attr.mInstance);
                }
            }

            Object[] newArray = (Object[]) Array.newInstance(KeyValueWpp.TYPE, filtered.size());
            for (int i = 0; i < filtered.size(); i++) {
                newArray[i] = filtered.get(i);
            }
            fieldAttributes.set(mInstance, newArray);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    public void modifyKeyValue(String key, String newValue) {
        try {
            List<KeyValueWpp> attrs = getAttributes();
            Object[] newArray = (Object[]) Array.newInstance(KeyValueWpp.TYPE, attrs.size());
            for (int i = 0; i < attrs.size(); i++) {
                KeyValueWpp attr = attrs.get(i);
                if (key.equals(attr.getKey())) {
                    KeyValueWpp newKv = KeyValueWpp.create(key, newValue);
                    newArray[i] = newKv != null ? newKv.mInstance : attr.mInstance;
                } else {
                    newArray[i] = attr.mInstance;
                }
            }
            fieldAttributes.set(mInstance, newArray);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    public String getFirstKeyValue(String key) {
        for (KeyValueWpp attr : getAttributes()) {
            if (key.equals(attr.getKey())) {
                return attr.getValue();
            }
        }
        return null;
    }

    public static class KeyValueWpp {
        public final Object mInstance;

        public static Class<?> TYPE;
        private static Field fieldKey;
        private static Field fieldValue;
        private static Field fieldJid;
        private static Constructor<?> constructorStringString;

        public KeyValueWpp(Object instance) {
            if (instance == null || !TYPE.isInstance(instance)) {
                throw new RuntimeException("object is not a KeyValue");
            }
            this.mInstance = instance;
        }

        public static void initialize(Class<?> keyValueClass) {
            try {
                TYPE = keyValueClass;

                List<Field> stringFields = new ArrayList<>();
                for (Field f : TYPE.getDeclaredFields()) {
                    if (f.getType() == String.class) {
                        stringFields.add(f);
                    }
                }
                if (stringFields.size() >= 2) {
                    fieldKey = stringFields.get(0);
                    fieldKey.setAccessible(true);
                    fieldValue = stringFields.get(1);
                    fieldValue.setAccessible(true);
                }

                for (Field f : TYPE.getDeclaredFields()) {
                    Class<?> type = f.getType();
                    if (type == FMessageWpp.UserJid.TYPE_JID || 
                        type == FMessageWpp.UserJid.TYPE_USERJID || 
                        type == FMessageWpp.UserJid.TYPE_DEVICEJID || 
                        type == FMessageWpp.UserJid.TYPE_PHONEUSERJID ||
                        (FMessageWpp.UserJid.TYPE_JID != null && FMessageWpp.UserJid.TYPE_JID.isAssignableFrom(type))) {
                        fieldJid = f;
                        fieldJid.setAccessible(true);
                        break;
                    }
                }
                if (fieldJid == null) {
                    for (Field f : TYPE.getDeclaredFields()) {
                        if (!f.getType().isPrimitive() && f.getType() != String.class) {
                            fieldJid = f;
                            fieldJid.setAccessible(true);
                            break;
                        }
                    }
                }
                if (fieldJid == null) {
                    fieldJid = TYPE.getDeclaredFields()[0];
                    fieldJid.setAccessible(true);
                }

                constructorStringString = TYPE.getDeclaredConstructor(String.class, String.class);
                constructorStringString.setAccessible(true);
            } catch (Exception e) {
                XposedBridge.log("KeyValueWpp Init Error: " + e.getMessage());
            }
        }

        public static KeyValueWpp create(String key, String value) {
            try {
                Object instance = constructorStringString.newInstance(key, value);
                return new KeyValueWpp(instance);
            } catch (Exception e) {
                XposedBridge.log(e);
                return null;
            }
        }

        public String getKey() {
            try {
                return (String) fieldKey.get(mInstance);
            } catch (Exception e) {
                return null;
            }
        }

        public String getValue() {
            try {
                return (String) fieldValue.get(mInstance);
            } catch (Exception e) {
                return null;
            }
        }

        public void setValue(String value) {
            try {
                fieldValue.set(mInstance, value);
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        }

        public FMessageWpp.UserJid getUserJid() {
            try {
                Object jidObj = fieldJid.get(mInstance);
                if (jidObj == null) return null;
                FMessageWpp.UserJid wrapped = new FMessageWpp.UserJid(jidObj);
                return new FMessageWpp.UserJid(wrapped.getPhoneRawString());
            } catch (Exception e) {
                return null;
            }
        }

        public void setUserJid(FMessageWpp.UserJid userJid) {
            try {
                fieldJid.set(mInstance, userJid != null ? userJid.userJid : null);
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        }
    }
}