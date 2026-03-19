# Proguard rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Gson
-keepattributes Signature
-keep class com.transcriber.app.data.** { *; }
-keep class com.transcriber.app.api.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
