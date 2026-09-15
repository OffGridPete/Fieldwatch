# Sideload is non-debuggable. Shrink unused library code (Material icons,
# Compose tooling) but do not obfuscate or optimize — same R8 shape as
# the old debug field build, without adb run-as into app data.
-dontobfuscate
-dontoptimize

-keep class app.fieldwatch.** { *; }
-keepclassmembers class app.fieldwatch.** { *; }

-keepattributes RuntimeVisibleAnnotations, AnnotationDefault, InnerClasses, Signature, Exception, EnclosingMethod, SourceFile, LineNumberTable

# config.json / signature packs
-dontwarn kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

-dontwarn androidx.compose.**
