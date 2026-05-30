# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# General Stability for R8
-keepattributes *Annotation*,Signature,InnerClasses,SourceFile,LineNumberTable

-dontwarn *

# Keep the critical Xposed loading entry point and all its members
-keep class com.waenhancer.WppXposed { *; }

# ── Pro module: aggressive obfuscation ───────────────────────────────────────
# Keep only what is strictly needed for reflection (Class.forName + constructor)
# and JNI (native method signatures must match the C++ side).
# Everything else in the pro package gets fully obfuscated.
-keep class com.waenhancer.pro.ProFeature {
    public <init>(java.lang.ClassLoader, android.content.SharedPreferences);
    native <methods>;
}

# Keep Native Security Bridge
-keepclasseswithmembernames class com.waenhancer.pro.utils.SecurityNative {
    native <methods>;
}
-keep class com.waenhancer.pro.utils.SecurityNative { *; }

# Keep Network and Crypto classes
-keep class com.waenhancer.xposed.utils.KeystoreHelper { *; }
-keep class com.waenhancer.xposed.utils.LicenseManager { *; }
-keep class com.waenhancer.pro.utils.ProStatusManager { *; }
-keep class com.waenhancer.pro.utils.ProConfig { *; }

# Keep the reflective constructors for all Feature plugins loaded dynamically at startup or lazily
-keep class * extends com.waenhancer.xposed.core.Feature {
    public <init>(java.lang.ClassLoader, android.content.SharedPreferences);
}

# Keep critical cross-app target Activities called via ComponentName string from WhatsApp process context
-keep class com.waenhancer.activities.ChangelogActivity { *; }
-keep class com.waenhancer.xposed.features.others.EmbeddedSettingsActivity { *; }

# Keep all IPC bridge AIDL interface, stub, and client classes intact to maintain communication stability
-keep class com.waenhancer.xposed.bridge.** { *; }

# Strip all debug info from pro classes — no source file, no line numbers
-assumenosideeffects class com.waenhancer.pro.** {
    void log(...);
}

# Obfuscate the dictionary for pro classes — use single-char names
-repackageclasses ''
-allowaccessmodification
-overloadaggressively

-renamesourcefileattribute ""

# Keep AndroidX libraries that are often accessed via reflection in Xposed environments
-keep class androidx.preference.** { *; }
-keep class androidx.fragment.** { *; }
-keep class androidx.appcompat.** { *; }
-keep class com.google.android.material.** { *; }

# Keep DexKit and other local libraries
-keep class io.luckypray.dexkit.** { *; }
-keep class org.luckypray.dexkit.** { *; }
-keep class com.waenhancer.xposed.utils.ResId** { *; }

# Keep Firebase classes accessed via reflection
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Support for Xposed libraries
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

-keep class io.github.libxposed.** { *; }

# Bouncy Castle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Support for jStyleParser — both the API (css) and the impl (csskit) packages must be kept.
# CSSFactory uses Class.forName("cz.vutbr.web.csskit.RuleFactoryImpl") at runtime, so R8
# must not rename or remove any class in the csskit package.
-keep class cz.vutbr.web.css.** { *; }
-keep class cz.vutbr.web.csskit.** { *; }
-keep class cz.vutbr.web.domassign.** { *; }
-keep class org.w3c.css.sac.** { *; }
-dontwarn cz.vutbr.web.**
-dontwarn org.w3c.css.sac.**

# OkHttp/Okio
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**