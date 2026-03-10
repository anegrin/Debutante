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
-dontobfuscate
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keep class io.github.debutante.** { *; }
-dontwarn kotlin.uuid.ExperimentalUuidApi
-dontwarn kotlin.uuid.Uuid$Companion
-dontwarn kotlin.uuid.Uuid

# Custom serializable
-keepclassmembers class * implements java.io.Serializable {
  static final long serialVersionUID;
  java.lang.Object writeReplace();
  java.lang.Object readResolve();
  private static final java.io.ObjectStreamField[] serialPersistentFields;
  private <fields>;
  public <fields>;
}

# Gson uses generic type information stored in a class file when working with
# fields. Proguard removes such information by default, keep it.
-keepattributes Signature
# This is also needed for R8 in compat mode since multiple optimizations will
# remove the generic signature such as class merging and argument removal.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# EventBus
-keepattributes *Annotation*