# Retrofit
-keepattributes Signature, InnerClasses
-keepattributes Exceptions

# Gson
-keep class com.eta.aigateway.data.model.** { *; }

# OkHttp
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
