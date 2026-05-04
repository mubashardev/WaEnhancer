package com.waenhancer.xposed.utils;

import android.content.Context;
import android.content.res.Configuration;

public class ThemeUtils {

    public static boolean isNightMode(Context context) {
        if (context == null) return false;
        try {
            int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return uiMode == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getThemeBackgroundColor(Context context, boolean isDark) {
        return isDark ? 0xff0b141a : 0xffffffff;
    }

    public static int getThemeTextColorPrimary(Context context, boolean isDark) {
        return isDark ? 0xffffffff : 0xff000000;
    }

    public static int getThemeTextColorSecondary(Context context, boolean isDark) {
        return isDark ? 0xff8696a0 : 0xff667781;
    }

    public static int getThemeAccentColor(Context context) {
        return 0xff25d366; // WhatsApp Green
    }
}
