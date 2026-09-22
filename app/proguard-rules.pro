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

# Hilt's generated LazyClassKey maps require each ViewModel to retain a distinct runtime class.
# R8 otherwise horizontally merges the structurally similar media ViewModels, rewriting multiple
# map keys to the same class name and crashing the minified release at startup.
-keep class com.blackandblue.justshare.presentation.** extends androidx.lifecycle.ViewModel { *; }
