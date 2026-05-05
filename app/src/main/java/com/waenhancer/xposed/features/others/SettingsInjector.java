package com.waenhancer.xposed.features.others;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Ultimate Settings Injector: Uses multiple strategies to find and inject into WA Settings.
 * Injects BOTH a Tile (row) and a Toolbar menu item as a backup.
 */
public class SettingsInjector extends Feature {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Integer> processedActivities = new HashSet<>();

    public SettingsInjector(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                String entryPoint = getSafeString("open_wae", "1");
                
                // Log for debugging sync issues
                XposedBridge.log("[WaEnhancer] SettingsInjector: Active in " + activity.getClass().getSimpleName() + ", entryPoint=" + entryPoint);
                
                if (!"2".equals(entryPoint)) return;

                int hash = System.identityHashCode(activity);
                if (processedActivities.contains(hash)) return;

                // Run multiple attempts to catch dynamic loading
                mainHandler.postDelayed(() -> performInjection(activity), 500);
                mainHandler.postDelayed(() -> performInjection(activity), 1500);
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                processedActivities.remove(System.identityHashCode(param.thisObject));
            }
        });
    }

    private void performInjection(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        
        // 1. Identification
        boolean isSettings = isSettingsActivity(activity, decor);
        if (!isSettings) return;

        XposedBridge.log("[WaEnhancer] SettingsInjector: Confirmed Settings Activity: " + activity.getClass().getSimpleName());

        // 2. Tile Injection (In RecyclerView)
        injectTile(decor, activity);

        // 3. Toolbar Injection (Menu Item)
        injectToolbarMenu(decor, activity);
        
        processedActivities.add(System.identityHashCode(activity));
    }

    private boolean isSettingsActivity(Activity activity, ViewGroup decor) {
        // Class check
        try {
            Class<?> settingsClass = Unobfuscator.loadSettingsActivityClass(classLoader);
            if (settingsClass != null && settingsClass.isInstance(activity)) return true;
        } catch (Throwable ignored) {}

        // Title check
        String title = getToolbarTitle(decor);
        if (title != null && (title.equalsIgnoreCase("Settings") || title.equalsIgnoreCase("Configurações") || title.equalsIgnoreCase("Cuenta"))) {
            return true;
        }

        // Marker check
        if (hasSettingsMarkers(decor)) return true;

        return false;
    }

    private String getToolbarTitle(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof TextView) {
                String text = ((TextView) v).getText().toString();
                if (text.length() > 2 && text.length() < 20) {
                    // Toolbar titles are usually fairly short but descriptive
                    if (text.equals("Settings") || text.equals("Configurações") || text.equals("Configuracion")) return text;
                }
            } else if (v instanceof ViewGroup) {
                String result = getToolbarTitle((ViewGroup) v);
                if (result != null) return result;
            }
        }
        return null;
    }

    private boolean hasSettingsMarkers(ViewGroup group) {
        String[] critical = {"Account", "Privacy", "Notifications", "Storage", "Help"};
        int count = 0;
        return scanForMarkers(group, critical, count) >= 2;
    }

    private int scanForMarkers(ViewGroup group, String[] markers, int count) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                String text = ((TextView) child).getText().toString();
                for (String m : markers) {
                    if (text.equalsIgnoreCase(m)) {
                        count++;
                        if (count >= 2) return count;
                    }
                }
            } else if (child instanceof ViewGroup) {
                count = scanForMarkers((ViewGroup) child, markers, count);
                if (count >= 2) return count;
            }
        }
        return count;
    }

    private void injectTile(ViewGroup decor, Activity activity) {
        ViewGroup rv = findRecyclerView(decor);
        if (rv == null) {
            XposedBridge.log("[WaEnhancer] SettingsInjector: Could not find RecyclerView for Tile injection");
            return;
        }

        try {
            Object adapter = XposedHelpers.callMethod(rv, "getAdapter");
            if (adapter == null) return;

            // Look for header methods
            java.lang.reflect.Method addHeader = findMethod(adapter.getClass(), "addHeader", View.class);
            if (addHeader == null) addHeader = findMethod(adapter.getClass(), "addSearchBar", View.class);
            if (addHeader == null) addHeader = findMethod(adapter.getClass(), "A0R", View.class); // Obfuscated common name

            if (addHeader != null) {
                View tile = createTile(activity);
                addHeader.setAccessible(true);
                addHeader.invoke(adapter, tile);
                XposedBridge.log("[WaEnhancer] SettingsInjector: Tile injected via Adapter." + addHeader.getName());
            } else {
                // Direct Layout Injection (Top of RV)
                ViewGroup parent = (ViewGroup) rv.getParent();
                if (parent != null && !hasChildWithText(parent, "WaEnhancerX Settings")) {
                    View tile = createTile(activity);
                    int index = parent.indexOfChild(rv);
                    parent.addView(tile, index);
                    XposedBridge.log("[WaEnhancer] SettingsInjector: Tile injected via Layout (index " + index + ")");
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancer] SettingsInjector: Tile error: " + t.getMessage());
        }
    }

    private void injectToolbarMenu(ViewGroup decor, Activity activity) {
        try {
            ViewGroup toolbar = findToolbar(decor);
            if (toolbar == null) return;

            // Access the menu via reflection
            Menu menu = (Menu) XposedHelpers.callMethod(toolbar, "getMenu");
            if (menu != null && menu.findItem(9999) == null) {
                String title = "WaEnhancerX Settings";
                try {
                    String moduleTitle = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.waenhancer_settings);
                    if (moduleTitle != null && !moduleTitle.isEmpty()) {
                        title = moduleTitle;
                    }
                } catch (Throwable ignored) {}
                
                var item = menu.add(0, 9999, 0, title);
                var icon = DesignUtils.getDrawableByName("ic_settings");
                if (icon != null) {
                    icon.setTint(0xff8696a0);
                    item.setIcon(icon);
                }
                item.setShowAsAction(2); // SHOW_AS_ACTION_IF_ROOM
                item.setOnMenuItemClickListener(it -> {
                    MenuHome.showWaeSettingsDialog(activity);
                    return true;
                });
                XposedBridge.log("[WaEnhancer] SettingsInjector: Toolbar menu item injected.");
            }
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancer] SettingsInjector: Toolbar error: " + t.getMessage());
        }
    }

    private ViewGroup findRecyclerView(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v.getClass().getName().contains("RecyclerView")) return (ViewGroup) v;
            if (v instanceof ViewGroup) {
                ViewGroup res = findRecyclerView((ViewGroup) v);
                if (res != null) return res;
            }
        }
        return null;
    }

    private ViewGroup findToolbar(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            String name = v.getClass().getName();
            if (name.contains("Toolbar") || name.contains("TopBar")) return (ViewGroup) v;
            if (v instanceof ViewGroup) {
                ViewGroup res = findToolbar((ViewGroup) v);
                if (res != null) return res;
            }
        }
        return null;
    }

    private java.lang.reflect.Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException e) {
                // Search for ANY method with matching params if name check fails for obfuscation
                if (name.length() <= 3) {
                    for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                        if (m.getParameterCount() == params.length && java.util.Arrays.equals(m.getParameterTypes(), params)) {
                            return m;
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private boolean hasChildWithText(ViewGroup group, String text) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof TextView && ((TextView) v).getText().toString().equals(text)) return true;
            if (v instanceof ViewGroup && hasChildWithText((ViewGroup) v, text)) return true;
        }
        return false;
    }

    private View createTile(Activity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(Utils.dipToPixels(16), Utils.dipToPixels(16), Utils.dipToPixels(16), Utils.dipToPixels(16));
        content.setGravity(android.view.Gravity.CENTER_VERTICAL);
        content.setBackground(DesignUtils.getSelectableItemBackground(activity));
        content.setOnClickListener(v -> MenuHome.showWaeSettingsDialog(activity));

        android.widget.ImageView icon = new android.widget.ImageView(activity);
        var iconDraw = DesignUtils.getDrawableByName("ic_settings");
        if (iconDraw != null) {
            iconDraw.setTint(0xff8696a0);
            icon.setImageDrawable(iconDraw);
        }
        var iconParams = new LinearLayout.LayoutParams(Utils.dipToPixels(24), Utils.dipToPixels(24));
        iconParams.rightMargin = Utils.dipToPixels(32);
        icon.setLayoutParams(iconParams);

        TextView textView = new TextView(activity);
        String title = "WaEnhancerX Settings";
        try {
            String moduleTitle = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.waenhancer_settings);
            if (moduleTitle != null && !moduleTitle.isEmpty()) {
                title = moduleTitle;
            }
        } catch (Throwable ignored) {}
        textView.setText(title);
        textView.setTextSize(16);
        textView.setTextColor(DesignUtils.getPrimaryTextColor());

        content.addView(icon);
        content.addView(textView);
        row.addView(content);

        View divider = new View(activity);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Utils.dipToPixels(1)));
        divider.setBackgroundColor(0x338696a0);
        row.addView(divider);

        return row;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Settings Injector";
    }
}
