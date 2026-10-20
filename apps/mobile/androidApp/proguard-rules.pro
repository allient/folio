# Folio release rules. R8 runs on release builds only (see build.gradle.kts).

# Readable stack traces in Play Console (upload mapping.txt, fastlane does it with the AAB).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Native libraries call back into these classes through JNI by name.
-keep class io.legere.pdfiumandroid.** { *; }
-keep class com.github.luben.zstd.** { *; }
-keep class androidx.ink.** { *; }

# PDFBox loads resources (fonts, CMaps, glyph lists) and some classes by name.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.**

# Readium uses reflection for its JS bridge and kotlinx-serialization models.
-keep class org.readium.** { *; }
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-dontwarn org.readium.**

# Optional dependencies referenced by libraries but not shipped.
-dontwarn org.slf4j.**
-dontwarn org.joda.convert.**

# ML Kit text recognition (bundled model): components are created by reflection
# (ComponentDiscovery needs the no-arg constructors) and fields are read from native code.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-dontwarn com.google.mlkit.**
