# Keep Poweramp receiver + plugin entry points; keep jaudiotagger codecs.
-keep class org.lyrictag.companion.poweramp.** { *; }
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# kotlinx.serialization: keep serializers and model fields.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclasseswithmembernames class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }

# Room / Retrofit / OkHttp ship their own consumer rules; keep our DB + workers.
-keep class org.lyrictag.companion.db.** { *; }
-keep class org.lyrictag.companion.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
