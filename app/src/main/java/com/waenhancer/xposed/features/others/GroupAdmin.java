package com.waenhancer.xposed.features.others;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class GroupAdmin extends Feature {

    public GroupAdmin(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        boolean enabled = prefs.getBoolean("admin_grp", false)
            || prefs.getBoolean("show_admin_group_icon", false)
            || (!prefs.contains("admin_grp") && !prefs.contains("show_admin_group_icon"));
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
                    Object targetObj = param.thisObject;
                    Object[] args = param.args;

                    if (Utils.DEBUG) XposedBridge.log("[GroupAdmin] Hook triggered, target: " + (targetObj != null ? targetObj.getClass().getSimpleName() : "null"));

                    // Get View from various sources
                    View rowView = null;
                    if (targetObj instanceof View) {
                        rowView = (View) targetObj;
                    }
                    // Look for View in args - might be a holder object
                    if (rowView == null && args != null) {
                        for (Object arg : args) {
                            if (arg instanceof View) {
                                rowView = (View) arg;
                                break;
                            }
                            // Check if it's a holder object with a view field
                            if (arg != null) {
                                View v = extractViewFromObject(arg);
                                if (v != null) {
                                    rowView = v;
                                    break;
                                }
                            }
                        }
                    }
                    // Try to extract view from target object
                    if (rowView == null && targetObj != null) {
                        rowView = extractViewFromObject(targetObj);
                    }
                    if (rowView == null) {
                        if (Utils.DEBUG) XposedBridge.log("[GroupAdmin] No view found");
                        return;
                    }

                    if (Utils.DEBUG) XposedBridge.log("[GroupAdmin] View found: " + rowView.getClass().getSimpleName());

                    // Get FMessage
                    Object fMessageObj = null;
                    if (targetObj != null) {
                        try {
                            var method = XposedHelpers.findMethodExactIfExists(targetObj.getClass(), "getFMessage");
                            if (method != null) {
                                fMessageObj = method.invoke(targetObj);
                            }
                        } catch (Throwable ignored) {}
                    }
                    if (fMessageObj == null && args != null) {
                        for (Object arg : args) {
                            if (arg != null && fMessageClass.isAssignableFrom(arg.getClass())) {
                                fMessageObj = arg;
                                break;
                            }
                        }
                    }

                    if (fMessageObj == null) {
                        if (Utils.DEBUG) XposedBridge.log("[GroupAdmin] No fMessage found");
                        return;
                    }

                    if (fMessageObj == null) return;

                    var fMessage = new FMessageWpp(fMessageObj);
                    var userJid = fMessage.getUserJid();
                    if (userJid == null || userJid.userJid == null) return;

                    var chatCurrentJid = resolveGroupJid(fMessage);
                    if (chatCurrentJid == null || !chatCurrentJid.isGroup()) {
                        if (Utils.DEBUG) XposedBridge.log("[GroupAdmin] Not a group message");
                        return;
                    }

                    if (Utils.DEBUG) XposedBridge.log("[GroupAdmin] Processing group message from: " + userJid.userJid);

                    // Try to find GroupParticipantManager instance
                    Object manager = null;
                    Class<?> managerClass = grpcheckAdmin.getDeclaringClass();

                    // Search in the target object fields
                    try {
                        Field managerField = ReflectionUtils.findFieldUsingFilterIfExists(targetObj.getClass(), f -> managerClass.isAssignableFrom(f.getType()));
                        if (managerField != null) {
                            manager = managerField.get(targetObj);
                        }
                    } catch (Throwable ignored) {}

                    // Search in Activity context
                    if (manager == null) {
                        try {
                            Context ctx = rowView.getContext();
                            while (ctx instanceof android.content.ContextWrapper && !(ctx instanceof android.app.Activity)) {
                                ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
                            }
                            if (ctx instanceof android.app.Activity) {
                                Field f = ReflectionUtils.findFieldUsingFilterIfExists(ctx.getClass(), field -> managerClass.isAssignableFrom(field.getType()));
                                if (f != null) {
                                    manager = f.get(ctx);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }

                    if (manager == null && args != null) {
                        for (Object arg : args) {
                            if (arg != null && managerClass.isAssignableFrom(arg.getClass())) {
                                manager = arg;
                                break;
                            }
                        }
                    }

                    if (manager == null) {
                        if (Utils.DEBUG) XposedBridge.log("[GroupAdmin] No manager found");
                        return;
                    }

                    if (Utils.DEBUG) XposedBridge.log("[GroupAdmin] Manager found: " + manager.getClass().getSimpleName());

                    Object jidGrp;
                    String groupRawJid = chatCurrentJid.getPhoneRawString();
                    if (Modifier.isStatic(jidFactory.getModifiers())) {
                        jidGrp = jidFactory.invoke(null, groupRawJid);
                    } else {
                        jidGrp = XposedHelpers.callStaticMethod(jidFactory.getDeclaringClass(), "A03", groupRawJid);
                    }
                    
                    if (jidGrp == null) return;

                    boolean isAdmin = false;

                    // Try with userJid first
                    var result = grpcheckAdmin.invoke(manager, jidGrp, userJid.userJid);
                    isAdmin = (result instanceof Boolean && (Boolean) result);

                    // Also try with phoneJid if different (for non-saved contacts)
                    if (!isAdmin && userJid.phoneJid != null && !userJid.phoneJid.equals(userJid.userJid)) {
                        Object res = grpcheckAdmin.invoke(manager, jidGrp, userJid.phoneJid);
                        isAdmin = (res instanceof Boolean && (Boolean) res);
                    }

                    TextView iconAdmin;
                    View messageBubble = null;

                    // Find message bubble (the parent container with message content)
                    ViewGroup parent = (ViewGroup) rowView.getParent();
                    if (parent != null) {
                        for (int i = 0; i < parent.getChildCount(); i++) {
                            View child = parent.getChildAt(i);
                            // Message bubble usually has specific resource IDs or patterns
                            if (child.getId() == Utils.getID("bubble_row", "id") ||
                                child.getId() == Utils.getID("message_bubble", "id") ||
                                child.getId() == Utils.getID("media_bubble", "id")) {
                                messageBubble = child;
                                break;
                            }
                        }
                    }

                    if (messageBubble == null) {
                        messageBubble = rowView.findViewById(Utils.getID("bubble_row", "id"));
                    }
                    if (messageBubble == null) {
                        messageBubble = rowView.findViewById(Utils.getID("bubble", "id"));
                    }
                    if (messageBubble == null) {
                        messageBubble = rowView;
                    }

                    // Try to find existing badge or create new one
                    iconAdmin = messageBubble.findViewById(0x7fff0010);
                    if (iconAdmin == null) {
                        iconAdmin = new TextView(rowView.getContext());
                        iconAdmin.setId(0x7fff0010);
                        iconAdmin.setTextSize(9);

                        String adminEmoji = prefs.getString("admin_emoji", "👑");
                        if (adminEmoji == null || adminEmoji.isEmpty()) {
                            adminEmoji = "👑";
                        }
                        iconAdmin.setText(adminEmoji);

                        // Create card-style background
                        GradientDrawable bg = new GradientDrawable();
                        bg.setShape(GradientDrawable.RECTANGLE);
                        bg.setCornerRadius(Utils.dipToPixels(8));
                        bg.setColor(Color.parseColor("#E040FB")); // Purple admin badge
                        iconAdmin.setBackground(bg);
                        iconAdmin.setTextColor(Color.WHITE);
                        iconAdmin.setGravity(Gravity.CENTER);

                        // Find a proper container to add the badge to
                        ViewGroup container = null;
                        if (messageBubble instanceof ViewGroup) {
                            container = (ViewGroup) messageBubble;
                        } else if (rowView instanceof ViewGroup) {
                            container = (ViewGroup) rowView;
                        }

                        if (container != null) {
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                Utils.dipToPixels(16),
                                Utils.dipToPixels(16)
                            );
                            params.gravity = Gravity.END | Gravity.TOP;
                            params.topMargin = Utils.dipToPixels(-4);
                            params.rightMargin = Utils.dipToPixels(2);
                            params.bottomMargin = Utils.dipToPixels(12);
                            iconAdmin.setLayoutParams(params);
                            container.addView(iconAdmin);
                        }
                    }
                    iconAdmin.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                } catch (Throwable t) {
                    XposedBridge.log("GroupAdmin HOOK ERROR: " + t.getMessage());
                }
            }
        };

        Set<Method> bindMethods = collectBindCandidates(grpAdmin1, fMessageClass);
        if (conversationRowClass != null) {
            bindMethods.addAll(collectBindCandidatesFromClass(conversationRowClass, fMessageClass));
        }
        for (Method method : bindMethods) {
            XposedBridge.hookMethod(method, hooked);
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

                if (looksLikeBind) {
                    method.setAccessible(true);
                    result.add(method);
                }
            }
            cursor = cursor.getSuperclass();
        }
        return result;
    }

    private ViewGroup findNameContainer(@NonNull View root) {
        if (root instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) root;
            for (int i = 0; i < layout.getChildCount(); i++) {
                if (layout.getChildAt(i) instanceof TextView) {
                    return layout;
                }
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                var child = vg.getChildAt(i);
                var found = findNameContainer(child);
                if (found != null) return (ViewGroup) found;
            }
        }
        return null;
    }

    private View extractViewFromObject(Object holder) {
        if (holder == null) return null;
        if (holder instanceof View) return (View) holder;
        Class<?> cursor = holder.getClass();
        while (cursor != null && cursor != Object.class) {
            for (Field field : cursor.getDeclaredFields()) {
                if (!View.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(holder);
                    if (value instanceof View) {
                        return (View) value;
                    }
                } catch (Throwable ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    private FMessageWpp.UserJid resolveGroupJid(@NonNull FMessageWpp fMessage) {
        try {
            var key = fMessage.getKey();
            if (key != null && key.remoteJid != null && key.remoteJid.isGroup()) {
                return key.remoteJid;
            }
        } catch (Throwable ignored) {
        }

        try {
            var currentJid = WppCore.getCurrentUserJid();
            if (currentJid != null && currentJid.isGroup()) {
                return currentJid;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GroupAdmin";
    }
}
