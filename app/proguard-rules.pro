# libadb loads Conscrypt reflectively (org.conscrypt.Conscrypt / OpenSSLProvider)
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
-keep class io.github.muntashirakon.adb.** { *; }
-keep class io.github.muntashirakon.crypto.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-dontwarn javax.naming.**
