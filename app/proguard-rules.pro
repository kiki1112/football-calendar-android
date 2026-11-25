# Keep kotlinx.serialization & Retrofit model classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.footballcalendar.app.data.remote.**$$serializer { *; }
-keepclassmembers class com.footballcalendar.app.data.remote.** { *** Companion; }
