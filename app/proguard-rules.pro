# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# =============================================================================
# 1. AGGRESSIVE OPTIMIZATIONS & GENERAL R8 TUNING
# =============================================================================
-optimizationpasses 5
-allowaccessmodification
-overloadaggressively

# Keep essential JVM metadata attributes but discard everything else
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Strip all debug/logging logs entirely from production builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# =============================================================================
# 2. XPOSED & ENTRY POINT BOUNDARIES (MUST BE KEPT)
# =============================================================================

# Keep Xposed framework entry points intact so LSPosed can load your module
-keep public class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keep public class * implements de.robv.android.xposed.IXposedHookInitPackageResources { *; }
-keep public class * implements de.robv.android.xposed.IXposedHookZygoteInit { *; }

# Keep Xposed framework classes and references intact to prevent R8 from obfuscating them
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Keep Xposed loading entry point classes and their member signatures
-keep class com.waenhancer.WppXposed { *; }

# Keep the reflective constructors for all Feature modules loaded dynamically
-keep class * extends com.waenhancer.xposed.core.Feature {
    public <init>(java.lang.ClassLoader, android.content.SharedPreferences);
}

# Keep classes referenced by the Pro submodule to prevent NoClassDefFoundError/ClassNotFoundException at runtime
-keep class com.waenhancer.xposed.core.Feature { *; }
-keep class com.waenhancer.xposed.core.FeatureLoader { *; }
-keep class com.waenhancer.xposed.core.WppCore { *; }
-keep class com.waenhancer.xposed.core.devkit.Unobfuscator { *; }
-keep class com.waenhancer.xposed.core.components.FMessageWpp { *; }
-keep class com.waenhancer.xposed.core.components.FMessageWpp$* { *; }
-keep class com.waenhancer.xposed.features.privacy.CustomPrivacy { *; }
-keep class com.waenhancer.xposed.utils.Utils { *; }
-keep class com.waenhancer.xposed.utils.DesignUtils { *; }
-keep class com.waenhancer.xposed.utils.ReflectionUtils { *; }
-keep class com.waenhancer.xposed.features.listeners.ConversationItemListener { *; }
-keep class com.waenhancer.xposed.core.components.AlertDialogWpp { *; }
-keep class com.waenhancer.xposed.core.db.** { *; }
-keep class com.waenhancer.R { *; }
-keep class com.waenhancer.R$* { *; }
-keep class com.waenhancer.ui.helpers.BottomSheetHelper { *; }
-keep class com.waenhancer.utils.KeyboxValidator { *; }
-keep class com.waenhancer.utils.KeyboxValidator$* { *; }
-keep class com.waenhancer.App { *; }
-keep class com.waenhancer.BuildConfig { *; }
-keep class com.waenhancer.preference.SafeSharedPreferences { *; }
-keep class com.waenhancer.xposed.utils.XPrefManager { *; }
-keep class com.waenhancer.xposed.utils.XResManager { *; }
-keep class com.waenhancer.model.FilterItem { *; }

# Keep all classes and members in the pro package (except the obfuscated module) to prevent reflection and JNI issues in release mode
-keep class !com.waenhancer.pro.FileSizeSpooferPro,com.waenhancer.pro.** { *; }

# Keep only the reflection entry point of the obfuscated module so its internal logic, methods, and fields can be obfuscated
-keep class com.waenhancer.pro.FileSizeSpooferPro {
    public static void applyHooks(java.lang.ClassLoader, android.content.SharedPreferences);
}

# Keep all IPC bridge stub and AIDL classes intact to maintain process stability
-keep class com.waenhancer.xposed.bridge.** { *; }

# Keep the plugin API interfaces and support classes intact for helper/pro plugins
-keep class com.waex.api.** { *; }

# =============================================================================
# 3. LICENSING LAYER REFLECTION SAFETY (GAP CLOSED)
# =============================================================================
# Keep the names and reflective entrypoints of LicenseManager to ensure dynamic lookups succeed.
-keep class com.waenhancer.xposed.utils.LicenseManager {
    public static void makePrefsWorldReadable(android.content.Context);
    public static void silentCheck(android.content.Context);
    public <init>(...);
}

# =============================================================================
# 4. FLAT PACKAGING (REPACKAGING CLASSES TO MATCH COMPETITOR)
# =============================================================================
# Repackage all internal utility, library, and support classes 
# into a custom root package 'Z'. This makes decompiled APKs incredibly hard to read.
-repackageclasses 'Z'
-renamesourcefileattribute ""

# =============================================================================
# 5. SELECTIVE THIRD-PARTY KEEPS (LIBRARY GAP CLOSED)
# =============================================================================
# We don't keep entire library packages (OkHttp, material, etc.) anymore.
# We only target reflection targets.

# AndroidX preferences dynamically inflated from XML layouts
-keep class com.waenhancer.preference.** { *; }
-keep class * extends androidx.preference.Preference {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
# Keep reflective helper methods and preference fields in preference fragments to prevent reflection failure at runtime
-keepclassmembers class * extends androidx.preference.PreferenceFragmentCompat {
    protected *** mPrefs;
    private *** getDefaultSpooferXml();
    private *** updateKeyboxVerifySummary();
}


# Keep the CSS parser library (jStyleParser) intact to prevent NoSuchFieldException during reflective enum/field lookups
-keep class cz.vutbr.web.** { *; }
-keep class org.w3c.css.sac.** { *; }
-dontwarn cz.vutbr.web.**
-dontwarn org.w3c.css.sac.**

# Keep PreferenceManager and all its methods intact for Xposed preference mode hook and dynamic preference access
-keep class androidx.preference.PreferenceManager { *; }

# Keep DevKit Unobfuscator packages completely intact to preserve stack trace method signatures and avoid cache collisions
-keep class com.waenhancer.xposed.core.devkit.** { *; }

# DexKit and OkHttp warning suppression (R8 handles OKHttp automatically)
-keep class org.luckypray.dexkit.DexKitBridge {
    private long dexKitPtr;
    native <methods>;
}
-keep class org.luckypray.dexkit.DexKitBridge$Companion {
    native <methods>;
}
-dontwarn io.luckypray.dexkit.**
-dontwarn org.luckypray.dexkit.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**

# Firebase reflection safety
-dontwarn com.google.firebase.**

# Markwon and Commonmark warning suppression
-dontwarn org.commonmark.**
-dontwarn io.noties.markwon.**

# =============================================================================
# 6. FRAGMENT REFLECTION SAFETY (KEEP FRAGMENTS INSTANTIATED FROM XML)
# =============================================================================
# Keep all Fragment subclasses and their constructors intact because they are 
# referenced in preference XML files by their fully qualified names.
-keep public class * extends androidx.fragment.app.Fragment {
    public <init>();
}