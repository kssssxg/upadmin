# Keep MNN LLM JNI classes
-keep class com.me.chat.ai.up.admin.mnn.MNNLLMEngine { *; }
-keep class com.me.chat.ai.up.admin.mnn.MNNLLMEngine$TokenCallback { *; }
-keep class com.me.chat.ai.up.admin.bridge.AppJsBridge { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
