# PolyGo+ Production ProGuard Rules

# 1. Keep Data Store Records (Required for JSON parsing)
-keep class com.poliku.polygoplus.data.AppDataStore$* { *; }

# 2. Keep Glide and its models
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-dontwarn com.bumptech.glide.**

# 3. Keep ViewModels (to prevent R8 from stripping them)
-keep class * extends androidx.lifecycle.ViewModel { *; }

# 4. Maintain line numbers for easier debugging of crashes
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
