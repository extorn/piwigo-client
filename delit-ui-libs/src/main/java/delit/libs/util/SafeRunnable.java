package delit.libs.util;

import android.util.Log;

import delit.libs.core.util.Logging;

public class SafeRunnable implements Runnable {

    private static final String TAG = "SafeRunnable";
    private final Runnable r;

    public SafeRunnable(Runnable r) {
        this.r = r;
    }

    @Override
    public void run() {
        try {
            this.r.run();
        } catch (Exception e) {
            Logging.log(Log.ERROR, TAG, "Sinking Unexpected error in runnable");
            Logging.recordException(e);
        }
    }
}
