package delit.libs.util.progress;

import android.util.Log;

import androidx.annotation.FloatRange;

import delit.libs.core.util.Logging;
import delit.libs.util.Utils;

public class SimpleProgressListener implements ProgressListener {

    private static final String TAG = "SimpleProgressListener";
    private double notifyOnProgress;
    private double lastNotifiedAt;
    private boolean complete;

    /**
     * Notify only when complete.
     */
    public SimpleProgressListener() {
        this(1.0);
    }

    public SimpleProgressListener(@FloatRange(from = 0, to = 1) double notifyOnProgress) {
        this.notifyOnProgress = notifyOnProgress;
        if(notifyOnProgress > 1) {
            Logging.log(Log.WARN, TAG, "NotifyOnProgress should never be beyond 100% (1). It is %1$.02f", notifyOnProgress);
        }
    }

    @Override
    public void setMinimumProgressToNotifyFor(double notifyOnProgress) {
        this.notifyOnProgress = notifyOnProgress;
    }

    @Override
    public double getMinimumProgressToNotifyFor() {
        return notifyOnProgress;
    }

    public double getLastNotifiedPercentage() {
        return lastNotifiedAt;
    }

    @Override
    public void onProgress(double percent) {
        onProgress(percent, false);
    }

    @Override
    public void onProgress(double percent, boolean forceNotification) {
        if(complete) {
            if(percent < (2*notifyOnProgress)) {
                Logging.log(Log.DEBUG, TAG, "Presuming tracker restarted. Calling '.onStarted()'");
                onStarted();
            } else {
                return; // do nothing. This listener already knows it's complete - a final notification to process.
            }
        }
        // note force notification won't force refresh of last updated percentage.
        if ((forceNotification && Utils.doubleEquals(lastNotifiedAt,percent, 0.001)) || lastNotifiedAt + notifyOnProgress < percent || percent < lastNotifiedAt) {
            onNotifiableProgress(percent);
            lastNotifiedAt = percent;
        }
    }

    @Override
    public void onStarted() {
        complete = false;
    }

    @Override
    public void onComplete() {
        if(!complete) {
            complete = true;
            onNotifiableProgress(1.0);
            lastNotifiedAt = 1.0;
        }
    }

    protected void onNotifiableProgress(double percent) {
    }
}