package delit.piwigoclient.business.video.capture;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.MediaClock;

import java.nio.ByteBuffer;

/**
 * This doesn't seem to work (with the video compression, but does alone) - issue with the link to exoplayer I think... It's kind of there, but not completely. Very weird error.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class AudioTrackDumpingRenderer extends MediaCodecAudioRenderer {

    private static final String TAG = "AudioDumpRenderer";
    private final boolean VERBOSE = false;
    private long lastPosition = -1;

    public AudioTrackDumpingRenderer(Context context, MediaCodecSelector mediaCodecSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener) {
        super(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, new DefaultAudioSink(AudioCapabilities.getCapabilities(context), new AudioProcessor[0]));
    }

    @Override
    protected boolean allowPassthrough(String mimeType) {
        return true;
    }

    @Override
    protected void onStarted() {
    }

    @Override
    protected void onStopped() {
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        if (VERBOSE) {
            Log.d(TAG, String.format("Checking audio buffer at positionUs %1$d", positionUs));
        }
        super.render(positionUs, elapsedRealtimeUs);
    }

    @Override
    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs, boolean shouldSkip) {
        if (bufferPresentationTimeUs > positionUs + 500000 || positionUs == lastPosition) {
            lastPosition = positionUs;
            if (VERBOSE) {
                Log.e(TAG, "Audio Processor - Giving up render to video at position " + positionUs);
            }
            return false;
        }
        codec.releaseOutputBuffer(bufferIndex, false);
        return true;
    }

    @Override
    public MediaClock getMediaClock() {
        return null;
    }
}