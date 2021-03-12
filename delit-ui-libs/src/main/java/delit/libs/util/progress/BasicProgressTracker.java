package delit.libs.util.progress;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Locale;

import delit.libs.core.util.Logging;

public class BasicProgressTracker implements Parcelable {

    private static final String TAG = "TaskProgressTrackerV2";
    private final String taskName;
    private final long totalWork;
    private ProgressListener listener;
    private long workComplete;
    private double progressPercentage;
    private double completePercentage;

    public BasicProgressTracker(String taskName, @IntRange(from=0) long totalWork) {
        this(taskName, totalWork, null);
    }

    public BasicProgressTracker(String taskName, @IntRange(from=0) long totalWork, ProgressListener listener) {
        this.taskName = taskName;
        this.totalWork = totalWork;
        this.listener = listener;
    }

    protected BasicProgressTracker(Parcel in) {
        taskName = in.readString();
        totalWork = in.readLong();
        workComplete = in.readLong();
        progressPercentage = in.readDouble();
        completePercentage = in.readDouble();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(taskName);
        dest.writeLong(totalWork);
        dest.writeLong(workComplete);
        dest.writeDouble(progressPercentage);
        dest.writeDouble(completePercentage);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BasicProgressTracker> CREATOR = new Creator<BasicProgressTracker>() {
        @Override
        public BasicProgressTracker createFromParcel(Parcel in) {
            return new BasicProgressTracker(in);
        }

        @Override
        public BasicProgressTracker[] newArray(int size) {
            return new BasicProgressTracker[size];
        }
    };

    public void setListener(@NonNull ProgressListener listener) {
        this.listener = listener;
    }

    public synchronized void decrementWorkDone(@IntRange(to=0) long decrementBy) {
        if(decrementBy < 0) {
            throw new IllegalStateException("Unable to decrement by a negative value. Use increment instead");
        }
        if(workComplete == 0) {
            Logging.log(Log.ERROR, TAG, "Task tracker already at 0 progress when attempting to decrement %1$s %2$d", getTaskName(), decrementBy);
            return;
        }
        if(decrementBy > workComplete) {
            Logging.log(Log.ERROR, TAG, "Decrementing workDone beyond 0 in tracker %1$s.", getTaskName());
        }
        workComplete -= decrementBy;
        workComplete = Math.max(workComplete, 0);// make sure we don't mess anything up visually
        onProgressChange();
    }

    public void setWorkDone(long newWorkDone) {
        long delta = newWorkDone - workComplete;
        if(delta > 0) {
            incrementWorkDone(delta);
        } else if(delta < 0) {
            decrementWorkDone(-delta);
        }
        // if not incrementing or decrementing, nothing to do.
    }

    public synchronized void incrementWorkDone(@IntRange(from=0) long incrementBy) {
        if(incrementBy < 0) {
            throw new IllegalStateException("Unable to increment by a negative value. Use decrement instead");
        }
        if(workComplete == totalWork) {
            Logging.log(Log.ERROR, TAG, "Task tracker already complete when attempting to increment %1$s %2$d", getTaskName(), incrementBy);
            return;
        }
        if(incrementBy > getUnallocatedAndIncompleteWork()) {
            Logging.log(Log.ERROR, TAG, "Incrementing workDone already accounted for in tracker %1$s.", getTaskName());
        }
        workComplete += incrementBy;
        workComplete = Math.min(workComplete, totalWork);// make sure we don't mess anything up visually
        onProgressChange();
    }

    public @FloatRange(from=0,to=1) double getProgressPercentage() {
        return progressPercentage;
    }

    protected void onProgressChange() {
        onProgressChange(false);
    }

    public void onProgressChange(boolean forceNotification) {
        calculateProgressValues();
        if(listener != null) {
            listener.onProgress(getProgressPercentage(), forceNotification);
            if(totalWork == workComplete) {
                listener.onComplete();
            }
        }
        synchronized (this) {
            notifyAll();
        }
    }

    /**
     * @return total work not yet either allocated to a child task or already complete
     */
    protected synchronized long getUnallocatedAndIncompleteWork() {
        return totalWork - workComplete;
    }

    protected synchronized void calculateProgressValues() {
        completePercentage = calculateCompletePercentage();
        progressPercentage = calculateProgressPercentage();
    }

    protected synchronized double calculateCompletePercentage() {
        return ((double)workComplete) / totalWork;
    }

    protected synchronized double calculateProgressPercentage() {
        return completePercentage;
    }

    public String getTaskName() {
        return taskName;
    }

    public void removeListener() {
        this.listener = null;
    }

    public ProgressListener getListener() {
        return listener;
    }

    public void markComplete() {
        workComplete = totalWork;
        onProgressChange();
    }

    public void rollbackProgress() {
        workComplete = 0;
        onProgressChange();
    }

    public boolean isComplete() {
        return totalWork == workComplete;
    }

    public long getRemainingWork() {
        return totalWork - workComplete;
    }

    public long getWorkComplete() {
        return workComplete;
    }

    public long getTotalWork() {
        return totalWork;
    }

    /**
     * Fastest requirement for updates to be sent by child tasks
     * This is set by the most demanding progress listener.
     * If the progressPercentage is requested, the value will returned will always be accurate.
     *
     * @return 1 if no listener set (update only once complete)
     */
    protected double getFastestUpdateNeeded() {
        if(listener != null) {
            return listener.getMinimumProgressToNotifyFor();
        } else {
            return 1.0;
        }
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BasicProgressTracker{");
        sb.append("taskName=").append(taskName);
        sb.append(String.format(Locale.UK, ", complete=%1.0f%% (%2$d / %3$d)", Math.rint(100 * getProgressPercentage()), workComplete, totalWork));
        sb.append('}');
        return sb.toString();
    }

    public void waitForProgress(long maxWait) throws InterruptedException {
        synchronized (this) {
            wait(maxWait);
        }
    }

    public void waitUntilComplete(long waitMillis, boolean exitOnInterrupt) {
        boolean cancelled = false;
        while (!cancelled && !isComplete()) {
            try {
                waitForProgress(waitMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
                if(exitOnInterrupt) {
                    cancelled = true;
                } else {
                    Logging.log(Log.WARN,TAG, "Ignoring interrupt while waiting for progress tracker to complete ! %1$s", this);
                }
            }
        }
        int outstandingTasks = (int)getRemainingWork();
        Logging.log(Log.INFO,TAG, "Finished waiting for executor to end (cancelled : "+cancelled+") while listening to progress. Outstanding Task Count : " + outstandingTasks);
    }
}
