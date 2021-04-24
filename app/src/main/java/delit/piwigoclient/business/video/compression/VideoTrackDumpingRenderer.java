package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.nio.ByteBuffer;

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoTrackDumpingRenderer extends MediaCodecVideoRenderer {

    private static final String TAG = "VideoDumpRender";
    private final boolean VERBOSE = false;
    private final MediaMuxerControl mediaMuxerControl;

    public VideoTrackDumpingRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify, MediaMuxerControl mediaMuxerControl) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, playClearSamplesWithoutKeys, eventHandler, eventListener, maxDroppedFramesToNotify);
        this.mediaMuxerControl = mediaMuxerControl;
    }

    @Override
    protected void onStarted() {
    }

    @Override
    protected void onStopped() {
    }

    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) throws ExoPlaybackException {
        int byteCount = buffer.data.remaining();
        if(byteCount > 0) {
            mediaMuxerControl.writeDumpedInputData(byteCount);
        }
        super.onQueueInputBuffer(buffer);
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        if (VERBOSE) {
            Log.d(TAG, String.format("Checking video buffer at positionUs %1$d", positionUs));
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

        boolean outputDone = (bufferFlags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        if(outputDone) {
            setPendingOutputEndOfStream();
        }

        if (bufferPresentationTimeUs > positionUs + 500000) {
            if (VERBOSE) {
                Log.e(TAG, "Video Processor - Giving up render to audio at position " + positionUs);
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
        return null;
    }
}