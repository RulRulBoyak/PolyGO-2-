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

# 5. Retrofit/Gson model classes - keep fields for JSON serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep,allowshrinking,allowobfuscation interface com.poliku.polygoplus.api.PolyGoApi
-keepclassmembers,allowshrinking,allowobfuscation interface com.poliku.polygoplus.api.PolyGoApi {
    @retrofit2.http.* <methods>;
}
-keep class com.poliku.polygoplus.api.PolyGoApi$* { *; }
-keep class com.poliku.polygoplus.api.model.* { *; }
-keep class com.poliku.polygoplus.data.PolyGoRepository { *; }

# Retrofit reflection
-keepattributes InnerClasses,EnclosingMethod
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# 6. Room - keep entities, DAOs, and database
-keep class com.poliku.polygoplus.data.local.PolyGoDatabase { *; }
-keep class com.poliku.polygoplus.data.local.entity.* { *; }
-keep class com.poliku.polygoplus.data.local.dao.* { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# 7. ML Kit barcode scanning
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# 8. CameraX
-dontwarn androidx.camera.**
-keep class androidx.camera.** { *; }

# 9. Hilt / Dagger
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**

# 10. Gson generic type safety
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# 11. Ktor (required by the Google Generative AI SDK - must stay on Ktor 2.x)
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**
-keep class com.google.common.util.concurrent.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
