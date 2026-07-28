# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\tools\adt-bundle-windows-x86_64-20131030\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# We use Reflection on the builder to avoid creating too many objects
 -keep class net.objecthunter.exp4j.ExpressionBuilder**
 -keepclassmembers class net.objecthunter.exp4j.ExpressionBuilder** {
    *;
 }
# Option screens
 -keep class net.kdt.pojavlaunch.prefs.screens.** { *; }

# Preference XML and navigation resources instantiate these fragments by
# fully-qualified class name. Keep the complete classes because R8 cannot infer
# every reflective reference from Android resources.
-keep class net.kdt.pojavlaunch.fragments.** extends androidx.fragment.app.Fragment { *; }

# Android resources instantiate custom views and preferences by class name.
-keep,allowoptimization class net.kdt.pojavlaunch.** extends android.view.View {
    public <init>(...);
}
-keep,allowoptimization class com.kdt.mcgui.** extends android.view.View {
    public <init>(...);
}
-keep,allowoptimization class net.kdt.pojavlaunch.prefs.** extends androidx.preference.Preference {
    public <init>(...);
}

# Gson constructs these models and reads/writes their fields reflectively.
# Keeping only field names is insufficient: R8 class merging previously turned
# ModrinthIndex into an abstract optimized type and broke workspace downloads.
-keep class net.kdt.pojavlaunch.modloaders.modpacks.models.** { *; }
-keep class net.kdt.pojavlaunch.value.** { *; }
-keep class net.kdt.pojavlaunch.JAssets { *; }
-keep class net.kdt.pojavlaunch.JMinecraftVersionList { *; }
-keep class net.kdt.pojavlaunch.JMinecraftVersionList$* { *; }
-keep class net.kdt.pojavlaunch.modloaders.FabricVersion { *; }
-keep class net.kdt.pojavlaunch.modloaders.FabricVersion$* { *; }
-keep class net.kdt.pojavlaunch.modloaders.BTAUtils$BTAVersionsManifest { *; }
-keep class net.kdt.pojavlaunch.customcontrols.** { *; }

# Native entry points are resolved by their Java class and method names.
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
}
-keep class org.libsdl.app.** { *; }
-keep class org.lwjgl.glfw.CallbackBridge { *; }
-keep class com.oracle.dalvik.VMLauncher { *; }
-keep class net.kdt.pojavlaunch.Logger { *; }
-keep class net.kdt.pojavlaunch.AWTInputBridge { *; }
-keep class net.kdt.pojavlaunch.utils.JREUtils { *; }
-keep class net.kdt.pojavlaunch.Tools$SDL { *; }

# SDL loads ReLinker and its listener through reflection when it is available.
-keep class com.getkeepsafe.relinker.** { *; }

# Gson and reflective model handling need generic signatures, annotations and
# nested-class metadata to survive optimized release builds.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

