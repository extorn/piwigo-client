package delit.libs.util.progress;

import androidx.annotation.FloatRange;

import delit.libs.util.Utils;

public class SimpleProgressListener implements ProgressListener {

    private final double notifyOnProgress;
    private double lastNotifiedAt;

    /**
     * Notify only when complete.
     */
    public SimpleProgressListener() {
        this(1.0);
    }

    public SimpleProgressListener(@FloatRange(from = 0, to = 1) double notifyOnProgress) {
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
        // note force notification won't force refresh of last updated percentage.
        if ((forceNotification && Utils.doubleEquals(lastNotifiedAt,percent, 0.001)) || lastNotifiedAt + notifyOnProgress < percent || percent < lastNotifiedAt) {
            onNotifiableProgress(percent);
            lastNotifiedAt = percent;
        }
    }

    @Override
    public void onStarted() {
    }

    @Override
    public void onComplete() {
        onNotifiableProgress(1.0);
        lastNotifiedAt = 1.0;
    }

    protected void onNotifiableProgress(double percent) {
    }
}