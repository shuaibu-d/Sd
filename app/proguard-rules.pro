# Project-specific ProGuard / R8 rules for SD Studio (com.sdtech.studio)
-keepattributes SourceFile,LineNumberTable

# Preserve Room database, entities, and DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class com.sdtech.studio.data.local.entity.** { *; }
-keep @androidx.room.Dao class com.sdtech.studio.data.local.dao.** { *; }

# Preserve Kotlinx Serialization classes and companions
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.sdtech.studio.**$$serializer {
    *** descriptor;
}
-keepclassmembers class com.sdtech.studio.** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepnames class <2>$$<3>
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Preserve enums stored by name in Room or serialized payloads
-keepclassmembers enum com.sdtech.studio.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
