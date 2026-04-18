package com.waenhancer.xposed.features.others;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.xposed.utils.ResId;
import com.waenhancer.xposed.utils.Utils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class GroupAdmin extends Feature {

    public GroupAdmin(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        boolean enabled = prefs.getBoolean("admin_grp", false)
            || prefs.getBoolean("show_admin_group_icon", false)
            || (!prefs.contains("admin_grp") && !prefs.contains("show_admin_group_icon"));
        XposedBridge.log("GroupAdmin: pref admin_grp=" + prefs.getBoolean("admin_grp", false)
            + ", show_admin_group_icon=" + prefs.getBoolean("show_admin_group_icon", false)
            + ", contains(admin_grp)=" + prefs.contains("admin_grp")
            + ", contains(show_admin_group_icon)=" + prefs.contains("show_admin_group_icon")
            + ", enabled=" + enabled);
        if (!enabled) return;
        var jidFactory = Unobfuscator.loadJidFactory(classLoader);
        var grpAdmin1 = Unobfuscator.loadGroupAdminMethod(classLoader);
        var grpcheckAdmin = Unobfuscator.loadGroupCheckAdminMethod(classLoader);
        var fMessageClass = Unobfuscator.loadFMessageClass(classLoader);
        Class<?> conversationRowClass = null;
        try {
            conversationRowClass = Unobfuscator.loadConversationRowClass(classLoader);
        } catch (Throwable ignored) {
        }
        var hooked = new XC_MethodHook() {
            @SuppressLint("ResourceType")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    XposedBridge.log("GroupAdmin HOOK: afterHookedMethod triggered, thisObject=" + (param.thisObject != null ? param.thisObject.getClass().getName() : "null") + " args=" + (param.args != null ? param.args.length : 0));
                    var targetObj = param.thisObject;
                    if (targetObj == null && param.args != null) {
                        for (Object arg : param.args) {
                            if (arg instanceof android.view.View) {
                                targetObj = arg;
                                break;
                            }
                        }
                    }
                    if (targetObj == null && param.args != null) {
                        for (Object arg : param.args) {
                            if (arg == null) continue;
                            if (XposedHelpers.findMethodExactIfExists(arg.getClass(), "getFMessage") != null) {
                                targetObj = arg;
                                break;
                            }
                        }
                    }
                    if (targetObj == null) {
                        XposedBridge.log("GroupAdmin HOOK: No target object found!");
                        return;
                    }

                    Object fMessageObj = null;
                    if (XposedHelpers.findMethodExactIfExists(targetObj.getClass(), "getFMessage") != null) {
                        fMessageObj = XposedHelpers.callMethod(targetObj, "getFMessage");
                    }
                    if (fMessageObj == null && param.args != null) {
                        for (Object arg : param.args) {
                            if (arg != null && fMessageClass.isAssignableFrom(arg.getClass())) {
                                fMessageObj = arg;
                                break;
                            }
                        }
                    }
                    XposedBridge.log("GroupAdmin HOOK: fMessageObj=" + (fMessageObj != null ? fMessageObj.getClass().getName() : "null"));
                    if (fMessageObj == null) return;
                    var fMessage = new FMessageWpp(fMessageObj);
                    var userJid = fMessage.getUserJid();
                    if (userJid == null || userJid.userJid == null) return;
                    XposedBridge.log("GroupAdmin HOOK: userJid=" + (userJid != null ? userJid.toString() : "null"));
                    var chatCurrentJid = WppCore.getCurrentUserJid();
                    if (chatCurrentJid == null || !chatCurrentJid.isGroup()) return;
                    XposedBridge.log("GroupAdmin HOOK: In a group chat, checking admin status...");

                    Object participantOwner = targetObj;
                    var field = ReflectionUtils.getFieldByType(participantOwner.getClass(), grpcheckAdmin.getDeclaringClass());
                    if (field == null && param.args != null) {
                        for (Object arg : param.args) {
                            if (arg == null) continue;
                            field = ReflectionUtils.getFieldByType(arg.getClass(), grpcheckAdmin.getDeclaringClass());
                            if (field != null) {
                                participantOwner = arg;
                                break;
                            }
                        }
                    }
                    if (field == null) {
                        XposedBridge.log("GroupAdmin HOOK: field for grpcheckAdmin not found in target/args");
                        return;
                    }
                    var grpParticipants = field.get(participantOwner);
                    Object jidGrp;
                    if (Modifier.isStatic(jidFactory.getModifiers())) {
                        jidGrp = jidFactory.invoke(null, chatCurrentJid.getUserRawString());
                    } else {
                        Object factoryInstance = XposedHelpers.newInstance(jidFactory.getDeclaringClass());
                        jidGrp = jidFactory.invoke(factoryInstance, chatCurrentJid.getUserRawString());
                    }
                    var result = grpcheckAdmin.invoke(grpParticipants, jidGrp, userJid.userJid);
                    XposedBridge.log("GroupAdmin HOOK: isAdmin result=" + result);

                    View view = targetObj instanceof View ? (View) targetObj : null;
                    if (view == null && param.args != null) {
                        for (Object arg : param.args) {
                            if (arg instanceof View v) {
                                view = v;
                                break;
                            }
                        }
                    }
                    if (view == null) {
                        view = extractViewFromObject(targetObj);
                    }
                    if (view == null && param.args != null) {
                        for (Object arg : param.args) {
                            if (arg == null) continue;
                            view = extractViewFromObject(arg);
                            if (view != null) break;
                        }
                    }
                    if (view == null) {
                        XposedBridge.log("GroupAdmin HOOK: no row view found, skipping icon render");
                        return;
                    }

                    var context = view.getContext();
                    ImageView iconAdmin;
                    if ((iconAdmin = view.findViewById(0x7fff0010)) == null) {
                        var nameGroup = (LinearLayout) view.findViewById(Utils.getID("name_in_group", "id"));
                        if (nameGroup == null) {
                            nameGroup = findNameContainer(view);
                        }
                        if (nameGroup == null) {
                            XposedBridge.log("GroupAdmin HOOK: name_in_group view not found!");
                            return;
                        }
                        var view1 = new LinearLayout(context);
                        view1.setOrientation(LinearLayout.HORIZONTAL);
                        view1.setGravity(Gravity.CENTER_VERTICAL);
                        var nametv = nameGroup.getChildCount() > 0 ? nameGroup.getChildAt(0) : null;
                        if (nametv == null) {
                            XposedBridge.log("GroupAdmin HOOK: no name text child found");
                            return;
                        }
                        iconAdmin = new ImageView(context);
                        var size = Utils.dipToPixels(16);
                        iconAdmin.setLayoutParams(new LinearLayout.LayoutParams(size, size));
                        iconAdmin.setImageResource(ResId.drawable.admin);
                        iconAdmin.setId(0x7fff0010);
                        nameGroup.removeView(nametv);
                        view1.addView(nametv);
                        view1.addView(iconAdmin);
                        nameGroup.addView(view1, 0);
                    }
                    iconAdmin.setVisibility(result != null && (boolean) result ? View.VISIBLE : View.GONE);
                    XposedBridge.log("GroupAdmin HOOK: Icon visibility set to " + (result != null && (boolean) result ? "VISIBLE" : "GONE"));
                } catch (Throwable t) {
                    XposedBridge.log("GroupAdmin HOOK ERROR: " + t.getMessage());
                    XposedBridge.log(t);
                }
            }
        };

        Set<Method> bindMethods = collectBindCandidates(grpAdmin1, fMessageClass);
        if (conversationRowClass != null) {
            bindMethods.addAll(collectBindCandidatesFromClass(conversationRowClass, fMessageClass));
        }
        for (Method method : bindMethods) {
            XposedBridge.hookMethod(method, hooked);
            XposedBridge.log("GroupAdmin: Hooked bind candidate " + method);
        }
    }

    private Set<Method> collectBindCandidates(@NonNull Method primaryMethod, @NonNull Class<?> fMessageClass) {
        Set<Method> result = new LinkedHashSet<>();
        result.add(primaryMethod);

        Class<?> rowClass = primaryMethod.getDeclaringClass();
        for (Method method : rowClass.getDeclaredMethods()) {
            if (method.isSynthetic()) continue;
            if (method.getReturnType() != Void.TYPE) continue;
            if (method.getParameterCount() == 0 || method.getParameterCount() > 4) continue;

            boolean looksLikeBind = false;
            for (Class<?> paramType : method.getParameterTypes()) {
                if (fMessageClass.isAssignableFrom(paramType) || paramType.getName().contains("FMessage")) {
                    looksLikeBind = true;
                    break;
                }
            }

            if (!looksLikeBind && Modifier.isStatic(method.getModifiers())) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length > 0 && rowClass.isAssignableFrom(params[0])) {
                    looksLikeBind = true;
                }
            }

            if (looksLikeBind) {
                method.setAccessible(true);
                result.add(method);
            }
        }

        return result;
    }

    private Set<Method> collectBindCandidatesFromClass(@NonNull Class<?> rowClass, @NonNull Class<?> fMessageClass) {
        Set<Method> result = new HashSet<>();
        Class<?> cursor = rowClass;
        while (cursor != null && cursor != Object.class) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (method.isSynthetic()) continue;
                if (method.getParameterCount() == 0 || method.getParameterCount() > 7) continue;

                boolean looksLikeBind = false;
                for (Class<?> paramType : method.getParameterTypes()) {
                    if (fMessageClass.isAssignableFrom(paramType)
                            || paramType.getName().contains("FMessage")
                            || paramType.getName().contains("12L")
                            || paramType.getName().contains("1Z7")) {
                        looksLikeBind = true;
                        break;
                    }
                }

                if (!looksLikeBind && Modifier.isStatic(method.getModifiers())) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length > 0 && rowClass.isAssignableFrom(params[0])) {
                        looksLikeBind = true;
                    }
                }

                if (!looksLikeBind && XposedHelpers.findMethodExactIfExists(method.getDeclaringClass(), "setFMessage", method.getParameterTypes()) != null) {
                    looksLikeBind = true;
                }

                if (looksLikeBind) {
                    method.setAccessible(true);
                    result.add(method);
                }
            }
            cursor = cursor.getSuperclass();
        }
        return result;
    }

    private LinearLayout findNameContainer(@NonNull View root) {
        if (root instanceof LinearLayout layout) {
            for (int i = 0; i < layout.getChildCount(); i++) {
                if (layout.getChildAt(i) instanceof TextView) {
                    return layout;
                }
            }
        }
        if (root instanceof ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) {
                var child = vg.getChildAt(i);
                var found = findNameContainer(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View extractViewFromObject(Object holder) {
        if (holder == null) return null;
        Class<?> cursor = holder.getClass();
        while (cursor != null && cursor != Object.class) {
            for (Field field : cursor.getDeclaredFields()) {
                if (!View.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(holder);
                    if (value instanceof View v) {
                        return v;
                    }
                } catch (Throwable ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GroupAdmin";
    }
}
