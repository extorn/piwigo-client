package delit.piwigoclient.business.video.compression;

import android.os.Build;
import android.os.Handler;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;

import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class CompressionProgressListener implements Runnable {

    private final ExoPlayer player;
    private final Handler eventHandler;
    private MediaMuxerControl mediaMuxerControl;
    private ExoPlayerCompression.CompressionListener progressListener;
    private double compressionProgress;
    private float minReportedChange = 3f;

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

        long position = 0;
        long bufferedPosition = 0;
        long duration = 0;
        if (player != null) {
            long currentWindowTimeBarOffsetUs = 0;
            long durationUs = 0;
            int adGroupCount = 0;
            Timeline timeline = player.getCurrentTimeline();
            Timeline.Window window = new Timeline.Window();
            Timeline.Period period = new Timeline.Period();
            if (!timeline.isEmpty()) {
                int currentWindowIndex = player.getCurrentWindowIndex();
                currentWindowTimeBarOffsetUs = durationUs;
                timeline.getWindow(currentWindowIndex, window);

                for (int j = window.firstPeriodIndex; j <= window.lastPeriodIndex; j++) {
                    timeline.getPeriod(j, period);
                }
                durationUs += window.durationUs;
            }
            duration = C.usToMs(durationUs);
            position = C.usToMs(currentWindowTimeBarOffsetUs);
            bufferedPosition = position;
            position += player.getCurrentPosition();
            bufferedPosition += player.getBufferedPosition();
        }

        withProgress(position, bufferedPosition, duration);

        // Cancel any pending updates and schedule a new one if necessary.
        eventHandler.removeCallbacks(this);

        int playbackState = player == null ? Player.STATE_IDLE : player.getPlaybackState();
        if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
            long delayMs;
            if (player.getPlayWhenReady() && playbackState == Player.STATE_READY) {
                float playbackSpeed = player.getPlaybackParameters().speed;
                if (playbackSpeed <= 0.1f) {
                    delayMs = 1000;
                } else if (playbackSpeed <= 5f) {
                    long mediaTimeUpdatePeriodMs = 1000 / Math.max(1, Math.round(1 / playbackSpeed));
                    long mediaTimeDelayMs = mediaTimeUpdatePeriodMs - (position % mediaTimeUpdatePeriodMs);
                    if (mediaTimeDelayMs < (mediaTimeUpdatePeriodMs / 5)) {
                        mediaTimeDelayMs += mediaTimeUpdatePeriodMs;
                    }
                    delayMs =
                            playbackSpeed == 1 ? mediaTimeDelayMs : (long) (mediaTimeDelayMs / playbackSpeed);
                } else {
                    delayMs = 200;
                }
            } else {
                delayMs = 1000;
            }
            eventHandler.postDelayed(this, delayMs);
        }
    }

    private void withProgress(long position, long bufferedPosition, long duration) {
        double currentPosition = mediaMuxerControl.getLastWrittenDataTimeMs();
        double newCompressionProgress = ((currentPosition) / duration) * 100;
        if (newCompressionProgress - compressionProgress > minReportedChange) {
            compressionProgress = newCompressionProgress;
            progressListener.onCompressionProgress(compressionProgress, duration);
        }
    }


}