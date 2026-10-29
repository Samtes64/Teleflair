# Flutter Proguard Rules
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }

# Teleboard Native Services & Keyboard IME
-keep class com.teleboard.teleboard.** { *; }
-keepclassmembers class com.teleboard.teleboard.** { *; }

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }

# SQLite FFI
-keep class org.sqlite.** { *; }

# Keep generic signatures and annotations for reflection
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Suppress Play Store deferred component warnings
-dontwarn com.google.android.play.core.**
