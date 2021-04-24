package delit.piwigoclient.business.video;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;

/**
 * The default {@link LoadControl} implementation.
 */
public final class PausableLoadControl extends DefaultLoadControl {

    private Listener listener;
    private boolean paused;

    /**
     * Constructs a pkg instance, using the {@code DEFAULT_*} constants defined in this class.
     */
    public PausableLoadControl() {
        super();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean shouldStartPlayback(long bufferedDurationUs, float playbackSpeed, boolean rebuffering, long targetLiveOffsetUs) {
        if (paused) {
            return false;
        }
        return super.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs);
    }

    public void pauseBuffering() {
        paused = true;
        if (listener != null) {
            listener.onPause();
        }
    }

    public void resumeBuffering() {
        paused = false;
        if (listener != null) {
            listener.onResume();
        }
    }

    @Override
    public boolean shouldContinueLoading(long playbackPositionUs, long bufferedDurationUs, float playbackSpeed) {
        if (paused) {
            return false;
        }
        return super.shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed);
    }

    public boolean isPaused() {
        return paused;
    }

    public interface Listener {
        void onPause();

        void onResume();
    }
}
