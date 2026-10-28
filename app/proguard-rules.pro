# Glyph Matrix SDK（AIDL とリフレクション経由で呼ばれる）
-keep class com.nothing.ketchum.** { *; }
-keep class com.nothing.thirdparty.** { *; }
-dontwarn com.nothing.**

# 配布版では、動きを追うためのログ（v・d・i）を消す。警告とエラー（w・e）は残す
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
