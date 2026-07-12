# Standard OkHttp/Okio R8 rules (recommended by the OkHttp project) to
# silence "missing class" warnings from optional platform integrations we
# don't use (Conscrypt, BouncyCastle, OpenJSSE).
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepattributes Signature
-keepattributes *Annotation*
