# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable,Signature

-keep class com.google.firebase.crashlytics.** { *; } # faster builds - don't obfuscate crashlytics
-dontwarn com.google.firebase.crashlytics.** # faster builds - don't obfuscate crashlytics

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile