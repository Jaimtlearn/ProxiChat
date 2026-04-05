# ProxiChat ProGuard Rules

# Keep Gson serialized classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.proxichat.app.data.bluetooth.MessageProtocol$** { *; }
-keep class com.proxichat.app.domain.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
