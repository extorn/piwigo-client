package delit.libs.core.util;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.BuildConfig;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import delit.libs.util.ObjectUtils;

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

    public static void logAnalyticEventIfPossible(@Size(min = 1,max = 40) String message) {
        logAnalyticEventIfPossible(message, null);
    }

    public static void logAnalyticEventIfPossible(@Size(min = 1,max = 40) String message, @Nullable Bundle detailBundle) {
        FirebaseAnalytics firebaseAnalytics = getFirebaseAnalytics(null);
        if(firebaseAnalytics != null) {
            if(message.length() > 40) {
                String safeMsg = message.substring(0, Math.min(message.length(), 39));
                firebaseAnalytics.logEvent(safeMsg, detailBundle);
                log(Log.ERROR, TAG, "FirebaseAnalytics message %1$s had to be trimmed to %2$s", message, safeMsg);
            } else {
                firebaseAnalytics.logEvent(message, detailBundle);
            }
        } else {
            log(Log.ERROR, TAG, "Unable to log to FirebaseAnalytics.");
        }
    }

    public static void logAnalyticEvent(@NonNull Context context, @NonNull @Size(min = 1,max = 40) String message) {
        logAnalyticEvent(context, message, null);
    }

    public static void logAnalyticEvent(@NonNull Context context, @NonNull @Size(min = 1,max = 40) String message, @Nullable Bundle detailBundle) {
        getFirebaseAnalytics(context).logEvent(message, detailBundle);
    }

    public static void initialiseLogger(String versionName, int versionCode) {
        FirebaseCrashlytics.getInstance().setCustomKey("global_app_version", versionName);
        FirebaseCrashlytics.getInstance().setCustomKey("global_app_version_code", versionCode);
    }

    public static void initialiseAnalytics(@NonNull Context context, String versionName, int versionCode) {

        FirebaseAnalytics firebaseAnalytics = getFirebaseAnalytics(context);
        firebaseAnalytics.setUserProperty("global_app_version", versionName);
        firebaseAnalytics.setUserProperty("global_app_version_code", "" + versionCode);
    }

    public static void initialise(@NonNull Context context, String versionName, int versionCode) {
        Logging.initialiseLogger(versionName, versionCode);
        Logging.initialiseAnalytics(context, versionName, versionCode);
    }

    public static void initialise(Context context, Class<?> buildConfigClass) {
        String versionName = ObjectUtils.getStaticStringFieldValue(buildConfigClass, "VERSION_NAME");
        int versionCode = ObjectUtils.getStaticIntFieldValue(buildConfigClass, "VERSION_CODE");
        Logging.initialiseLogger(versionName, versionCode);
        Logging.initialiseAnalytics(context, versionName, versionCode);
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

    public static void addContext(Context context, String key, Object val) {
        FirebaseAnalytics firebaseAnalytics = getFirebaseAnalytics(context);
        FirebaseCrashlytics firebaseCrashlytics = FirebaseCrashlytics.getInstance();
        if(!key.matches("[a-zA-Z0-9_]*")) {
            Logging.log(Log.ERROR, TAG, "logging context keys must only contain alphanumeric + _ chars");
        }
        if(firebaseAnalytics != null) {
            firebaseAnalytics.setUserProperty(key, (String)val);
        }
        if(val instanceof Long) {
            firebaseCrashlytics.setCustomKey(key, (Long) val);
        } else if(val instanceof Integer) {
            firebaseCrashlytics.setCustomKey(key, (Integer) val);
        } else if(val instanceof Double) {
            firebaseCrashlytics.setCustomKey(key, (Double) val);
        } else if(val instanceof Float) {
            firebaseCrashlytics.setCustomKey(key, (Float) val);
        } else if(val instanceof Boolean) {
            firebaseCrashlytics.setCustomKey(key, (Boolean) val);
        } else {
            if(val != null) {
                firebaseCrashlytics.setCustomKey(key, val.toString());
            }
        }
    }

    public static void addContext(Context context, Map<String, ?> contextInfoMap) {
        for(Map.Entry<String,?> contextInfoEntry : contextInfoMap.entrySet()) {
            addContext(context, contextInfoEntry.getKey(), contextInfoEntry.getValue());
        }
    }

    public static void waitForExceptionToBeSent() {
        boolean hasUnsentLogs = false;
        long timeoutAt = 2000 + System.currentTimeMillis();
        boolean timeout;
        do {
            Task<Boolean> task = FirebaseCrashlytics.getInstance().checkForUnsentReports();
            try {
                Tasks.await(task);
                hasUnsentLogs = task.getResult();
            } catch (ExecutionException | InterruptedException e) {
                Logging.log(Log.WARN, TAG, "Exception waiting for Crashlytics to upload report");
            }
            timeout = System.currentTimeMillis() > timeoutAt;
        } while(hasUnsentLogs && !timeout);
        if(timeout) {
            Logging.logAnalyticEventIfPossible("CrashlyticsUploadTimeout");
        }
    }
}
