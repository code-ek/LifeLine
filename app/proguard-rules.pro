# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
-keep class com.lifeline.app.protocol.** { *; }
-keep class com.lifeline.app.crypto.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# Keep SecureIdentityStateManager from being obfuscated to prevent reflection issues
-keep class com.lifeline.app.identity.SecureIdentityStateManager {
    private android.content.SharedPreferences prefs;
    *;
}

# Keep all classes that might use reflection
-keep class com.lifeline.app.identity.** { *; }

# Fix for AbstractMethodError on API < 29 where LocationListener methods are abstract
-keepclassmembers class * implements android.location.LocationListener {
    public <methods>;
}
