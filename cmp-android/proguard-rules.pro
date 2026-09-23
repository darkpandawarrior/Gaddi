# Gaddi — R8/ProGuard rules

# Keep Compose runtime internals that R8 would otherwise strip
-keepclassmembers class androidx.compose.** { *; }

# Kotlin serialization (used by multiplatform-settings and MatchSnapshot codecs)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Multiplatform-settings — keep codec implementations
-keep class com.russhwolf.settings.** { *; }

# Ktor (server module, not used on Android at runtime — but keep for future)
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Enum entries must survive shrinking so when/is dispatches work
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# Keep the game's top-level entry points (called reflectively by Android OS)
-keep class com.kursi.android.MainActivity { *; }

# Strip debug logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
