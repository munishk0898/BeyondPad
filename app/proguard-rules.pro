# libadb finds Conscrypt by reflection (org.conscrypt.Conscrypt / OpenSSLProvider)
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
# spake2 is called from native code
-keep class io.github.muntashirakon.crypto.spake2.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
