package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.MediaClock;

import java.nio.ByteBuffer;

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class AudioTrackDumpingRenderer extends MediaCodecAudioRenderer {

    private static final String TAG = "AudioDumpRender";
    private final boolean VERBOSE = false;
    private final MediaMuxerControl mediaMuxerControl;

    public AudioTrackDumpingRenderer(Context context, MediaCodecSelector mediaCodecSelector, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, MediaMuxerControl mediaMuxerControl) {
        super(context, mediaCodecSelector, playClearSamplesWithoutKeys, eventHandler, eventListener, new DefaultAudioSink(AudioCapabilities.getCapabilities(context), new AudioProcessor[0]));
        this.mediaMuxerControl = mediaMuxerControl;
    }

    @Override
    protected void onStarted() {
        //super.super.onStarted(); // does nothing
//        audioSink.play(); // illogical
    }

    @Override
    protected void onStopped() {
//        updateCurrentPosition(); // corrupts current position by reading from the sink which we're not using.
//        audioSink.pause(); // illogical
//        super.super.onStopped(); // does nothing
    }

    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
        int byteCount = buffer.data.remaining();
        if(byteCount > 0) {
            mediaMuxerControl.writeDumpedInputData(byteCount);
        }
        super.onQueueInputBuffer(buffer);
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        if (VERBOSE) {
            Log.d(TAG, String.format("Checking audio buffer at positionUs %1$d", positionUs));
        }
        super.render(positionUs, elapsedRealtimeUs);
    }

    @Override
    protected boolean processOutputBuffer(
            long positionUs,
            long elapsedRealtimeUs,
            @Nullable MediaCodecAdapter codec,
            @Nullable ByteBuffer buffer,
            int bufferIndex,
            int bufferFlags,
            int sampleCount,
            long bufferPresentationTimeUs,
            boolean isDecodeOnlyBuffer,
            boolean isLastBuffer,
            Format format)
            throws ExoPlaybackException {
        if (bufferPresentationTimeUs > positionUs + 500000) {
            if (VERBOSE) {
                Log.e(TAG, "Audio Processor - Giving up render to video at position " + positionUs);
            }
            return false;
        }
        codec.releaseOutputBuffer(bufferIndex, false);
        return true;
    }

    @Override
    public boolean isReady() {
        return super.isReady() || mediaMuxerControl.isMediaMuxerStarted();
    }

    @Override
    public MediaClock getMediaClock() {
        if (mediaMuxerControl.isHasVideo()) {
            return null; // don't act like a clock!
        } else {
            return super.getMediaClock();
        }
    }
}