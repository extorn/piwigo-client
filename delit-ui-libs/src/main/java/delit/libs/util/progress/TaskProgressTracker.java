package delit.libs.util.progress;

import android.util.Log;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import delit.libs.core.util.Logging;

/**
 * Override reportProgress to manage the progress tracked
 * <p>
 * task comprises of x ticks (equal sized)
 */
public class TaskProgressTracker implements ProgressListener {

    private static final String TAG = "TaskProgressTracker";
    private final ProgressListener progressListener;
    private final long totalWork;
    private final double scalar;
    private final String taskName;
    private final List<TaskProgressTracker> subTasks = new ArrayList<>();
    private long workDone;
    private @FloatRange(from = 0, to = 1) double currentScaledProgress;
    private double notifiableProgressStep;
    private double lastNotifiedProgress;
    private SubTaskListener subTaskListener;
    private long minimumWorkReportingInterval;

    public TaskProgressTracker(String taskName, long totalWork, ProgressListener listener) {
        this(taskName, totalWork, 1, listener);
    }

    private TaskProgressTracker(String taskName, long totalWork, double scalar, ProgressListener listener) {
        this.taskName = taskName;
        this.totalWork = totalWork;
        this.scalar = scalar;
        this.progressListener = listener;
        this.notifiableProgressStep = calculateNotifiableProgressStep();
    }

    private double calculateNotifiableProgressStep() {
        double listenerMinimumNotifiableProgress = 0;
        if(progressListener != null) {
            double updateStep = progressListener.getUpdateStep();
            if(updateStep < 0 || updateStep > 1) {
                throw new IllegalArgumentException("Update step must be a % between 0 and 1, but was " + updateStep);
            }
            listenerMinimumNotifiableProgress = updateStep * scalar;
        }
        double taskTrackerMinimumReportingProgress = ((double)minimumWorkReportingInterval / totalWork) * scalar;
        return Math.max(listenerMinimumNotifiableProgress, taskTrackerMinimumReportingProgress);
    }

    public synchronized TaskProgressTracker addSubTask(String subTaskName, long subTaskTotalWork, long mainTaskWorkUnitsInSubTask) {
        TaskProgressTracker subTaskTracker = new TaskProgressTracker(subTaskName, subTaskTotalWork, calculateScalar(mainTaskWorkUnitsInSubTask), getSubTaskListener());
        subTasks.add(subTaskTracker);
        return subTaskTracker;
    }

    private ProgressListener getSubTaskListener() {
        if(subTaskListener == null) {
            subTaskListener = new SubTaskListener(this);
        }
        return subTaskListener;
    }

    private double calculateScalar(long subTaskTotalWork) {
        return ((double)subTaskTotalWork) / totalWork;
    }

    public synchronized void incrementWorkDone(long workDone) {
        this.workDone += workDone;
        afterTaskProgress();
        // should be fine. its all synchronized.
//        if(!subTasks.isEmpty()) {
//            throw new IllegalStateException("Unable to update overall work progress when sub divided.");
//        }
    }

    /**
     * Not safe when sub divided - which is going to be the definitive value?
     * @param workDone
     * @throws IllegalStateException if currently sub divided into one or more tasks as yet incomplete.
     */
    public synchronized void setWorkDone(long workDone) {
        this.workDone = workDone;
        afterTaskProgress();
        if(!subTasks.isEmpty()) {
            calculateProgressValue(); // this will clear any orphaned sub tasks that are complete.
            if(!subTasks.isEmpty()) {
                throw new IllegalStateException("Unable to update overall work progress when sub divided.\nProgressTracker state: " + toString());
            }
        }
    }

    private synchronized void afterTaskProgress() {
        if(workDone == totalWork) {
            currentScaledProgress = scalar;
            notifiableProgressStep = 0; // ensure the listener is called.
        } else {
            currentScaledProgress = calculateProgressValue() * scalar;
        }
        notifyListenersOfProgress();
        synchronized (this) {
            notifyAll();
        }
    }

    public synchronized boolean isComplete() {
        if(!subTasks.isEmpty()) {
            for(TaskProgressTracker subTask : subTasks) {
                if(!subTask.isComplete()) {
                    return false;
                }
            }
        }
        return totalWork == workDone;
    }

