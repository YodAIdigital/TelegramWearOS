# TDLib JNI bridge — native code instantiates these classes reflectively.
-keep class dev.g000sha256.tdl.** { *; }
-keep class org.drinkless.** { *; }

# Coroutines debug metadata
-dontwarn kotlinx.coroutines.debug.**
