package delit.libs.util.progress;

public class TrackerUpdatingProgressListener implements ProgressListener {

    private BasicProgressTracker tracker;

    public TrackerUpdatingProgressListener(BasicProgressTracker tracker) {
        this.tracker = tracker;
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
