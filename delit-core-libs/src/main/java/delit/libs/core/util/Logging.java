package delit.libs.core.util;

import android.util.Log;

import com.google.firebase.BuildConfig;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.Arrays;
import java.util.Locale;

public class Logging {

    private static boolean isDebug;

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

}
