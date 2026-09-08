# kotlinx.serialization keeps its generated serializers by annotation; R8 needs
# to be told they are reachable even though nothing references them by name.
-keepclasseswithmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static <1>$Companion Companion;
    static **$* *;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.asura.finanzas.data.** {
    *;
}

# OkHttp ships optional integrations it guards with reflection.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
