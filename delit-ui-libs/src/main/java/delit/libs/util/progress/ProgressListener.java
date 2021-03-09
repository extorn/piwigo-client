package delit.libs.util.progress;

import androidx.annotation.FloatRange;

public interface ProgressListener {
    void setMinimumProgressToNotifyFor(double notifyOnProgress);

    double getMinimumProgressToNotifyFor();

    void onProgress(double percent);

    void onProgress(@FloatRange(from = 0, to = 1) double percent, boolean forceNotification);

    /**
     * Optional heads up that the task has just started doing something.
     */
    void onStarted();
    /**
     * Optional heads up that the task has just finished doing something.
     */
    void onComplete();
}