# Add project specific ProGuard rules here.
-keep class com.example.wechatautoreply.model.** { *; }
-keep class com.example.wechatautoreply.ai.LlamaClient$* { *; }
-keepclassmembers class * implements kotlinx.serialization.KSerializer { *; }
