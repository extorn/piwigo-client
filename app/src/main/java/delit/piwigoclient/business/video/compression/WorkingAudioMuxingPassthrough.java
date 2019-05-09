package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.SampleStream;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * This doesn't seem to work (with the video compression, but does alone) - issue with the link to exoplayer I think... It's kind of there, but not completely. Very weird error.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class WorkingAudioMuxingPassthrough extends MediaCodecAudioRenderer {

    private static final String TAG = "WorkingAudioCompressor";

    private final MediaMuxerControl mediaMuxerControl;
    private final boolean VERBOSE = true;
    private MediaFormat currentMediaFormat;
    private boolean rendererPaused;
    private Queue<RenderItem> renderItems = new ArrayDeque<>();
    private Queue<AudioBufferQueueItem> cachedAudioBufferQueue = new ArrayDeque<>();
    private long bytesWritten;

    public WorkingAudioMuxingPassthrough(Context context, MediaCodecSelector mediaCodecSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, MediaMuxerControl mediaMuxerControl, AudioSink audioSink) {
        super(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, audioSink);
        this.mediaMuxerControl = mediaMuxerControl;
    }

    @Override
    protected boolean allowPassthrough(String mimeType) {
        return true;
    }

    @Override
    protected void configureCodec(MediaCodecInfo codecInfo, MediaCodec codec, Format format, MediaCrypto crypto) {
        super.configureCodec(codecInfo, codec, format, crypto);
        int codecMaxInputSize = getCodecMaxInputSize(codecInfo, format, getStreamFormats());
        MediaFormat mediaFormat = getMediaFormat(format, format.sampleMimeType, codecMaxInputSize);
        boolean addTrack = false;
        if (!mediaFormat.equals(currentMediaFormat)) {
            currentMediaFormat = mediaFormat;
            addTrack = true;
        }
        if (addTrack) {
            mediaMuxerControl.addAudioTrack(mediaFormat);
        }
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
    protected void onEnabled(boolean joining) throws ExoPlaybackException {
        mediaMuxerControl.setHasAudio();
        super.onEnabled(joining);
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        if (VERBOSE) {
            Log.d(TAG, String.format("Checking audio buffer at positionUs %1$d", positionUs));
        }
        super.render(positionUs, elapsedRealtimeUs);
    }

    @Override
    protected void onStreamChanged(Format[] formats, long offsetUs) throws ExoPlaybackException {
        SampleStream stream = getStream();
        if (!(stream instanceof SampleStreamWrapper)) {
            replaceStream(formats, new SampleStreamWrapper(stream, mediaMuxerControl) {


                @Override
                boolean isMediaMuxerBeingConfigured(MediaMuxerControl mediaMuxerControl) {
                    return mediaMuxerControl.isAudioConfigured() && !mediaMuxerControl.isConfigured();
                }
            }, offsetUs);
        }
        super.onStreamChanged(formats, offsetUs);
    }


    @Override
    protected MediaCodecInfo getDecoderInfo(MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder) throws MediaCodecUtil.DecoderQueryException {
        MediaCodecInfo decoderInfo = super.getDecoderInfo(mediaCodecSelector, format, requiresSecureDecoder);
        return decoderInfo;
    }

    @Override
    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs, boolean shouldSkip) {
//                            return super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags, bufferPresentationTimeUs, false);
        return writeTranscodedAudioToMuxer(codec, buffer, bufferIndex, bufferFlags, bufferPresentationTimeUs, true);
    }

    @Override
    public boolean isEnded() {
        return super.isEnded() && !mediaMuxerControl.isHasAudio();
    }

    @Override
    protected void renderToEndOfStream() {
        if (mediaMuxerControl.isHasAudio()) {
            mediaMuxerControl.audioRendererStopped();
            Log.e(TAG, "Audio bytes written : " + bytesWritten);
        }
    }

    private boolean writeTranscodedAudioToMuxer(MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs, boolean checkCache) {
        mediaMuxerControl.markAudioConfigured();

        if (!mediaMuxerControl.isReadyForAudio(bufferPresentationTimeUs)) {
            return false;
        }


        if (!mediaMuxerControl.isConfigured()) {

            ByteBuffer bufferClone = ByteBuffer.allocate(buffer.remaining());
            byte[] dst = new byte[buffer.remaining()];
            buffer.get(dst);
            bufferClone.put(dst);
            bufferClone.flip();

            cachedAudioBufferQueue.add(new AudioBufferQueueItem(codec, bufferClone, bufferIndex, bufferFlags, bufferPresentationTimeUs));
            codec.releaseOutputBuffer(bufferIndex, false);
            if (VERBOSE) {
                Log.d(TAG, String.format("Pausing Audio Renderer: Deferring writing of sample audio data for track %1$d from buffer %2$d at [%3$d]", mediaMuxerControl.getAudioTrackId(), bufferIndex, bufferPresentationTimeUs));
            }
            rendererPaused = mediaMuxerControl.isAudioConfigured();
            return true;
        } else {
            if (rendererPaused) {
                Log.d(TAG, "Resuming Audio Renderer");
                rendererPaused = false;
            }
            if (checkCache) {
                while (cachedAudioBufferQueue.size() > 0) {
                    AudioBufferQueueItem cachedItem = cachedAudioBufferQueue.poll();
                    Log.d(TAG, String.format("Audio Renderer: Processing deferred sample audio data for track %1$d from buffer %2$d at [%3$d]", mediaMuxerControl.getAudioTrackId(), cachedItem.bufferIndex, cachedItem.bufferPresentationTimeUs));
                    writeTranscodedAudioToMuxer(cachedItem.codec, cachedItem.buffer, cachedItem.bufferIndex, cachedItem.bufferFlags, cachedItem.bufferPresentationTimeUs, false);
                }
            }
        }

        mediaMuxerControl.startMediaMuxer();


        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
        audioBufferInfo.flags = bufferFlags;
        audioBufferInfo.offset = 0;
        audioBufferInfo.size = buffer.remaining();
        audioBufferInfo.presentationTimeUs = bufferPresentationTimeUs;
        if (VERBOSE) {
            Log.d(TAG, String.format("writing sample audio data : [%1$d] - flags : %2$d", audioBufferInfo.presentationTimeUs, bufferFlags));
        }

        bytesWritten += buffer.remaining();
        mediaMuxerControl.writeSampleData(mediaMuxerControl.getAudioTrackId(), buffer, audioBufferInfo);

        if (checkCache) {
            codec.releaseOutputBuffer(bufferIndex, false);
        }

        decoderCounters.renderedOutputBufferCount++;
        return true;
    }

    @Override
    protected void releaseCodec() {
        Log.e(TAG, "releasing decoding codec");
        super.releaseCodec();

    }

    private static class RenderItem {
        long positionUs;
        long elapsedRealtimeUs;

        public RenderItem(long positionUs, long elapsedRealtimeUs) {
            this.positionUs = positionUs;
            this.elapsedRealtimeUs = elapsedRealtimeUs;
        }
    }

    private static class AudioBufferQueueItem {
        MediaCodec codec;
        ByteBuffer buffer;
        int bufferIndex;
        int bufferFlags;
        long bufferPresentationTimeUs;

        public AudioBufferQueueItem(MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs) {
            this.codec = codec;
            this.buffer = buffer;
            this.bufferIndex = bufferIndex;
            this.bufferFlags = bufferFlags;
            this.bufferPresentationTimeUs = bufferPresentationTimeUs;
        }
    }


}