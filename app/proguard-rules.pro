# ProGuard rules for VYORA (Retrofit, Moshi, OkHttp, Media3)
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Domain & DTO models used with Moshi
-keep class com.vyorasound.app.domain.model.** { *; }
-keep class com.vyorasound.app.data.dto.** { *; }

# Retrofit interfaces
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclassmembers,allowshrinking,allowobfuscation interface com.vyorasound.app.network.** {
    @retrofit2.http.* <methods>;
}

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
