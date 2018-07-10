package delit.piwigoclient.business.video;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.upstream.DefaultAllocator;

/**
 * The default {@link LoadControl} implementation.
 */
public final class PausableLoadControl extends DefaultLoadControl {

    private boolean paused;

    /**
     * Constructs a pkg instance, using the {@code DEFAULT_*} constants defined in this class.
     */
    public PausableLoadControl() {
        super(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE));
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
    }

    public void resumeBuffering() {
        paused = false;
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
}
