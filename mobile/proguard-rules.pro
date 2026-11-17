# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.omamoripet.shared.**$$serializer { *; }
-keepclassmembers class com.omamoripet.shared.** {
    *** Companion;
}
-keepclasseswithmembers class com.omamoripet.shared.** {
    kotlinx.serialization.KSerializer serializer(...);
}