    /**
     *
     * @return progress through this task (not scaled as a portion of a larger task)
     */
    public double getTaskProgress() {
        return getScaledProgressValue() / scalar;
    }

    private void notifyListenersOfProgress() {
        if(progressListener != null) {
            double notifyAfter = lastNotifiedProgress + notifiableProgressStep;
            if(currentScaledProgress >= notifyAfter) {
                progressListener.onProgress(currentScaledProgress);
                lastNotifiedProgress = currentScaledProgress;
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TaskProgressTracker{");
        sb.append("taskName=").append(taskName);
        sb.append(String.format(Locale.UK, ", complete=%1.0f%% (%2$d / %3$d)", Math.rint(100 * getTaskProgress()), workDone, totalWork));
        sb.append(", subTasks=[").append(subTasks.size()).append(']');
        if(subTasks.size() > 0) {
            sb.append('{');
            for (Iterator<TaskProgressTracker> iterator = subTasks.iterator(); iterator.hasNext(); ) {
                TaskProgressTracker task = iterator.next();
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

    /**
     * @return progress in the range 0 - 1
     */
    private @FloatRange(from = 0,to = 1) double calculateProgressValue() {
        double totalSubTasksProgress = 0;
        if(!subTasks.isEmpty()) {
            for (Iterator<TaskProgressTracker> iterator = subTasks.iterator(); iterator.hasNext(); ) {
                TaskProgressTracker subTask = iterator.next();
                if (subTask.isComplete()) {
                    workDone += (totalWork * subTask.scalar);
                    iterator.remove();
                } else {
                    totalSubTasksProgress += subTask.getScaledProgressValue();
                }
            }
        }
        return (((double) workDone) / totalWork) + totalSubTasksProgress;
    }

    public synchronized int getActiveSubTasks() {
        return subTasks.size();
    }

    private double getScaledProgressValue() {
        return currentScaledProgress;
    }

    public void setMinimumWorkReportingInterval(long minimumWorkReportingInterval) {
        this.minimumWorkReportingInterval = minimumWorkReportingInterval;
        this.notifiableProgressStep = calculateNotifiableProgressStep();
    }

    public synchronized void markComplete() {
        if(workDone != totalWork) {
            setWorkDone(totalWork);
        }
    }

    @Override
    public void onProgress(@FloatRange(from = 0, to = 1) double percent) {
        if(percent < 0 || percent > 1) {
            Logging.log(Log.ERROR, TAG, "Progress out of range %1$.2f", percent);
            throw new IllegalArgumentException("Progress out of range");
        }
        setWorkDone((long)Math.rint(((double)totalWork) * percent));
    }

    /**
     * Always update the tracker when {@link #onProgress(double)} called
     * @return 0
     */
    @Override
    public double getUpdateStep() {
        return 0;
    }

    public long getRemainingWork() {
        return totalWork - workDone;
    }

    public double getMinimumUpdatePercent() {
        return notifiableProgressStep;
    }

    public synchronized void setExactProgress(double progress) {
        long workToMarkDone = BigDecimal.valueOf(progress * totalWork).setScale(0, RoundingMode.HALF_DOWN).longValue();
        setWorkDone(workToMarkDone);
    }

    public long getWorkDone() {
        return workDone;
    }

    private static class SubTaskListener implements ProgressListener {

        private final TaskProgressTracker overallTaskTracker;

        public SubTaskListener(TaskProgressTracker overallTaskTracker) {
            this.overallTaskTracker = overallTaskTracker;
        }

         /**
          * called on sub task progress.
          *
          * @param percent current task completion status
          */
         @Override
         public synchronized void onProgress(@FloatRange(from = 0, to = 1) double percent) {
             overallTaskTracker.afterTaskProgress();
         }

         @Override
         public synchronized double getUpdateStep() {
             // update this 10 times as frequently as this updates its listener.
             return overallTaskTracker.notifiableProgressStep / 10;
         }
     }


    public static class ProgressAdapter implements ProgressListener {

        @Override
        public void onProgress(@FloatRange(from = 0, to = 1) double percent) {
        }

        @Override
        public double getUpdateStep() {
            return 1; // update at 100% only
        }
    }
}