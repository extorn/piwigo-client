package delit.piwigoclient.business.video.compression;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * This doesn't seem to work - issue with the link to exoplayer I think... It's kind of there, but not completely. Very weird error.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoTrackMuxerCompressionRenderer extends MediaCodecVideoRenderer implements MediaClock {

    private static final String MIME_TYPE = "video/avc"; //API19+ MediaFormat.MIMETYPE_VIDEO_AVC;    // H.264 Advanced Video Coding
    //    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_MPEG4; // MPEG4 format
    private final static boolean VERBOSE = false;
    private static final String KEY_CROP_LEFT = "crop-left";
    private static final String KEY_CROP_RIGHT = "crop-right";
    private static final String KEY_CROP_BOTTOM = "crop-bottom";
    private static final String KEY_CROP_TOP = "crop-top";
    private final MediaMuxerControl mediaMuxerControl;
    private final String TAG = "CompressionVidRenderer";
    private final ExoPlayerCompression.VideoCompressionParameters compressionSettings;
    private CodecMaxValues currentCodecMaxValues;
    private int currentUnappliedRotationDegrees;
    private float currentPixelWidthHeightRatio;
    private int currentHeight;
    private int currentWidth;
    private int pendingRotationDegrees;
    private float pendingPixelWidthHeightRatio;
    private Integer scalingMode;
    Map<Long, Integer> sampleTimeSizeMap = new HashMap<>(100);
    private OutputSurface decoderOutputSurface;
    private boolean mediaMuxerStarted;
    private MediaCodec encoder;
    private MediaFormat inputMediaFormat;
    private boolean encoderInputEndedSignalled;
    private PlaybackParameters playbackParameters;
    private InputSurface encoderInputSurface;
    private Queue<Long> framesQueuedInEncoder = new LinkedList<>();

    public VideoTrackMuxerCompressionRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify, MediaMuxerControl mediaMuxerControl, ExoPlayerCompression.VideoCompressionParameters compressionSettings) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, maxDroppedFramesToNotify);
        this.mediaMuxerControl = mediaMuxerControl;
        this.compressionSettings = compressionSettings;
    }

    @Override
    protected boolean shouldDropBuffersToKeyframe(long earlyUs, long elapsedRealtimeUs) {
        if (compressionSettings.isAllowSkippingFrames()) {
            boolean dropBuffersToKeyFrame = super.shouldDropBuffersToKeyframe(earlyUs, elapsedRealtimeUs);
            return dropBuffersToKeyFrame;
        }
        return false; // never drop a buffer
    }

    @Override
    protected void dropOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
        if (VERBOSE) {
            Log.w(TAG, "Dropping output buffer at position " + presentationTimeUs);
        }
        super.dropOutputBuffer(codec, index, presentationTimeUs);
    }

    @Override
    protected boolean shouldDropOutputBuffer(long earlyUs, long elapsedRealtimeUs) {
        if (compressionSettings.isAllowSkippingFrames()) {
            boolean dropOutputBuffer = super.shouldDropOutputBuffer(earlyUs, elapsedRealtimeUs);
            return dropOutputBuffer;
        }
        return false; // never drop a buffer
    }

    @Override
    protected void skipOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
        if (compressionSettings.isAllowSkippingFrames()) {
            super.skipOutputBuffer(codec, index, presentationTimeUs);
            if (VERBOSE) {
                Log.w(TAG, "Skipping output buffer at position " + presentationTimeUs);
            }
        }
        // never skip a buffer
    }

    @Override
    protected boolean shouldForceRenderOutputBuffer(long earlyUs, long elapsedSinceLastRenderUs) {
        if (compressionSettings.isAllowSkippingFrames()) {
            return super.shouldForceRenderOutputBuffer(earlyUs, elapsedSinceLastRenderUs);
        }
        return true;
    }

    @Override
    protected void releaseCodec() {
        if (VERBOSE) {
            Log.d(TAG, "releasing decoding codec");
        }
        super.releaseCodec();
    }

    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
        sampleTimeSizeMap.put(buffer.timeUs, buffer.data.remaining());
        super.onQueueInputBuffer(buffer);
    }

    /**
     * cloned from MediaCodecVideoRenderer
     *
     * @param newFormat
     * @throws ExoPlaybackException
     */
    @Override
    protected void onInputFormatChanged(Format newFormat) throws ExoPlaybackException {
        // now do the normal call
        super.onInputFormatChanged(newFormat);
        pendingPixelWidthHeightRatio = newFormat.pixelWidthHeightRatio;
        pendingRotationDegrees = newFormat.rotationDegrees;
    }

    /**
     * Method cloned verbartim from the Parent class
     * <p>
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private android.media.MediaCodecInfo selectCodec(String mimeType) {
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
    public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
        if (messageType == C.MSG_SET_SCALING_MODE) {
            scalingMode = (Integer) message;
        }
        // now do the normal call
        super.handleMessage(messageType, message);
    }

    /**
     * Renders the output buffer with the specified index. This method is only called if the platform
     * API version of the device is less than 21.
     *
     * @param codec              The codec that owns the output buffer.
     * @param index              The index of the output buffer to drop.
     * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
     */
    protected void renderOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
        processEncoderOutput();
        super.renderOutputBuffer(codec, index, presentationTimeUs);
        moveDecoderOutputToEncoderInput(presentationTimeUs);
    }

    /**
     * Renders the output buffer with the specified index. This method is only called if the platform
     * API version of the device is 21 or later.
     *
     * @param codec              The codec that owns the output buffer.
     * @param index              The index of the output buffer to drop.
     * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
     * @param releaseTimeNs      The wallclock time at which the frame should be displayed, in nanoseconds.
     */
    @TargetApi(21)
    protected void renderOutputBufferV21(
            MediaCodec codec, int index, long presentationTimeUs, long releaseTimeNs) {
        if (VERBOSE) {
            Log.e(TAG, "Rendering to encoder input surface " + presentationTimeUs + " " + releaseTimeNs);
        }
        processEncoderOutput();
        super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
        moveDecoderOutputToEncoderInput(presentationTimeUs);
//        renderOutputBuffer(codec, index, presentationTimeUs);
    }

    @Override
    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs, boolean shouldSkip) throws ExoPlaybackException {
        if (mediaMuxerControl.isVideoConfigured() && !mediaMuxerControl.isConfigured()) {
            return false;
        }
        if (bufferPresentationTimeUs > positionUs + compressionSettings.getMaxInterleavingIntervalUs()) {
            Log.e(TAG, "Video Processor - Giving up render to audio at position " + positionUs);
            return false;
        }
        return super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags, bufferPresentationTimeUs, shouldSkip && compressionSettings.isAllowSkippingFrames());
    }


    @Override
    public boolean isReady() {
        return super.isReady() || mediaMuxerControl.isMediaMuxerStarted();
    }

    @Override
    protected void onStopped() {
        super.onStopped();
    }



    private void releaseTranscoder() {
        if (VERBOSE) {
            Log.d(TAG, "stopping and releasing encoder");
        }
        if (decoderOutputSurface != null && mediaMuxerControl.isHasVideo()) {
            if (decoderOutputSurface.isFrameAvailable()) {
                Log.e(TAG, "output surface has unexpected frame available");
            }
            decoderOutputSurface.release();
            decoderOutputSurface = null;
        }
        if (encoderInputSurface != null) {
            encoderInputSurface.release();
            encoderInputSurface = null;
        }
        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }
    }

    @Override
    protected void renderToEndOfStream() throws ExoPlaybackException {
        super.renderToEndOfStream();
        if (super.isEnded()) { // if the output stream has finished.
            // if the encoder is running - signal end of encoding stream
            if (!encoderInputEndedSignalled) {
                if (VERBOSE) {
                    Log.d(TAG, "Video encoder EOS signalled");
                }
                encoder.signalEndOfInputStream();
                encoderInputEndedSignalled = true;
            }
        }
    }

    private void moveDecoderOutputToEncoderInput(long presentationTimeUs) {

        framesQueuedInEncoder.add(presentationTimeUs);

        if (VERBOSE) {
            Log.d(TAG, String.format("Moving Sample to encoder - render time %1$d", presentationTimeUs));
        }

        try {
            // await for new data from the decoder
            decoderOutputSurface.awaitNewImage(10000);
        } catch (RuntimeException e) {
            //TODO this will occur if the thread is killed off - need to deal with this cleaning the mess left on file system!
            Log.w(TAG, "Timeout waiting for image to become available was interrupted");
        }

        // try and render what has been sent so far to the decoder.
        decoderOutputSurface.drawImage();

        // publish the frame to the encoder surface from the decoder decoderOutputSurface
        encoderInputSurface.setPresentationTime(presentationTimeUs * 1000);

        // move the frame to encoder
        encoderInputSurface.swapBuffers();

//        }
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        if (isEnded()) {
            return;
        }

        if (encoder != null) {
            if (VERBOSE) {
                Log.d(TAG, "Trying to render position " + positionUs + " isEnded : " + isEnded() + " isSuperEnded : " + super.isEnded());
            }
            processEncoderOutput();
        } else {
            if (VERBOSE) {
                Log.d(TAG, "Examining stream for format etc at position [" + positionUs + "].  isEnded : " + isEnded() + " isSuperEnded : " + super.isEnded());
            }
        }
        if (!super.isEnded()) {
            super.render(positionUs, elapsedRealtimeUs);
        } else {
            if (VERBOSE) {
                Log.d(TAG, "Extractor and decoder have already ended! Nothing to do for position : " + positionUs);
            }
        }
    }

    private boolean processEncoderOutput() {

        if (encoder == null) {
            Log.d(TAG, "No encoder to pass data to");
            return false;
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();


        boolean wroteData = false;
        boolean encoderOutputAvailable = true;
        int loopCnt = 0;
        boolean outputDone = false;
        ByteBuffer encodedDataBuffer;
        while (encoderOutputAvailable || framesQueuedInEncoder.size() > 2) {
            if (VERBOSE) {
                Log.d(TAG, "reading output from encoder if available");
            }
            // Start by draining any pending output from the encoder.  It's important to
            // do this before we try to stuff any more data in.
            int encoderOutputBufferId = encoder.dequeueOutputBuffer(info, 10 * framesQueuedInEncoder.size()); // vary the timeout to stop the decoder rushing ahead


            //MediaFormat bufferFormat = encoder.getOutputFormat(outputBufferId);
            if (encoderOutputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
//                if (VERBOSE) {
//                    Log.d(TAG, "no output from encoder available");
//                }
                encoderOutputAvailable = false;
            } else if (encoderOutputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = encoder.getOutputFormat();
                if (VERBOSE) {
                    Log.d(TAG, "encoder output format changed: " + newFormat + " [" + loopCnt + "]");
                }
                if (mediaMuxerControl.hasVideoTrack()) {
                    if (VERBOSE) {
                        Log.e(TAG, "Skipping encoder output format swap to : " + newFormat);
                    }
                } else {
                    mediaMuxerControl.addVideoTrack(newFormat);
                    mediaMuxerControl.setOrientationHint(currentUnappliedRotationDegrees);
                    mediaMuxerControl.markVideoConfigured();
                }
            } else if (encoderOutputBufferId < 0 && encoderOutputBufferId != MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderOutputBufferId + " [" + loopCnt + "]");
            }

            if (encoderOutputBufferId > 0) {
                // have data to process.

                if (!mediaMuxerStarted) {
                    try {
                        mediaMuxerStarted = true;
                        // the media muxer is ready to start accepting data now from the encoder
                        mediaMuxerControl.startMediaMuxer();
                    } catch (IllegalStateException e) {
                        Log.d(TAG, "video: error starting media muxer", e);
                    }
                }

                // get the data buffer
                encodedDataBuffer = getEncoderOutputBuffer(encoder, encoderOutputBufferId);

                // Write the data to the output media muxer
                if (info.size != 0) {

                    Long lastFrameQueued = framesQueuedInEncoder.remove();
                    if (lastFrameQueued != info.presentationTimeUs) {
                        throw new RuntimeException(String.format("Expected to retrieve frame %1$d but was frame %2$d", lastFrameQueued, info.presentationTimeUs));
                    }

                    if (VERBOSE) {
                        Log.d(TAG, String.format("writing sample video data (%2$dbytes) for frame at time [%1$d]", info.presentationTimeUs, info.size));
                    }

                    long originalBytes = sampleTimeSizeMap.get(info.presentationTimeUs);
                    mediaMuxerControl.writeSampleData(mediaMuxerControl.getVideoTrackId(), encodedDataBuffer, info, originalBytes);
                    wroteData = true;
                } else {
                    if (!outputDone) {
                        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    }
                    if (!outputDone) {
                        Log.e(TAG, "No Data in Buffer returned from encoder!");
                    }
                }
                encoder.releaseOutputBuffer(encoderOutputBufferId, false);

            }
            if (!outputDone) {
                outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            }
        }

        //Got to here!

        if (outputDone) {
            if (VERBOSE) {
                Log.d(TAG, "video encoder: EOS");
            }
            mediaMuxerControl.videoRendererStopped();
        }

        if (VERBOSE) {
            Log.d(TAG, "finished checking and processing encoder output");
        }

        if (isEnded()) {
            releaseTranscoder();
            inputMediaFormat = null;
        }
        return wroteData;
    }

    private ByteBuffer getEncoderOutputBuffer(MediaCodec encoder, int encoderOutputBufferId) {
        ByteBuffer encodedDataBuffer = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            encodedDataBuffer = encoder.getOutputBuffer(encoderOutputBufferId);
        }
        if (encodedDataBuffer == null && encoderOutputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
            encodedDataBuffer = encoderOutputBuffers[encoderOutputBufferId];
            if (VERBOSE) {
                Log.d(TAG, "encoder output buffers changed");
            }
        }
        return encodedDataBuffer;
    }

    @Override
    protected void onEnabled(boolean joining) throws ExoPlaybackException {
        super.onEnabled(joining);
        mediaMuxerControl.setHasVideo();
    }

    @Override
    public boolean isEnded() {
        return super.isEnded() && !mediaMuxerControl.isVideoConfigured();
    }



    @Override
    protected void onOutputFormatChanged(MediaCodec codec, MediaFormat outputFormat) {

        // called when the decoder is configured!

        boolean hasCrop = outputFormat.containsKey(KEY_CROP_RIGHT)
                && outputFormat.containsKey(KEY_CROP_LEFT) && outputFormat.containsKey(KEY_CROP_BOTTOM)
                && outputFormat.containsKey(KEY_CROP_TOP);
        currentWidth = hasCrop
                ? outputFormat.getInteger(KEY_CROP_RIGHT) - outputFormat.getInteger(KEY_CROP_LEFT) + 1
                : outputFormat.getInteger(MediaFormat.KEY_WIDTH);
        currentHeight = hasCrop
                ? outputFormat.getInteger(KEY_CROP_BOTTOM) - outputFormat.getInteger(KEY_CROP_TOP) + 1
                : outputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        currentPixelWidthHeightRatio = pendingPixelWidthHeightRatio;
        if (Util.SDK_INT >= 21) {
            // On API level 21 and above the decoder applies the rotation when rendering to the surface.
            // Hence currentUnappliedRotation should always be 0. For 90 and 270 degree rotations, we need
            // to flip the width, height and pixel aspect ratio to reflect the rotation that was applied.
            if (pendingRotationDegrees == 90 || pendingRotationDegrees == 270) {
                if (compressionSettings.isHardRotateVideo()) {
                    int rotatedHeight = currentWidth;
                    currentWidth = currentHeight;
                    currentHeight = rotatedHeight;
                    currentPixelWidthHeightRatio = 1f / currentPixelWidthHeightRatio;
                }
            }
        } else {
            // On API level 20 and below the decoder does not apply the rotation.
            currentUnappliedRotationDegrees = pendingRotationDegrees;
        }
        if (VERBOSE) {
            Log.d(TAG, "decoder output media format changing to : " + outputFormat);
        }

        try {
            configureCompressor(new CodecMaxValues(currentWidth, currentHeight, currentCodecMaxValues.inputSize));
        } catch (IOException e) {
            throw new RuntimeException("Error reconfiguring compressor", e);
        } catch (ExoPlaybackException e) {
            throw new RuntimeException("Error reconfiguring compressor", e);
        }
        // now do the normal call
        super.onOutputFormatChanged(codec, outputFormat);
    }

    @Override
    protected MediaFormat getMediaFormat(Format format, CodecMaxValues codecMaxValues, boolean deviceNeedsAutoFrcWorkaround, int tunnelingAudioSessionId) {
        inputMediaFormat = super.getMediaFormat(format, codecMaxValues, deviceNeedsAutoFrcWorkaround, tunnelingAudioSessionId);
        return inputMediaFormat;
    }

    @Override
    protected void configureCodec(MediaCodecInfo codecInfo, MediaCodec codec, Format format, MediaCrypto crypto) throws MediaCodecUtil.DecoderQueryException {
        super.configureCodec(codecInfo, codec, format, crypto);
        currentCodecMaxValues = getCodecMaxValues(codecInfo, format, getStreamFormats());
    }

    private boolean configureCompressor(CodecMaxValues codecMaxValues) throws IOException, ExoPlaybackException {
        if (VERBOSE) {
            Log.d(TAG, "configuring compressor using thread : " + Thread.currentThread().getName());
        }
        int wantedWidthPx = compressionSettings.getWantedWidthPx();
        if (wantedWidthPx < 0) {
            wantedWidthPx = codecMaxValues.width;
        }
        int wantedHeightPx = compressionSettings.getWantedHeightPx();
        if (wantedHeightPx < 0) {
            wantedHeightPx = codecMaxValues.height;
        }
        int wantedFrameRate = compressionSettings.getWantedFrameRate();
        int wantedKeyFrameInterval = compressionSettings.getWantedKeyFrameInterval();
        int wantedBitRate = compressionSettings.getWantedBitRate();
        if (wantedBitRate < 0) {
            wantedBitRate = codecMaxValues.inputSize * 8; // * (8 / 2);
        }
        int wantedBitRateModeV21 = compressionSettings.getWantedBitRateModeV21();

        android.media.MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            if (VERBOSE) {
                // Don't fail CTS if they don't have an AVC codec (not here, anyway).
                Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            }
            return false;
        }
        if (VERBOSE) {
            Log.d(TAG, "found codec: " + codecInfo.getName());
        }

        // Create an encoder format that matches the input format.  (Might be able to just
        // re-use the format used to generate the video, since we want it to be the same.)
        MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, wantedWidthPx, wantedHeightPx);
        outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

        if (inputMediaFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, inputMediaFormat.getInteger(MediaFormat.KEY_BIT_RATE));
        } else {
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, wantedBitRate); // average bitrate in bits per second
        }
        if (inputMediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, inputMediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
        } else {
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, wantedFrameRate); // Frames per second
        }
        if (inputMediaFormat.containsKey(MediaFormat.KEY_I_FRAME_INTERVAL)) {
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, inputMediaFormat.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL));
        } else {
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, wantedKeyFrameInterval); // interval in seconds between key frames
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (inputMediaFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                // it gets rotated by exoplayer so this isn't needed now.
                outputFormat.setInteger(MediaFormat.KEY_ROTATION, inputMediaFormat.getInteger(MediaFormat.KEY_ROTATION));
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (inputMediaFormat.containsKey(MediaFormat.KEY_BITRATE_MODE)) {
                // it gets rotated by exoplayer so this isn't needed now.
                outputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, inputMediaFormat.getInteger(MediaFormat.KEY_BITRATE_MODE));
            } else {
                outputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, wantedBitRateModeV21);
            }
        }

        encoder = MediaCodec.createEncoderByType(MIME_TYPE);
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        if (encoderInputSurface != null) {
            encoderInputSurface.release();
        }
        if (decoderOutputSurface != null) {
            decoderOutputSurface.release();
        }
        encoderInputSurface = new InputSurface(encoder.createInputSurface());//new InputSurfaceBuilder().getInputSurface(context, encoder.createInputSurface());
        encoderInputSurface.makeCurrent(); // must occur on same thread as the new OutputSurface() call!
        // the encoder is now ready to start accepting data from the input surface as it arrives (one frame at a time)
        encoder.start();

        decoderOutputSurface = new OutputSurface(new Handler(Looper.getMainLooper()));//new OutputSurfaceBuilder().getOutputSurface(context);
        // ensure the decoded data gets written to this surface
        handleMessage(C.MSG_SET_SURFACE, decoderOutputSurface.getSurface());
        return true;
    }

    @Override
    public MediaClock getMediaClock() {
        return this;
    }

    @Override //MediaClock
    public long getPositionUs() {
        return mediaMuxerControl.getLastWrittenDataTimeUs();
    }

    @Override //MediaClock
    public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
        return this.playbackParameters;
    }

    @Override //MediaClock
    public PlaybackParameters getPlaybackParameters() {
        return playbackParameters;
    }
/*
    private static class InputSurfaceBuilder implements Runnable {

        private InputSurface inputSurface;
        private Surface encoderInputSurface;

        public InputSurface getInputSurface(Context context, Surface encoderInputSurface) {
            this.encoderInputSurface = encoderInputSurface;
            Handler mainHandler = new Handler(context.getMainLooper());
            mainHandler.post(this);
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException("Thread was interrupted - probably wants to die");
                }
            }

            return this.inputSurface;
        }

        @Override
        public void run() {
            inputSurface = new InputSurface(encoderInputSurface);
            inputSurface.makeCurrent();
            synchronized (this) {
                notifyAll();
            }
        }
    }

    private static class OutputSurfaceBuilder implements Runnable {

        private OutputSurface outputSurface;

        public OutputSurface getOutputSurface(Context context) {
            Handler mainHandler = new Handler(context.getMainLooper());
            mainHandler.post(this);
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException("Thread was interrupted - probably wants to die");
                }
            }

            return outputSurface;
        }

        @Override
        public void run() {
            outputSurface = new OutputSurface(new Handler(Looper.getMainLooper()));
            synchronized (this) {
                notifyAll();
            }
        }
    }*/
}
