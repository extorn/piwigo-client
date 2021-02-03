package delit.libs.core.util;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.BuildConfig;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.Arrays;
import java.util.Locale;

public class Logging {

    private static final String TAG = "Logging";
    private static boolean isDebug;
    private static FirebaseAnalytics firebaseAnalytics;

    public static void setDebug(boolean debug) {
        isDebug = debug;
    }


    /**
     * Final Argument should never be a throwable. Should use recordException to log a full exception
     * @param logLevel
     * @param tag
     * @param format
     * @param formatArgs formatArgs
     */
    public static void log(int logLevel, String tag, String format, Object ... formatArgs) {
        if(formatArgs.length > 0 && formatArgs[formatArgs.length-1] instanceof Throwable) {
            Object[] newArgs = Arrays.copyOf(formatArgs, formatArgs.length -1);
            Throwable th = (Throwable) formatArgs[formatArgs.length-1];
            log(logLevel, tag, String.format(Locale.ENGLISH, format, newArgs));
            recordException(th);
            if(BuildConfig.DEBUG) {
                throw new RuntimeException("Logging being used incorrectly. Don't pass throwable");
            }
        } else {
            log(logLevel, tag, String.format(format, formatArgs));
        }
    }

    public static void log(int logLevel, String tag, String message) {
        if(isDebug) {
            switch(logLevel) {
                case Log.DEBUG:
                    Log.d(tag, message);
                    break;
                case Log.VERBOSE:
                    Log.v(tag, message);
                    break;
                case Log.ERROR:
                    Log.e(tag, message);
                    break;
                case Log.WARN:
                    Log.w(tag, message);
                    break;
                case Log.INFO:
                    Log.i(tag, message);
                    break;
            }
        } else {
            FirebaseCrashlytics.getInstance().log(tag + ":" + message);
        }
    }

    public static void recordException(Throwable e) {
        if(isDebug) {
            Log.e("", "", e);
        }
        FirebaseCrashlytics.getInstance().recordException(e);
    }

    private synchronized static @Nullable FirebaseAnalytics getFirebaseAnalytics(Context context) {
        // Obtain the FirebaseAnalytics instance.
        if(firebaseAnalytics == null) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context);
        }
        return firebaseAnalytics;
    }

    public static void logAnalyticEventIfPossible(String message, Bundle detailBundle) {
        FirebaseAnalytics firebaseAnalytics = getFirebaseAnalytics(null);
        if(firebaseAnalytics != null) {
            firebaseAnalytics.logEvent(message, detailBundle);
        } else {
            log(Log.ERROR, TAG, "Unable to log to FirebaseAnalytics.");
        }
    }

    public static void logAnalyticEvent(Context context, String message, Bundle detailBundle) {
        getFirebaseAnalytics(context).logEvent(message, detailBundle);
    }

    public static void initialiseLogger(Class buildConfigClass) {
        String versionName = geStaticStringFieldValue(buildConfigClass, "VERSION_NAME");
        int versionCode = geStaticIntFieldValue(buildConfigClass, "VERSION_CODE");
        FirebaseCrashlytics.getInstance().setCustomKey("global_app_version", versionName);
        FirebaseCrashlytics.getInstance().setCustomKey("global_app_version_code", versionCode);
    }

    private static int geStaticIntFieldValue(@NonNull Class<?> clazz, @NonNull String fieldName) {
        try {
            return clazz.getField(fieldName).getInt(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            log(Log.ERROR, TAG, "Unable to get static field from class");
        }
        return -1;
    }

    private static String geStaticStringFieldValue(Class<?> clazz, @NonNull String fieldName) {
        try {
            return (String)clazz.getField(fieldName).get(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            log(Log.ERROR, TAG, "Unable to get static field from class");
        }
        return null;
    }

    public static void initialiseAnalytics(Context context, Class<?> buildConfigClass) {
        FirebaseAnalytics firebaseAnalytics = getFirebaseAnalytics(context);
        String versionName = geStaticStringFieldValue(buildConfigClass, "VERSION_NAME");
        int versionCode = geStaticIntFieldValue(buildConfigClass, "VERSION_CODE");
        firebaseAnalytics.setUserProperty("global_app_version", versionName);
        firebaseAnalytics.setUserProperty("global_app_version_code", "" + versionCode);
    }

    public static void initialise(Context context, Class<?> buildConfigClass) {
        Logging.initialiseLogger(buildConfigClass);
        Logging.initialiseAnalytics(context, buildConfigClass);
    }

    public static void addUserGuid(Context context, String userGuid) {
        FirebaseCrashlytics.getInstance().setUserId(userGuid);
        FirebaseAnalytics firebaseAnalytics = getFirebaseAnalytics(context);
        if(firebaseAnalytics != null) {
            firebaseAnalytics.setUserId(userGuid);
        } else {
            log(Log.ERROR, TAG, "Unable to add user GUID to FirebaseAnalytics.");
        }
    }
}
