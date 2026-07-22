-keep class com.aquarium.neon.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn java.lang.invoke.**