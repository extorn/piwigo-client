package delit.libs.util.progress;

import android.util.Log;

import delit.libs.core.util.Logging;

public class TrackerUpdatingProgressListener implements ProgressListener {

    private static final String TAG = "TrackerUpdatingProgressListener";
    private final BasicProgressTracker tracker;

    public TrackerUpdatingProgressListener(BasicProgressTracker tracker) {
        this.tracker = tracker;
        if(this.tracker.getTotalWork() != 100) {
            Logging.log(Log.WARN, TAG, "Tracker is expected to have exactly 100 items of work. Actually has %1$d", tracker.getTotalWork());
        }
    }

    @Override
    public void setMinimumProgressToNotifyFor(double notifyOnProgress) {
        // Do nothing. always updates in any case.
    }

    @Override
    public double getMinimumProgressToNotifyFor() {
        return 0;
    }

    @Override
    public void onProgress(double percent) {
        long totalWork = tracker.getTotalWork();
        long workComplete = tracker.getWorkComplete();
        long newWorkComplete = Math.round(((double)totalWork) * percent);
        if(newWorkComplete < workComplete) {
            throw new IllegalStateException("This is expected to always be higher than the previous value");
        }
        tracker.setWorkDone(newWorkComplete);
    }

    @Override
    public void onProgress(double percent, boolean forceNotification) {
        onProgress(percent);
    }

    @Override
    public void onStarted() {
        // trigger an update for listeners
        onProgress(tracker.getProgressPercentage());
    }

    @Override
    public void onComplete() {
        tracker.markComplete();
    }
}
