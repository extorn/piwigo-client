package delit.libs.util.progress;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import delit.libs.core.util.Logging;

public class DividableProgressTracker extends BasicProgressTracker implements Parcelable {
    private static final String TAG = "DividableProgressTracker";
    private long allocatedWorkFromParentTask = 0;
    private double progressScalar = 0;
    private long allocatedWork;
    private List<DividableProgressTracker> activeChildTasks = new ArrayList<>();
    private boolean alwaysNotifyOnChildComplete;

    public DividableProgressTracker(String taskName, @IntRange(from=0) long totalWork) {
        this(taskName, totalWork, null);
    }

    public DividableProgressTracker(String taskName, @IntRange(from=0) long totalWork, ProgressListener listener) {
        super(taskName, totalWork, listener);
    }

    protected DividableProgressTracker(@IntRange(from=1) long allocatedWorkFromParentTask, @NonNull String taskName, @IntRange(from=0) long totalWorkInThisTask, @NonNull DividableProgressTracker parentTask) {
        super(taskName, totalWorkInThisTask);
        // scalar is percent in main task
        this.progressScalar = ((double)allocatedWorkFromParentTask) / parentTask.getTotalWork();
        this.allocatedWorkFromParentTask = allocatedWorkFromParentTask;
        attachToParent(parentTask);
    }

