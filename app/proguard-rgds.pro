# Keep the complete application boundary: Manifest components, Android callbacks,
# storage/lifecycle and diagnostic entry points retain names and members.
# This first bounded pass primarily removes unreachable runtime-library code.
-dontobfuscate
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class com.rgds.ultimate.shell.** { *; }

# Exact consumer rules shipped in the pinned, unmodified OkHttp 4.12.0 JAR.
# These refer to annotation-only dependencies and optional JVM TLS providers.
# Android's platform trust manager and host name verification remain enabled.
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
