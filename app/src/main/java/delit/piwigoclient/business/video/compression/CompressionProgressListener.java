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
    private final ExoPlayer player;
    private final Handler eventHandler;
    private MediaMuxerControl mediaMuxerControl;
    private ExoPlayerCompression.CompressionListener progressListener;
    private double compressionProgress;
    private double progressPerSecond;
    private float minReportedChange = 0.03f; // 3%
    private long progressMeasurementPeriod;

    public CompressionProgressListener(Handler eventHandler, ExoPlayer player, MediaMuxerControl mediaMuxerControl, ExoPlayerCompression.CompressionListener progressListener) {
        this.player = player;
        this.progressListener = progressListener;
        this.eventHandler = eventHandler;
        this.mediaMuxerControl = mediaMuxerControl;
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
            withProgress(C.usToMs(durationUs));

            int playbackState = player == null ? Player.STATE_IDLE : player.getPlaybackState();
            if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
                if (progressMeasurementPeriod == 0) {
                    progressMeasurementPeriod = 1000; // presume it takes a second to achieve something useful
                } else {
                    progressMeasurementPeriod = (long) Math.rint(minReportedChange / progressPerSecond);
                    progressMeasurementPeriod = Math.max(500, progressMeasurementPeriod);
                }
                eventHandler.postDelayed(this, progressMeasurementPeriod);
            }
        }
    }

    private void withProgress(long durationMs) {
        double progress = mediaMuxerControl.getOverallProgress();
        if (progressMeasurementPeriod > 0) {
            progressPerSecond = (progress - compressionProgress) / progressMeasurementPeriod;
        }
        if (VERBOSE) {
            Log.d(TAG, String.format("%1$02f%%", 100 * progress));
        }
        if (progress - compressionProgress > minReportedChange || progress + 0.00001 > 1 || mediaMuxerControl.isFinished()) {
            compressionProgress = progress;
            progressListener.onCompressionProgress(100 * compressionProgress, durationMs);
        }
    }


}