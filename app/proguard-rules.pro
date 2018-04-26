# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/gareth/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it. (Signature)

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable,Signature

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

#-keep class okio.** { *; }
#-dontwarn com.squareup.okhttp.internal.okio.DeflaterSink

-keep class com.squareup.picasso.** { *; }
#-dontwarn com.squareup.picasso.**

-keep class com.commonsware.cwac.merge.** { *; }
#-dontwarn com.commonsware.cwac.merge.**

-keep class delit.piwigoclient.ui.events.**

-keep class delit.piwigoclient.model.piwigo.CategoryItem

-keep class com.google.android.exoplayer2.** { public *; }
#-dontwarn com.google.android.exoplayer2.**

-keep class com.google.android.gms.** { public *; }
#-dontwarn com.google.android.gms.**

-keep class delit.piwigoclient.ui.common.SlidingTabLayout* { public *; }

-keep class com.google.ads.** { public *; }

-keep class com.ortiz.touch.TouchImageView* { public *; }

-keep class com.loopj.android.http.SerializableCookie { *; }

-keep class cz.msebera.android.httpclient.cookie.Cookie { *; }

-keep class com.google.gson.stream.** { *; }

-keep class com.google.gson.Json* { *; }

# Prevent proguard from stripping interface information from TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Application classes that will be serialized/deserialized over Gson
-keep class delit.piwigoclient.model.piwigo.PiwigoJsonResponse { *; }

-keepclassmembers class com.google.android.gms.dynamite.descriptors.com.google.android.gms.flags.ModuleDescriptor {
    java.lang.String MODULE_ID;
    int MODULE_VERSION;
}
-keepclassmembers class com.google.android.gms.dynamite.descriptors.com.google.android.gms.ads.dynamite.ModuleDescriptor {
    java.lang.String MODULE_ID;
    int MODULE_VERSION;
}

-keepclassmembers class com.google.android.gms.dynamite.DynamiteModule$DynamiteLoaderClassLoader { java.lang.ClassLoader sClassLoader; }

# For using GSON @Expose annotation
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Only required if you use AsyncExecutor
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}

-keep class **.R

-keep class **.R$* {
     <fields>;
}

-keep class delit.piwigoclient.ui.Constants

-keep class delit.piwigoclient.business.video.CachedContent

# We're not using the okhttp library at the moment so we don't need it on the classpath.
-dontwarn com.squareup.okhttp.**

# Gson specific classes
-dontwarn sun.misc.**

#-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement