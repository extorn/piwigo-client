# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable,Signature

-keep public class com.google.android.gms.** { public *; }
-dontwarn com.google.android.gms.**

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile