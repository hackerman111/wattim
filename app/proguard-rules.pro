# wattim Proguard Rules
-keepattributes *Annotation*
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
