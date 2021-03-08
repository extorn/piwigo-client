package delit.piwigoclient.ui.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import delit.libs.BuildConfig;
import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.ProgressIndicator;
import delit.libs.util.progress.SimpleProgressListener;

public class UiUpdatingProgressListener extends SimpleProgressListener {

    private static final String TAG = "UiUpdProgListener";
    private final @StringRes
    int taskDescriptionStrRes;
    private final ProgressIndicator progressIndicator;
    private final TimerThreshold thesholdGate = new TimerThreshold(100); // max update the ui once per half second

    public UiUpdatingProgressListener(@NonNull ProgressIndicator progressIndicator, @StringRes int taskDescriptionStrRes) {
        super(0.01);
        this.progressIndicator = progressIndicator;
        this.taskDescriptionStrRes = taskDescriptionStrRes;
    }

    @Override
    public void onProgress(double percent, boolean forceNotification) {
        if(!thesholdGate.thresholdMet() && !forceNotification) {
            if(BuildConfig.DEBUG) {
                Log.d(TAG,"Skipping UI progress view update (beyond refresh rate): " + percent);
            }
            return;
        }
        super.onProgress(percent, forceNotification);
    }

    @Override
    protected void onNotifiableProgress(double percent) {
        try {
            int progressAsInt = (int)Math.rint(100 * percent);
            if(BuildConfig.DEBUG) {
                Log.e(TAG,"Requesting UI progress view update: " + progressAsInt);
            }
            DisplayUtils.runOnUiThread(() -> {
                progressIndicator.showProgressIndicator(taskDescriptionStrRes, progressAsInt);
                synchronized (progressIndicator) {
                    progressIndicator.notifyAll();
                }
            });
            synchronized (progressIndicator) {
                try {
                    // pause thread until the progress bar has been updated
                    progressIndicator.wait(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (NullPointerException e) {
            Logging.log(Log.ERROR, TAG, "Error updating upload progress");
        }
    }

    @Override
    public void onStarted() {
        DisplayUtils.runOnUiThread(() -> progressIndicator.showProgressIndicator(taskDescriptionStrRes, 0));
    }

    @Override
    public void onComplete() {
        super.onComplete();
        DisplayUtils.runOnUiThread(progressIndicator::hideProgressIndicator);
    }
}
