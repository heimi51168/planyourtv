# The JavaScript interface is called by name from shell_bridge.js. R8 keeps
# @JavascriptInterface methods on recent AGP, but the class is also constructed
# reflectively by the WebView bridge, so keep it explicit.
-keepclassmembers class app.orchard.tv.parent.MainActivity$ShellBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class app.orchard.tv.parent.MainActivity$ShellBridge { *; }

# zxing-cpp's AAR carries its own consumer keep rule for the JNI bridge. Keep
# the wrapper here too so a future AAR update cannot silently strip it.
-keep class zxingcpp.** { *; }
-dontwarn zxingcpp.**
