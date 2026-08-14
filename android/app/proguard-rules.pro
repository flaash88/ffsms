# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class cc.netwokx.ffsms.**$$serializer { *; }
-keepclassmembers class cc.netwokx.ffsms.** {
    *** Companion;
}
-keepclasseswithmembers class cc.netwokx.ffsms.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions

# libphonenumber Metadaten
-keep class com.google.i18n.phonenumbers.** { *; }
-dontwarn com.google.i18n.phonenumbers.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
