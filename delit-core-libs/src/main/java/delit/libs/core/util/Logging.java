package delit.libs.core.util;

import android.util.Log;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class Logging {

    private static boolean isDebug;

    public static void setDebug(boolean debug) {
        isDebug = debug;
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
