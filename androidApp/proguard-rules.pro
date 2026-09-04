# 仅保留 Android 入口与反射所需的序列化元数据，避免整包宽泛保留。
-keep class com.novelstudio.app.android.MainActivity { *; }
-keep class com.novelstudio.app.android.StudioApplication { *; }
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation class * {
    @kotlinx.serialization.Serializable <fields>;
}
