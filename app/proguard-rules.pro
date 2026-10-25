# ========== 聚影 TV R8 规则 ==========

# Gson 反射解析的数据模型（字段名即 JSON key，必须保留）
-keep class com.juying.tv.data.model.** { *; }
-keep class com.juying.tv.data.source.SourceEntry { *; }
-keep class com.juying.tv.data.source.SourceHealth { *; }

# Gson TypeToken 依赖泛型签名
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# Room 实体（字段名映射数据库列）
-keep class com.juying.tv.data.local.*Entity { *; }

# WebView JS 交互（防万一）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 压缩优化
-optimizationpasses 5
-dontpreverify
