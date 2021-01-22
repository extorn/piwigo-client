package delit.piwigoclient.ui.util;

import android.util.Log;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.ProgressIndicator;
import delit.libs.util.progress.TaskProgressListener;

public class UiUpdatingProgressListener implements TaskProgressListener {

    private static final String TAG = "UiUpdProgListener";
    private final @StringRes
    int taskDescriptionStrRes;
    private final ProgressIndicator progressIndicator;
    private TimerThreshold thesholdGate = new TimerThreshold(1000); // max update the ui once per second

    public UiUpdatingProgressListener(@NonNull ProgressIndicator progressIndicator, @StringRes int taskDescriptionStrRes) {
        this.progressIndicator = progressIndicator;
        this.taskDescriptionStrRes = taskDescriptionStrRes;
    }

    @Override
    public void onProgress(@FloatRange(from = 0, to = 1) double percent) {
        if(!thesholdGate.thresholdMet()) {
            return;
        }
        try {
            int progressAsInt = (int)Math.rint(100 * percent);
            DisplayUtils.runOnUiThread(() -> {progressIndicator.showProgressIndicator(taskDescriptionStrRes, progressAsInt);});
        } catch (NullPointerException e) {
            Logging.log(Log.ERROR, TAG, "Error updating upload progress");
        }
    }

    @Override
    public @FloatRange(from = 0, to = 1) double getUpdateStep() {
        return 0.01;//1%
    }

    @Override
    public void onTaskStarted() {
        progressIndicator.showProgressIndicator(taskDescriptionStrRes, 0);
    }

    @Override
    public void onTaskFinished() {
        progressIndicator.hideProgressIndicator();
    }
}
