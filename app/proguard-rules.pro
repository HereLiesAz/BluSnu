# Keep Room entities and DAOs
-keep class com.hereliesaz.blusnu.data.TargetDevice { *; }
-keep class com.hereliesaz.blusnu.data.SavedSession { *; }
-keep class com.hereliesaz.blusnu.data.AttackChainTemplate { *; }
-keep class com.hereliesaz.blusnu.data.Vulnerability { *; }
-keep class com.hereliesaz.blusnu.data.Converters { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * implements androidx.room.dao.Dao { *; }

# Keep AttackNode implementations for Gson polymorphic deserialization
-keep class com.hereliesaz.blusnu.ui.attackchaining.nodes.** { *; }
-keep class com.hereliesaz.blusnu.ui.attackchaining.AttackChainingState { *; }

# Gson: keep classes used with TypeToken / fromJson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Ktor client internals
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep kotlinx.serialization
-keepattributes RuntimeVisibleAnnotations
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
