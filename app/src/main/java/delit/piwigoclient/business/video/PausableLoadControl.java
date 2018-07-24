package delit.piwigoclient.business.video;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.upstream.DefaultAllocator;

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
        super(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean shouldStartPlayback(long bufferedDurationUs, float playbackSpeed, boolean rebuffering) {
        if (paused) {
            return false;
        }
        return super.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering);
    }

    public void pauseBuffering() {
        paused = true;
        if(listener != null) {
            listener.onPause();
        }
    }

    public void resumeBuffering() {
        paused = false;
        if(listener != null) {
            listener.onResume();
        }
    }

    @Override
    public boolean shouldContinueLoading(long bufferedDurationUs, float playbackSpeed) {
        if (paused) {
            return false;
        }
        return super.shouldContinueLoading(bufferedDurationUs, playbackSpeed);
    }

    public boolean isPaused() {
        return paused;
    }

    public interface Listener {
        void onPause();
        void onResume();
    }
}
