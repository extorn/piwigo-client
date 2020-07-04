# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable,Signature

-keep class com.google.firebase.crashlytics.** { *; } # faster builds - don't obfuscate crashlytics
-dontwarn com.google.firebase.crashlytics.** # faster builds - don't obfuscate crashlytics

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

-keep class delit.libs.ui.view.SlidingTabLayout$TabColorizer { public *; }

# Allow customised serialization to work (all serializable classes must have serialVersionUID for this to be sufficient)
     # <init>(...);
-keepclassmembers class * implements java.io.Serializable {
     static final long serialVersionUID;
     private static final java.io.ObjectStreamField[] serialPersistentFields;
     !static !transient <fields>;
     !private <fields>;
     !private <methods>;
     private void writeObject(java.io.ObjectOutputStream);
     private void readObject(java.io.ObjectInputStream);
     java.lang.Object writeReplace();
     java.lang.Object readResolve();
}