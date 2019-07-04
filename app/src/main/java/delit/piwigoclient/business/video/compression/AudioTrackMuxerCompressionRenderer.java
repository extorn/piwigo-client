package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.crashlytics.android.Crashlytics;
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
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;

/**
 * This doesn't seem to work (with the video compression, but does alone) - issue with the link to exoplayer I think... It's kind of there, but not completely. Very weird error.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class AudioTrackMuxerCompressionRenderer extends MediaCodecAudioRenderer {

    private static final String TAG = "WorkingAudioCompressor";
    private static final long ENCODER_CODEC_INPUT_TIMEOUT_IN_MS = 10000;
    private static final long ENCODER_CODEC_OUTPUT_TIMEOUT_IN_MS = 200;

    private final MediaMuxerControl mediaMuxerControl;
    private final ExoPlayerCompression.AudioCompressionParameters compressionSettings;
    private boolean VERBOSE = false;
    private MediaFormat currentDecodedMediaFormat;
    private Set<Long> encodingFrames = new HashSet<>();
    private Map<Long, Integer> sampleTimeSizeMap = new HashMap<>(100);
    private MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo(); // shared copy for efficiency.
    private MediaFormat currentOutputMediaFormat;
    private MediaCodec encoder;
    private boolean transcodingTrack;
    private boolean processedSourceDataDuringRender;
    private int steppedWithoutActionCount;


    public AudioTrackMuxerCompressionRenderer(Context context, MediaCodecSelector mediaCodecSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, MediaMuxerControl mediaMuxerControl, AudioSink audioSink, ExoPlayerCompression.AudioCompressionParameters compressionSettings) {
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
        super.onQueueInputBuffer(buffer);
    }

    protected MediaFormat getOutputMediaFormat(MediaFormat inputMediaFormat) {
        if ("audio/raw".equals(inputMediaFormat.getString(MediaFormat.KEY_MIME))) {
            MediaFormat mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, inputMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), inputMediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));

            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, compressionSettings.getBitRate());
            if (inputMediaFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, inputMediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            } else {
                mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 655360);
            }
            // Set codec configuration values.
            if (Util.SDK_INT >= 23) {
                mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, 0 /* realtime priority */);
            }
            return mediaFormat;
        }
        return inputMediaFormat;
    }

    @Override
    protected void configureCodec(MediaCodecInfo codecInfo, MediaCodec streamDecoderCodec, Format format, MediaCrypto crypto) {
        super.configureCodec(codecInfo, streamDecoderCodec, format, crypto);
        int codecMaxInputSize = getCodecMaxInputSize(codecInfo, format, getStreamFormats());
        MediaFormat mediaFormat = getMediaFormat(format, format.sampleMimeType, codecMaxInputSize);
        MediaFormat outputMediaFormat = getOutputMediaFormat(mediaFormat);
        boolean addTrack = false;
        if (!mediaFormat.equals(currentDecodedMediaFormat)) {
            currentDecodedMediaFormat = mediaFormat;
            currentOutputMediaFormat = outputMediaFormat;
            transcodingTrack = !currentDecodedMediaFormat.equals(currentOutputMediaFormat);
            addTrack = !transcodingTrack;
        }
        if (addTrack) {
            mediaMuxerControl.addAudioTrack(outputMediaFormat);
            mediaMuxerControl.markAudioConfigured();
            mediaMuxerControl.startMediaMuxer();
        }
    }

    /**
     * Method cloned verbartim from the Parent class
     * <p>
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private android.media.MediaCodecInfo selectEncoderCodec(String mimeType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return selectEncoderCodecV21(mimeType);
        } else {
            return selectEncoderCodecV20(mimeType);
        }

    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private android.media.MediaCodecInfo selectEncoderCodecV21(String mimeType) {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (android.media.MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private android.media.MediaCodecInfo selectEncoderCodecV20(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            android.media.MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
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
        steppedWithoutActionCount++;
        processedSourceDataDuringRender = false;

        if (mediaMuxerControl.isAudioConfigured() && !mediaMuxerControl.isMediaMuxerStarted()) {
            // still trying to configure other tracks so lets not fill the buffers!
            return;
        }
        if (VERBOSE) {
            Log.d(TAG, String.format("Checking audio buffer at positionUs %1$d", positionUs));
        }

        if (transcodingTrack) {
            // need to do this here to ensure that there is space to move data to so as the decoder buffers don't get clogged up.
            processAnyEncoderOutput();
        }

//        this.renderPositionUs = positionUs;
        super.render(positionUs, elapsedRealtimeUs);
        mediaMuxerControl.markDataRead(processedSourceDataDuringRender);
        if (steppedWithoutActionCount > 10) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Process the decoder output buffer!
     *
     * @param positionUs
     * @param elapsedRealtimeUs
     * @param streamDecoderCodec
     * @param buffer
     * @param bufferIndex
     * @param bufferFlags
     * @param bufferPresentationTimeUs
     * @param shouldSkip
     * @return
     * @throws ExoPlaybackException
     */
    @Override
    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec streamDecoderCodec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs, boolean shouldSkip) throws ExoPlaybackException {
        steppedWithoutActionCount = 0;
        if (mediaMuxerControl.isVideoConfigured() && !mediaMuxerControl.isConfigured()) {
            Log.e(TAG, "Audio Processor - deferring render until mediamuxer is configured: position " + positionUs + " bufferIdx : " + bufferIndex);
            return false;
        }
        if (mediaMuxerControl.getAndResetIsSourceDataRead() && mediaMuxerControl.isHasVideo() && mediaMuxerControl.hasAudioDataQueued()) {
            if (bufferPresentationTimeUs > positionUs + compressionSettings.getMaxInterleavingIntervalUs()) {
                Log.e(TAG, "Audio Processor - Giving up render to video at position " + positionUs + " bufferIdx : " + bufferIndex);
                return false;
            }
            // this will essentially block the reading of data because it will allow all the input buffers on the decoder to fill and prevent them emptying.
        }

        try {
            // process the output data from the decoder
            processedSourceDataDuringRender = processDecoderOutputDataBuffer(streamDecoderCodec, buffer, bufferIndex, bufferFlags, bufferPresentationTimeUs);
            return processedSourceDataDuringRender;

        } catch (RuntimeException e) {
            throw ExoPlaybackException.createForRenderer(e, getIndex());
        } catch (IOException e) {
            throw ExoPlaybackException.createForRenderer(e, getIndex());
        }
    }

    /**
     * process data coming into the decoder from the input stream.
     *
     * @param streamDecoderCodec
     * @param buffer
     * @param bufferIndex
     * @param bufferFlags
     * @param bufferPresentationTimeUs
     * @return
     * @throws ExoPlaybackException
     * @throws IOException
     */
    private boolean processDecoderOutputDataBuffer(MediaCodec streamDecoderCodec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs) throws ExoPlaybackException, IOException {

        boolean bufferRead = processDecodedAudioData(buffer, bufferFlags, bufferPresentationTimeUs);
        if (bufferRead) {
            Log.d(TAG, "Releasing output buffer " + bufferIndex);
            streamDecoderCodec.releaseOutputBuffer(bufferIndex, false);
        } else {
            Log.e(TAG, "Not releasing output buffer " + bufferIndex);
        }
        return bufferRead;
    }

    private void initialiseOutputEncoder() throws IOException {
        String outputMimeType = currentOutputMediaFormat.getString(MediaFormat.KEY_MIME);
        android.media.MediaCodecInfo encoderCodecInfo = selectEncoderCodec(outputMimeType);
        if (encoderCodecInfo == null) {
            if (VERBOSE) {
                // Don't fail CTS if they don't have an AVC codec (not here, anyway).
                Log.e(TAG, "Unable to find an appropriate codec for " + outputMimeType);
            }
            throw new IOException("Unable to find appropriate codec for " + outputMimeType);
        }
        if (VERBOSE) {
            Log.d(TAG, "found codec: " + encoderCodecInfo.getName());
        }
        try {
            encoder = MediaCodec.createByCodecName(encoderCodecInfo.getName());
            encoder.configure(currentOutputMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

        } catch (IOException e) {
            throw new IOException("Unable to create, configure and start encoder for codec for " + outputMimeType, e);
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
    protected void renderToEndOfStream() throws ExoPlaybackException {
        if (mediaMuxerControl.isHasAudio()) {
            mediaMuxerControl.audioRendererStopped();
        }
    }

    private ByteBuffer getEncoderOutputBuffer(MediaCodec encoder, int encoderOutputBufferId) {
        ByteBuffer encodedDataBuffer = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            encodedDataBuffer = encoder.getOutputBuffer(encoderOutputBufferId);
        }
        if (encodedDataBuffer == null && encoderOutputBufferId >= 0) {
            ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
            encodedDataBuffer = encoderOutputBuffers[encoderOutputBufferId];
            if (VERBOSE) {
                Log.d(TAG, "encoder output buffers changed");
            }
        }
        return encodedDataBuffer;
    }

    private boolean processDecodedAudioData(ByteBuffer buffer, int bufferFlags, long bufferPresentationTimeUs) throws IOException {

        if (transcodingTrack) {
            // we're needing to encode the audio stream
            if (encoder == null) {
                initialiseOutputEncoder();
            }

            // write the data to the encoder.
            if (encoder != null) {
                return writeDataToEncoder(buffer, bufferFlags, bufferPresentationTimeUs);
            }
        } else {
            // this is not a stream being transcoded.
            processOutputAudioData(buffer, bufferFlags, bufferPresentationTimeUs);
        }
        return true;
    }

    private boolean writeDataToEncoder(ByteBuffer buffer, int bufferFlags, long bufferPresentationTimeUs) {

        int flags;
        int bytesWritten = 0;
        while (buffer.remaining() > 0) {
            flags = bufferFlags;
            int inputBufferId = encoder.dequeueInputBuffer(ENCODER_CODEC_INPUT_TIMEOUT_IN_MS);
            if (bytesWritten == 0 && inputBufferId < 0) {
                if (VERBOSE) {
                    Log.e(TAG, "Encoder InputBuffId : " + inputBufferId);
                    Log.e(TAG, "BufferBytes Need to wait for space in encoder : " + buffer.remaining());
                }
                return false;
            }

            ByteBuffer encoderInputBuffer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                encoderInputBuffer = encoder.getInputBuffer(inputBufferId);
            } else {
                encoderInputBuffer = encoder.getInputBuffers()[inputBufferId];
            }

            if (encoderInputBuffer != null) {
                encoderInputBuffer.clear();
                if (encoderInputBuffer.capacity() < buffer.remaining()) {
                    // the encoder buffer is smaller, we need to copy the raw data in chunks.
                    ByteBuffer tmp = buffer.duplicate();
                    tmp.limit(encoderInputBuffer.capacity());
                    buffer.position(tmp.limit());
                    bytesWritten = tmp.remaining();
                    encoderInputBuffer.put(tmp);
                } else {
                    bytesWritten = buffer.remaining();
                    encoderInputBuffer.put(buffer);
                }
                if (buffer.remaining() > 0 && (bufferFlags & BUFFER_FLAG_END_OF_STREAM) == BUFFER_FLAG_END_OF_STREAM) {
                    flags = bufferFlags ^ BUFFER_FLAG_END_OF_STREAM; // remove the flag
                }
                encoder.queueInputBuffer(inputBufferId, 0, bytesWritten, bufferPresentationTimeUs, flags);
            }
        }
        encodingFrames.add(bufferPresentationTimeUs);
        return true;
    }

    private void processAnyEncoderOutput() {
        if (encoder == null) {
            if (VERBOSE) {
                Log.d(TAG, "encoder not available to process data");
            }
            return;
        }
        int outputBufIndex = 0;
        MediaCodec.BufferInfo outBuffInfo = new MediaCodec.BufferInfo();
        while (outputBufIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
            // read data from the encoder if available.
            outputBufIndex = encoder.dequeueOutputBuffer(outBuffInfo, ENCODER_CODEC_OUTPUT_TIMEOUT_IN_MS);

            if (outputBufIndex >= 0) {
                ByteBuffer outputBuffer = getEncoderOutputBuffer(encoder, outputBufIndex);
                outputBuffer.position(outBuffInfo.offset);
                outputBuffer.limit(outBuffInfo.offset + outBuffInfo.size);

                if ((outBuffInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 && outBuffInfo.size != 0) {
                    //TODO maybe should process this by sending to the output too using the following call????
                    //processOutputAudioData(outputBuffer, outBuffInfo.flags, outBuffInfo.presentationTimeUs);
                    encoder.releaseOutputBuffer(outputBufIndex, false);
                } else {
                    processOutputAudioData(outputBuffer, outBuffInfo.flags, outBuffInfo.presentationTimeUs);
                    encoder.releaseOutputBuffer(outputBufIndex, false);
                }
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                currentOutputMediaFormat = encoder.getOutputFormat();
                if (VERBOSE) {
                    Log.d(TAG, "Output format changed to : " + currentOutputMediaFormat);
                }
                mediaMuxerControl.addAudioTrack(currentOutputMediaFormat);
                mediaMuxerControl.markAudioConfigured();
                mediaMuxerControl.startMediaMuxer();

            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Crashlytics.log(Log.ERROR, TAG, "Output buffers changed during encode!");
            } else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // NO OP
            } else {
                Crashlytics.log(Log.ERROR, TAG, "Unknown return code from dequeueOutputBuffer : " + outputBufIndex);
            }

            if ((outBuffInfo.flags & BUFFER_FLAG_END_OF_STREAM) == BUFFER_FLAG_END_OF_STREAM) {
                // The encoder is done with.
                if (VERBOSE) {
                    if (encodingFrames.size() > 0) {
                        Log.e(TAG, String.format("releasing encoding codec. It has %1$d frames still being encoded", encodingFrames.size()));
                    } else {
                        Log.d(TAG, "releasing encoding codec.");
                    }
                }
                encoder.stop();
                encoder.release();
                encoder = null;
                return;
            }
        }
    }

    private void processOutputAudioData(ByteBuffer buffer, int bufferFlags, long bufferPresentationTimeUs) {
        encodingFrames.remove(bufferPresentationTimeUs);
        audioBufferInfo.flags = bufferFlags;
        audioBufferInfo.offset = 0;
        audioBufferInfo.size = buffer.remaining();
        audioBufferInfo.presentationTimeUs = bufferPresentationTimeUs;
        if (VERBOSE) {
            Log.d(TAG, String.format("writing sample audio data : [%1$d] - flags : %2$d", audioBufferInfo.presentationTimeUs, bufferFlags));
        }

        Integer bytes = sampleTimeSizeMap.get(bufferPresentationTimeUs);
        long originalBytes = bytes == null ? 0 : bytes;

        mediaMuxerControl.writeSampleData(mediaMuxerControl.getAudioTrackId(), buffer, audioBufferInfo, originalBytes);


        decoderCounters.renderedOutputBufferCount++;
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
        int bufferFlags;
        long bufferPresentationTimeUs;

        public AudioBufferQueueItem(ByteBuffer buffer, int bufferFlags, long bufferPresentationTimeUs) {
            this.buffer = buffer;
            this.bufferFlags = bufferFlags;
            this.bufferPresentationTimeUs = bufferPresentationTimeUs;
        }
    }
}