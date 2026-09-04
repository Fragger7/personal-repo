# Keep GSON generic signatures and annotations
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep Gson
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep our models
-keep class com.projectstrong.iptv.data.** { *; }
-keepclassmembers class com.projectstrong.iptv.data.** { *; }
-keep class com.projectstrong.iptv.network.** { *; }
-keepclassmembers class com.projectstrong.iptv.network.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
