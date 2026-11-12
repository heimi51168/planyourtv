# The JavaScript interface is called by name from shell_bridge.js. R8 keeps
# @JavascriptInterface methods on recent AGP, but the class is also constructed
# reflectively by the WebView bridge, so keep it explicit.
-keepclassmembers class app.orchard.tv.parent.MainActivity$ShellBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class app.orchard.tv.parent.MainActivity$ShellBridge { *; }

# ZXing embedded is referenced from its own manifest (CaptureActivity) and its
# camera code uses reflection on some older devices.
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