    protected DividableProgressTracker(Parcel in) {
        super(in);
        allocatedWorkFromParentTask = in.readLong();
        progressScalar = in.readDouble();
        allocatedWork = in.readLong();
        activeChildTasks = Objects.requireNonNull(in.createTypedArrayList(DividableProgressTracker.CREATOR));
        for(DividableProgressTracker task : activeChildTasks) {
            task.attachToParent(this);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeLong(allocatedWorkFromParentTask);
        dest.writeDouble(progressScalar);
        dest.writeLong(allocatedWork);
        dest.writeTypedList(activeChildTasks);
    }

    public void setAlwaysNotifyOnChildComplete(boolean alwaysNotifyOnChildComplete) {
        this.alwaysNotifyOnChildComplete = alwaysNotifyOnChildComplete;
    }

    public void onProgressChange(boolean force) {
        //TODO add option of making this progress report notifiable - just because.
        boolean forceNotification = alwaysNotifyOnChildComplete && processAnyCompleteChildTasks();
        super.onProgressChange(force || forceNotification);
    }

    /**
     * @return true if any child tasks completed
     */
    private boolean processAnyCompleteChildTasks() {
        boolean childTaskCompleted = false;
        for (int i = 0; i < activeChildTasks.size(); i++) {
            DividableProgressTracker childTaskInProgress = activeChildTasks.get(i);
            if (childTaskInProgress.isComplete()) {
                // this will remove the child from the list.
                onChildTaskEventComplete(childTaskInProgress);
                childTaskCompleted = true;
                // because the child was removed, we need to adjust the loop idx.
                i--;
            }
        }
        return childTaskCompleted;
    }

    /**
     * @return total work not yet either allocated to a child task or already complete
     */
    @Override
    protected synchronized long getUnallocatedAndIncompleteWork() {
        return super.getUnallocatedAndIncompleteWork() - allocatedWork;
    }

    @Override
    protected synchronized double calculateProgressPercentage() {
        double progressPercentage = super.calculateProgressPercentage();
        for(DividableProgressTracker childTaskInProgress : activeChildTasks) {
            progressPercentage += childTaskInProgress.getProgressAsPercentageOfParent();
        }
        return progressPercentage;
    }

    public synchronized DividableProgressTracker addChildTask(@NonNull String taskName, @IntRange(from=1) long totalWorkInChild, @IntRange(from=1) long allocatedWorkFromParentTask) {
        if(allocatedWorkFromParentTask > getUnallocatedAndIncompleteWork()) {
            Logging.log(Log.ERROR, TAG, "Double allocation of work in tracker %1$s when creating child %2$s", getTaskName(), taskName);
        }
        DividableProgressTracker childTask = new DividableProgressTracker(allocatedWorkFromParentTask, taskName, totalWorkInChild, this);
        activeChildTasks.add(childTask);
        allocatedWork += allocatedWorkFromParentTask;
        return childTask;
    }

    public void removeChildTask(@NonNull DividableProgressTracker childTask) {
        if(!activeChildTasks.contains(childTask)) {
            Logging.log(Log.WARN, TAG, "Unable to remove child task %1$S. Task not found in parent %2$s", childTask.getTaskName(), getTaskName());
            return;
        }
        allocatedWork -= childTask.getWorkAllocatedFromParentTask();
        activeChildTasks.remove(childTask);
        childTask.onDetachedFromParent();
        onProgressChange(true);
    }

    public synchronized void onChildTaskEventComplete(@NonNull DividableProgressTracker childTask) {
        if(activeChildTasks.contains(childTask)) {
            long workDone = childTask.getWorkAllocatedFromParentTask();
            allocatedWork -= workDone;
            activeChildTasks.remove(childTask);
            incrementWorkDone(workDone);
        }
    }

    @Override
    public void markComplete() {

        if(!activeChildTasks.isEmpty()) {
            Logging.log(Log.WARN, TAG, "Updating task %1$s. Marking complete progress on all active child tasks %2$s", getTaskName(), getChildTaskSummary());
            while(!activeChildTasks.isEmpty()) {
                //NOTE: do the loop like this to avoid concurrent modification exception when the child triggers a removal of itself from this list.
                DividableProgressTracker child = activeChildTasks.get(0);
                Logging.log(Log.DEBUG, TAG, "task %1$s complete. marking child task %2$s (currently %3$.2f%%) complete", getTaskName(), child.getTaskName(), (100*child.getProgressPercentage()));
                child.markComplete();
            }
        }

        super.markComplete();

        ProgressListener progressListener = getListener();
        if(progressListener != null) {
            if(progressListener instanceof ChildProgressListener) {
                ((ChildProgressListener)progressListener).onComplete(this);
            }
            progressListener.onComplete();
        } else {
            Logging.log(Log.WARN, TAG, "rollback called on detached child task progress tracker: %1$s", getTaskName());
        }
    }

    private String getChildTaskSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (Iterator<DividableProgressTracker> iterator = activeChildTasks.iterator(); iterator.hasNext(); ) {
            DividableProgressTracker task = iterator.next();
            sb.append("['");
            sb.append(task.getTaskName());
            sb.append("':");
            sb.append(Math.round(100* task.getProgressPercentage()));
            sb.append("%]");
            if(iterator.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public void rollbackProgress() {
        if(!activeChildTasks.isEmpty()) {
            Logging.log(Log.WARN, TAG, "Rolling back progress on all active child tasks");
            for(DividableProgressTracker childTask : activeChildTasks) {
                childTask.rollbackProgress();
            }
        }

        super.rollbackProgress();

        if(allocatedWorkFromParentTask > 0 && getListener() != null && getListener() instanceof ChildProgressListener) {
            ((ChildProgressListener) getListener()).onRollback(this);
        } else {
            Logging.log(Log.WARN, TAG, "rollback called on detached child task progress tracker: %1$s", getTaskName());
        }
    }

    public int getActiveChildCount() {
        return activeChildTasks.size();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<DividableProgressTracker> CREATOR = new Creator<DividableProgressTracker>() {
        @Override
        public DividableProgressTracker createFromParcel(Parcel in) {
            return new DividableProgressTracker(in);
        }

        @Override
        public DividableProgressTracker[] newArray(int size) {
            return new DividableProgressTracker[size];
        }
    };

    public @IntRange(from=1) long getWorkAllocatedFromParentTask() {
        if(allocatedWorkFromParentTask == 0) {
            throw new IllegalStateException("Not attached to parent");
        }
        return allocatedWorkFromParentTask;
    }

    /**
     * Used by a parent task to sum up progress of all sub tasks (may be rolled back)
     * @return a percentage of the main task
     */
    protected @FloatRange(from = 0, to = 1) double getProgressAsPercentageOfParent() {
        if(allocatedWorkFromParentTask == 0) {
            throw new IllegalStateException("Not attached to parent");
        }
        return progressScalar * getProgressPercentage();
    }

    public void onDetachedFromParent() {
        //TODO maybe allow adding another listener for e.g. ui update of this piece of the main task.
        removeListener();
    }

    protected void attachToParent(@NonNull DividableProgressTracker parentTracker) {
        setListener(buildLinkProgressListener(parentTracker));
    }

    @Override
    public void setListener(@NonNull ProgressListener listener) {
        super.setListener(listener);
        if(!activeChildTasks.isEmpty()) {
            for (DividableProgressTracker activeChildTask : activeChildTasks) {
                activeChildTask.updateListenerNotificationFrequency(getFastestUpdateNeeded());
            }
        }
    }

    private void updateListenerNotificationFrequency(double fastedUpdateNeeded) {
        getListener().setMinimumProgressToNotifyFor(fastedUpdateNeeded);
    }

    private double getListenerNeededUpdateNotification(double fastestUpdateNeededByParent) {
        double percentProgressAsOnePercentOfMainProgress = Math.min(1,fastestUpdateNeededByParent * (1/progressScalar));
        return percentProgressAsOnePercentOfMainProgress;
    }

    protected DividableLinkProgressListener buildLinkProgressListener(@NonNull DividableProgressTracker parentTracker) {
        return new DividableLinkProgressListener(getListenerNeededUpdateNotification(parentTracker.getFastestUpdateNeeded()), parentTracker);
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DividableProgressTracker{");
        sb.append("taskName=").append(getTaskName());
        sb.append(String.format(Locale.UK, ", complete=%1.0f%% (%2$d / %3$d) allocatedWork:[%4$d]", Math.rint(100 * getProgressPercentage()), getWorkComplete(), getTotalWork(), allocatedWork));
        sb.append(", activeChildren=[").append(activeChildTasks.size()).append(']');
        if(activeChildTasks.size() > 0) {
            sb.append('{');
            for (Iterator<DividableProgressTracker> iterator = activeChildTasks.iterator(); iterator.hasNext(); ) {
                DividableProgressTracker task = iterator.next();
                sb.append(task);
                if(iterator.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    public DividableProgressTracker getChildTask(String taskName) {
        for(DividableProgressTracker child : activeChildTasks) {
            if(Objects.equals(taskName, child.getTaskName())) {
                return child;
            }
        }
        return null;
    }

    public void releaseParent() {
        ProgressListener progressListener = getListener();
        if(progressListener != null) {
            if(progressListener instanceof ChildProgressListener) {
                ((ChildProgressListener)progressListener).onReleaseParent(this);
            }
        } else {
            Logging.log(Log.WARN, TAG, "rollback called on detached child task progress tracker: %1$s", getTaskName());
        }
    }

    public void onChildTaskEventRollback(DividableProgressTracker basicProgressTracker) {
        basicProgressTracker.onProgressChange();
    }

    public interface ChildProgressListener extends ProgressListener {

        void onRollback(DividableProgressTracker childTask);

        void onComplete(DividableProgressTracker childTask);

        void onReleaseParent(DividableProgressTracker dividableProgressTracker);
    }
}
