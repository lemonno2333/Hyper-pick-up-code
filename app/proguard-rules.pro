# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# 保留行号信息（发布版崩溃堆栈可追溯源文件行号）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# PaddleOCR ncnn 库 - 仅保留 JNI 入口类
-keep class com.equationl.ncnnandroidppocr.NcnnOcr { *; }
-keep class com.equationl.ncnnandroidppocr.NcnnOcr$Companion { *; }

# ncnn 库 - 仅保留 JNI native 方法
-keep class org.ncnn.Ncnn { *; }
-keepclasseswithmembers class org.ncnn.** {
    native <methods>;
}
