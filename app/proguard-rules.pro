# kotlinx.serialization: keep generated serializers.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.buslive.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.buslive.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit interfaces (suspend functions use generic signatures).
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.buslive.app.data.api.** { *; }

# MapLibre uses JNI and reflection.
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
