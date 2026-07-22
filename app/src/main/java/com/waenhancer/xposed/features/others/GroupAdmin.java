package com.waenhancer.xposed.features.others;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.app.Activity;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;

public class GroupAdmin extends Feature {

    private static final int BADGE_ID = 0x7fff0010;

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

                    // Find View - skip if no valid target
                    View rowView = null;
                    if (targetObj instanceof View) {
                        rowView = (View) targetObj;
                    } else if (args != null) {
                        for (Object arg : args) {
                            if (arg instanceof View) {
                                rowView = (View) arg;
                                break;
                            }
                            if (arg != null) {
                                View v = extractViewFromObject(arg);
                                if (v != null) {
                                    rowView = v;
                                    break;
                                }
                            }
                        }
                    }
                    if (rowView == null) return;

                    // Quick check: if badge already exists, just update visibility
                    TextView existingBadge = rowView.findViewById(BADGE_ID);
                    boolean badgeExists = existingBadge != null;

                    // Get FMessage - skip if can't get
                    Object fMessageObj = null;
                    if (targetObj != null) {
                        try {
                            var method = XposedHelpers.findMethodExactIfExists(targetObj.getClass(), "getFMessage");
                            if (method != null) fMessageObj = method.invoke(targetObj);
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
                        // If badge exists, hide it since we can't determine admin status
                        if (badgeExists) existingBadge.setVisibility(View.GONE);
                        return;
                    }

                    var fMessage = new FMessageWpp(fMessageObj);
                    var userJid = fMessage.getUserJid();
                    if (userJid == null) {
                        if (badgeExists) existingBadge.setVisibility(View.GONE);
                        return;
                    }

                    var chatCurrentJid = resolveGroupJid(fMessage);
                    if (chatCurrentJid == null || !chatCurrentJid.isGroup()) {
                        if (badgeExists) existingBadge.setVisibility(View.GONE);
                        return;
                    }

                    // Try to find GroupParticipantManager
                    Object manager = null;
                    Class<?> managerClass = grpcheckAdmin.getDeclaringClass();

                    try {
                        Field managerField = ReflectionUtils.findFieldUsingFilterIfExists(targetObj.getClass(), f -> managerClass.isAssignableFrom(f.getType()));
                        if (managerField != null) manager = managerField.get(targetObj);
                    } catch (Throwable ignored) {}

                    if (manager == null && rowView.getContext() != null) {
                        try {
                            Context ctx = rowView.getContext();
                            while (ctx instanceof ContextWrapper && !(ctx instanceof Activity)) {
                                ctx = ((ContextWrapper) ctx).getBaseContext();
                            }
                            if (ctx instanceof Activity) {
                                Field f = ReflectionUtils.findFieldUsingFilterIfExists(ctx.getClass(), field -> managerClass.isAssignableFrom(field.getType()));
                                if (f != null) manager = f.get(ctx);
                            }
                        } catch (Throwable ignored) {}
                    }

                    if (manager == null) return;

                    // Build group JID
                    Object jidGrp;
                    String groupRawJid = chatCurrentJid.getPhoneRawString();
                    if (Modifier.isStatic(jidFactory.getModifiers())) {
                        jidGrp = jidFactory.invoke(null, groupRawJid);
                    } else {
                        jidGrp = XposedHelpers.callStaticMethod(jidFactory.getDeclaringClass(), "A03", groupRawJid);
                    }
                    if (jidGrp == null) return;

                    // Check admin status - try userJid first, then phoneJid
                    boolean isAdmin = false;

                    if (userJid.userJid != null) {
                        var result = grpcheckAdmin.invoke(manager, jidGrp, userJid.userJid);
                        if (result instanceof Boolean) isAdmin = (Boolean) result;
                    }

                    if (!isAdmin && userJid.phoneJid != null) {
                        if (userJid.userJid == null || !userJid.phoneJid.toString().equals(userJid.userJid.toString())) {
                            var result = grpcheckAdmin.invoke(manager, jidGrp, userJid.phoneJid);
                            if (result instanceof Boolean) isAdmin = (Boolean) result;
                        }
                    }

                    // If badge doesn't exist and user is admin, create it
                    // If badge exists, just update visibility
                    if (badgeExists) {
                        existingBadge.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                        return;
                    }

                    // Need to create badge only if admin
                    if (!isAdmin) return;

                    // Find the name TextView directly
                    TextView nameTextView = findNameTextView(rowView);
                    if (nameTextView == null) return;

                    String adminEmoji = prefs.getString("admin_emoji", "👑");
                    if (adminEmoji == null || adminEmoji.isEmpty()) adminEmoji = "👑";

                    // Check if already has prefix to avoid duplicate
                    CharSequence currentText = nameTextView.getText();
                    if (currentText != null && currentText.length() > 0 && currentText.charAt(0) == adminEmoji.charAt(0)) {
                        // Already has the indicator, just ensure it's visible
                        return;
                    }

                    // Prepend emoji to name text
                    nameTextView.setText(adminEmoji + " " + currentText);
                    return;
                } catch (Throwable t) {
                    XposedBridge.log("GroupAdmin Error: " + t.getMessage());
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

    private int getBubbleColor(View rowView) {
        // Default colors by theme
        int defaultLight = Color.parseColor("#E8E8E8");
        int defaultDark = Color.parseColor("#3B3B3B");

        try {
            Context ctx = rowView.getContext();
            boolean isDarkMode = (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

            int baseColor = isDarkMode ? defaultDark : defaultLight;

            // Try to get actual bubble color
            View bubbleView = rowView.findViewById(Utils.getID("bubble_row", "id"));
            if (bubbleView == null) bubbleView = rowView.findViewById(Utils.getID("bubble", "id"));
            if (bubbleView != null && bubbleView.getBackground() != null) {
                if (bubbleView.getBackground() instanceof GradientDrawable) {
                    var gd = (GradientDrawable) bubbleView.getBackground();
                    if (gd.getColor() != null) baseColor = gd.getColor().getDefaultColor();
                } else if (bubbleView.getBackground() instanceof ColorDrawable) {
                    baseColor = ((ColorDrawable) bubbleView.getBackground()).getColor();
                }
            }

            // Apply 90% opacity (230 alpha)
            return Color.argb(230, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
        } catch (Throwable ignored) {
            return Color.argb(230, Color.red(defaultLight), Color.green(defaultLight), Color.blue(defaultLight));
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

    private TextView findNameTextView(@NonNull View root) {
        // First try to find by resource ID
        int nameId = Utils.getID("name_in_group", "id");
        if (nameId != 0) {
            View found = root.findViewById(nameId);
            if (found instanceof TextView) {
                return (TextView) found;
            }
            // If it's a ViewGroup, search inside it
            if (found instanceof ViewGroup) {
                TextView tv = findTextViewInViewGroup((ViewGroup) found);
                if (tv != null) return tv;
            }
        }

        // Try ConversationRowParticipantHeaderMainView directly
        View headerView = root.findViewById(Utils.getID("participant_header_main", "id"));
        if (headerView == null) {
            // Try alternative ID names
            headerView = root.findViewById(Utils.getID("name_in_group", "id"));
        }
        if (headerView instanceof ViewGroup) {
            TextView tv = findTextViewInViewGroup((ViewGroup) headerView);
            if (tv != null) return tv;
        }

        // Search recursively for any TextView (including subclasses like WDSTextView)
        return findTextViewInViewGroupRecursive(root, 6);
    }

    private TextView findTextViewInViewGroup(ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                CharSequence text = tv.getText();
                if (text != null && text.length() > 0) {
                    String str = text.toString().trim();
                    // Skip timestamps, numbers only, etc.
                    if (str.length() > 1 && !str.matches("^\\d+$") && !str.matches("^\\d{1,2}:\\d{2}$") && !str.contains("AM") && !str.contains("PM") && !str.contains(":")) {
                        return tv;
                    }
                }
            }
            if (child instanceof ViewGroup) {
                TextView result = findTextViewInViewGroupRecursive(child, 3);
                if (result != null) return result;
            }
        }
        return null;
    }

    private TextView findTextViewInViewGroupRecursive(@NonNull View root, int depth) {
        if (depth <= 0) return null;

        if (root instanceof TextView) {
            TextView tv = (TextView) root;
            CharSequence text = tv.getText();
            if (text != null && text.length() > 0) {
                String str = text.toString().trim();
                // Skip timestamps, numbers only, etc.
                if (str.length() > 1 && !str.matches("^\\d+$") && !str.matches("^\\d{1,2}:\\d{2}$") && !str.contains("AM") && !str.contains("PM") && !str.contains(":")) {
                    return tv;
                }
            }
            return null;
        }

        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextView result = findTextViewInViewGroupRecursive(vg.getChildAt(i), depth - 1);
                if (result != null) return result;
            }
        }
        return null;
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