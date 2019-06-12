package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.MediaClock;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import delit.piwigoclient.util.IOUtils;

/**
 * This doesn't seem to work (with the video compression, but does alone) - issue with the link to exoplayer I think... It's kind of there, but not completely. Very weird error.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class AudioTrackMuxerPassthroughRenderer extends MediaCodecAudioRenderer {

    private static final String TAG = "WorkingAudioCompressor";

    private final MediaMuxerControl mediaMuxerControl;
    private final boolean VERBOSE = false;
    private final ExoPlayerCompression.AudioCompressionParameters compressionSettings;
    private MediaFormat currentMediaFormat;
    Map<Long, Integer> sampleTimeSizeMap = new HashMap<>(100);
    private static final int PREFERRED_MAX_CACHE_SIZE = 3;
    private Queue<AudioBufferQueueItem> cachedAudioBufferQueue = new ArrayDeque<>();
    private long renderPositionUs;
    private MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo(); // shared copy for efficiency.
    private boolean lastRendererLoadedSourceData;

    public AudioTrackMuxerPassthroughRenderer(Context context, MediaCodecSelector mediaCodecSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, MediaMuxerControl mediaMuxerControl, AudioSink audioSink, ExoPlayerCompression.AudioCompressionParameters compressionSettings) {
        super(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, audioSink);
        this.mediaMuxerControl = mediaMuxerControl;
        this.compressionSettings = compressionSettings;
    }

    @Override
    protected boolean allowPassthrough(String mimeType) {
        return true; // why?!
    }

    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
        sampleTimeSizeMap.put(buffer.timeUs, buffer.data.remaining());
        mediaMuxerControl.markDataRead();
        super.onQueueInputBuffer(buffer);
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
        // do nothing
    }

    @Override
    protected void onStopped() {
        // Do nothing
    }

    @Override
    protected void onEnabled(boolean joining) throws ExoPlaybackException {
        mediaMuxerControl.setHasAudio();
        super.onEnabled(joining);
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        lastRendererLoadedSourceData = mediaMuxerControl.isSourceDataRead();
        if (mediaMuxerControl.isAudioConfigured() && !mediaMuxerControl.isMediaMuxerStarted()) {
            // still trying to configure other tracks so lets not fill the buffers!
            return;
        }
        if (VERBOSE) {
            Log.d(TAG, String.format("Checking audio buffer at positionUs %1$d", positionUs));
        }
        this.renderPositionUs = positionUs;
        super.render(positionUs, elapsedRealtimeUs);
    }

//    @Override
//    protected void onStreamChanged(Format[] formats, long offsetUs) throws ExoPlaybackException {
//        SampleStream stream = getStream();
//        if (!(stream instanceof SampleStreamWrapper)) {
//            replaceStream(formats, new SampleStreamWrapper(stream, mediaMuxerControl) {
//
//
//                @Override
//                boolean isMediaMuxerBeingConfigured(MediaMuxerControl mediaMuxerControl) {
//                    return mediaMuxerControl.isAudioConfigured() && !mediaMuxerControl.isConfigured();
//                }
//            }, offsetUs);
//        }
//        super.onStreamChanged(formats, offsetUs);
//    }

    @Override
    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs, boolean shouldSkip) {
        if (!mediaMuxerControl.isAudioConfigured()) {
            mediaMuxerControl.markAudioConfigured();
            return false;
            // don't want to cache everything in the buffer unless we need to clear some space for video data.
        }

        long maxRenderItemUs = positionUs + compressionSettings.getMaxInterleavingIntervalUs();

//        if (!mediaMuxerControl.isConfigured()) {
//            if ((cachedAudioBufferQueue.size() < PREFERRED_MAX_CACHE_SIZE || !lastRendererLoadedSourceData) && bufferPresentationTimeUs > maxRenderItemUs) { // if the last renderer didn't read anything, maybe the source data buffers are full - drain a bit.
//                writeTranscodedAudioToCache(codec, buffer, bufferIndex, bufferFlags, bufferPresentationTimeUs);
//                return true;
//            }
//            return false;
//        }

        mediaMuxerControl.startMediaMuxer();

        processAnyOlderCachedData(maxRenderItemUs);

        if (mediaMuxerControl.isHasVideo() && mediaMuxerControl.hasAudioDataQueued()) {
            if (bufferPresentationTimeUs > maxRenderItemUs) {
                if (cachedAudioBufferQueue.size() < PREFERRED_MAX_CACHE_SIZE || !lastRendererLoadedSourceData) { // if the last renderer didn't read anything, maybe the source data buffers are full - drain a bit.
                    writeTranscodedAudioToCache(codec, buffer, bufferIndex, bufferFlags, bufferPresentationTimeUs);
                    if (VERBOSE) {
                        Log.e(TAG, "Audio Processor - Giving up render to video at position " + positionUs);
                    }
                    return true;
                } else {
                    return false;
                }
            }
        }

        return writeTranscodedAudioToMuxer(codec, buffer, bufferIndex, bufferFlags, bufferPresentationTimeUs);

    }

    private void processAnyOlderCachedData(long maxRenderItemUs) {

        while (!cachedAudioBufferQueue.isEmpty()) {
            AudioBufferQueueItem cachedItem = cachedAudioBufferQueue.peek();
            if (cachedItem.bufferPresentationTimeUs > maxRenderItemUs) {
                return;
            }
            cachedItem = cachedAudioBufferQueue.poll();
            if (VERBOSE) {
                Log.d(TAG, String.format("Audio Renderer: Processing deferred sample audio data for track %1$d from buffer %2$d at [%3$d]", mediaMuxerControl.getAudioTrackId(), cachedItem.bufferIndex, cachedItem.bufferPresentationTimeUs));
            }
            writeTranscodedAudioToMuxer(null, cachedItem.buffer, cachedItem.bufferIndex, cachedItem.bufferFlags, cachedItem.bufferPresentationTimeUs);
        }
    }

    private void writeTranscodedAudioToCache(MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs) {
        ByteBuffer bufferClone = IOUtils.deepCopyVisible(buffer);
        cachedAudioBufferQueue.add(new AudioBufferQueueItem(bufferClone, bufferIndex, bufferFlags, bufferPresentationTimeUs));
        codec.releaseOutputBuffer(bufferIndex, false);
        if (VERBOSE) {
            Log.d(TAG, String.format("Pausing Audio Renderer: Deferring writing of sample audio data for track %1$d from buffer %2$d at [%3$d]", mediaMuxerControl.getAudioTrackId(), bufferIndex, bufferPresentationTimeUs));
        }
        if (!cachedAudioBufferQueue.isEmpty()) {
            long cacheSize = 0;
            for (AudioBufferQueueItem item : cachedAudioBufferQueue) {
                cacheSize += item.buffer.remaining();
            }
            Log.e(TAG, "AudioQueueSize : " + cachedAudioBufferQueue.size() + IOUtils.toNormalizedText(cacheSize));
        }
    }

    @Override
    public boolean isReady() {
        return super.isReady() || mediaMuxerControl.isMediaMuxerStarted();
    }

    @Override
    public boolean isEnded() {
        return super.isEnded() && !mediaMuxerControl.isAudioConfigured();
    }

    @Override
    protected void renderToEndOfStream() {
        if (cachedAudioBufferQueue.isEmpty()) {
            if (mediaMuxerControl.isHasAudio()) {
                mediaMuxerControl.audioRendererStopped();
            }
        } else {
            long maxRenderItemUs = renderPositionUs + compressionSettings.getMaxInterleavingIntervalUs();
            processAnyOlderCachedData(maxRenderItemUs);
        }
    }

    private boolean writeTranscodedAudioToMuxer(MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs) {
        audioBufferInfo.flags = bufferFlags;
        audioBufferInfo.offset = 0;
        audioBufferInfo.size = buffer.remaining();
        audioBufferInfo.presentationTimeUs = bufferPresentationTimeUs;
        if (VERBOSE) {
            Log.d(TAG, String.format("writing sample audio data : [%1$d] - flags : %2$d", audioBufferInfo.presentationTimeUs, bufferFlags));
        }

        long originalBytes = sampleTimeSizeMap.get(bufferPresentationTimeUs);

        mediaMuxerControl.writeSampleData(mediaMuxerControl.getAudioTrackId(), buffer, audioBufferInfo, originalBytes);

        if (codec != null) {
            // this is a live buffer attached not cached
            codec.releaseOutputBuffer(bufferIndex, false);
        }

        decoderCounters.renderedOutputBufferCount++;
        return true;
    }

    @Override
    protected void releaseCodec() {
        if (VERBOSE) {
            Log.e(TAG, "releasing decoding codec");
        }
        super.releaseCodec();

    }

    @Override //MediaClock
    public long getPositionUs() {
        return mediaMuxerControl.getLastWrittenDataTimeUs();
    }

    @Override
    public MediaClock getMediaClock() {
        if (mediaMuxerControl.isHasVideo()) {
            return null; // don't act like a clock!
        } else {
            return super.getMediaClock();
        }
    }

    private static class AudioBufferQueueItem {
        ByteBuffer buffer;
        int bufferIndex;
        int bufferFlags;
        long bufferPresentationTimeUs;

        public AudioBufferQueueItem(ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs) {
            this.buffer = buffer;
            this.bufferIndex = bufferIndex;
            this.bufferFlags = bufferFlags;
            this.bufferPresentationTimeUs = bufferPresentationTimeUs;
        }
    }
}