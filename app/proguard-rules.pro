# Lucento release rules. Room, Navigation and kotlinx.serialization ship their own consumer rules.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.lucento.app.**$$serializer { *; }
-keepclassmembers class com.lucento.app.** {
    *** Companion;
}
