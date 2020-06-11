package delit.piwigoclient.business.video.compression;

import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class CompressionProgressListener implements Runnable {

    private static final String TAG = "CompressionListener";
    private static final boolean VERBOSE = false;
    private static final long MAX_PROGRESS_REPORT_INTERVAL_DEFAULT = 5000;
    private final ExoPlayer player;
    private final Handler eventHandler;
    private MediaMuxerControl mediaMuxerControl;
    private ExoPlayerCompression.CompressionListener progressListener;
    private double lastReportedCompressionProgress;
    private double progressPerSecond;
    private float minReportedChange = 0.03f; // 3%
    private long maxProgressReportingIntervalMillis = MAX_PROGRESS_REPORT_INTERVAL_DEFAULT;
    private long millisSinceLastReport;

    public CompressionProgressListener(Handler eventHandler, ExoPlayer player, MediaMuxerControl mediaMuxerControl, ExoPlayerCompression.CompressionListener progressListener) {
        this.player = player;
        this.progressListener = progressListener;
        this.eventHandler = eventHandler;
        this.mediaMuxerControl = mediaMuxerControl;
    }

    public void setMaxProgressReportingIntervalMillis(long maxProgressReportingIntervalMillis) {
        this.maxProgressReportingIntervalMillis = maxProgressReportingIntervalMillis;
    }

    public void setMinReportedChange(float minReportedChange) {
        this.minReportedChange = minReportedChange;
    }

    @Override
    public void run() {
        calculateProgress();
    }

    private void calculateProgress() {

        long durationUs = 0;
        if (player != null) {
            Timeline timeline = player.getCurrentTimeline();
            if (!timeline.isEmpty()) {
                int windows = timeline.getWindowCount();
                Timeline.Window window = new Timeline.Window();
                for (int i = 0; i < windows; i++) {
                    timeline.getWindow(i, window);
                    durationUs += window.getDurationUs();
                }
            }
        }

        // Cancel any pending updates and schedule a new one if necessary.
        eventHandler.removeCallbacks(this);

        if (!mediaMuxerControl.isFinished()) {
            double currentProgress = mediaMuxerControl.getOverallProgress();

            withProgress(C.usToMs(durationUs), currentProgress);


            int playbackState = player == null ? Player.STATE_IDLE : player.getPlaybackState();
            if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {

                long progressMeasurementPeriod = getDelayUntilNextProgressCheck(currentProgress);
                millisSinceLastReport += progressMeasurementPeriod;
                eventHandler.postDelayed(this, progressMeasurementPeriod);
            }
        }
    }

    private long getDelayUntilNextProgressCheck(double currentProgress) {
        long progressMeasurementPeriod = maxProgressReportingIntervalMillis;

        if (currentProgress < minReportedChange) {
            // update twice a second.
            progressMeasurementPeriod = 500;
        } else {
            if (progressPerSecond > 0f) {
                progressMeasurementPeriod = (long) Math.rint(minReportedChange / progressPerSecond);
                progressMeasurementPeriod = Math.max(500, progressMeasurementPeriod);
                // adjust to try and ensure the end isn't missed by a long way.
                long estRemaining = Math.max(0, (long) Math.rint(((double) 1 - currentProgress) * (progressPerSecond / 1000)) - 1000);
                progressMeasurementPeriod = Math.min(progressMeasurementPeriod, estRemaining);
            }
            // update at a sensible rate
            progressMeasurementPeriod = Math.min(maxProgressReportingIntervalMillis, progressMeasurementPeriod); // never report progress at intervals exceeding x milliseconds
        }
        return progressMeasurementPeriod;
    }

    private void withProgress(long durationMs, double progress) {
        if (millisSinceLastReport > 0) {
            progressPerSecond = (progress - lastReportedCompressionProgress) / millisSinceLastReport;
        }
        if (VERBOSE) {
            Log.d(TAG, String.format("%1$02f%%", 100 * progress));
        }
        if (progress < minReportedChange || progress - lastReportedCompressionProgress > minReportedChange || progress + minReportedChange > 1 || mediaMuxerControl.isFinished()) {
            lastReportedCompressionProgress = progress;
            millisSinceLastReport = 0;
            progressListener.onCompressionProgress(mediaMuxerControl.getInputFile(), mediaMuxerControl.getOutputFile(), 100 * lastReportedCompressionProgress, durationMs);
        }
    }


}