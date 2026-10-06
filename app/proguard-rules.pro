# Keep Poweramp receiver + plugin entry points; keep jaudiotagger codecs.
-keep class org.lyrictag.companion.poweramp.** { *; }
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
