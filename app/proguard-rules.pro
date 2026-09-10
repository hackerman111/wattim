# Wattim R8 / Proguard Rules
# Invariant: preserve data models, Room database, platform entrypoints, and Compose runtime

# Keep attributes needed for Room and Kotlin reflection/serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database, DAOs, Entities, and Converters
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-keep class io.ronesec.android.data.entity.** { *; }
-keep class io.ronesec.android.data.dao.** { *; }
-dontwarn androidx.room.paging.**

# Domain Models (policy snapshots, settings, attempts, grants)
-keep class io.ronesec.domain.model.** { *; }
-keep class io.ronesec.domain.protection.** { *; }
-keep class io.ronesec.domain.policy.** { *; }
-keep class io.ronesec.domain.breathing.** { *; }

# Android Platform Entrypoints & Services
-keep class io.ronesec.android.WattimApplication { *; }
-keep class io.ronesec.android.ui.MainActivity { *; }
-keep class io.ronesec.android.ui.intervention.InterventionActivity { *; }
-keep class io.ronesec.android.platform.accessibility.AppMonitorService { *; }
-keep class io.ronesec.android.platform.system.FocusForegroundService { *; }
-keep class io.ronesec.android.platform.system.BootReceiver { *; }

# Compose Runtime
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Coroutines and Flow
-dontwarn kotlinx.coroutines.**
