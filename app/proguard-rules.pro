# Add project specific ProGuard rules here.
# Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Data Entities and Models
-keep class com.example.data.local.** { *; }
-keep class com.example.data.ai.** { *; }

# OkHttp / JSON
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Android Keystore & Crypto
-keepclassmembers class * implements java.security.KeyStore$Entry { *; }
