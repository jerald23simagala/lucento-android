# Lucento release rules. Room, Navigation and kotlinx.serialization ship their own consumer rules.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.lucento.app.**$$serializer { *; }
-keepclassmembers class com.lucento.app.** {
    *** Companion;
}

# Lottie exposes optional okio-based loaders; okio is not on Lucento's classpath and those APIs are unused.
-dontwarn okio.**
