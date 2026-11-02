# Room metadata and generated DAO access.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep class com.sdtech.studio.data.local.entity.** { *; }
-keep class com.sdtech.studio.data.local.dao.** { *; }

# Kotlin serialization models and generated serializers.
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class **$Companion { kotlinx.serialization.KSerializer serializer(...); }
-keep @kotlinx.serialization.Serializable class com.sdtech.studio.** { *; }

# Android entry points referenced by the manifest.
-keep class com.sdtech.studio.export.VideoExportService { *; }
-keep class com.sdtech.studio.SDStudioApplication { *; }
-keep class com.sdtech.studio.planner.PlannerReminderReceiver { *; }
