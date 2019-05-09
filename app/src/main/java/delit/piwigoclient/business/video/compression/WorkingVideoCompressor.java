package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.io.IOException;
import java.nio.ByteBuffer;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * This doesn't seem to work - issue with the link to exoplayer I think... It's kind of there, but not completely. Very weird error.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class WorkingVideoCompressor extends MediaCodecVideoRenderer implements MediaClock {

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_MPEG4; //MIMETYPE_VIDEO_AVC;    // H.264 Advanced Video Coding
    private final static boolean VERBOSE = true;
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
    private long samplesTranscoded;
    private OutputSurface outputSurface;
    private InputSurface inputSurface;
    private boolean mediaMuxerStarted;
    private MediaCodec encoder;
    private MediaFormat inputMediaFormat;
    private int encodedBytes;
    private boolean encoderInputEndedSignalled;
    private long currentPositionUs;
    private PlaybackParameters playbackParameters;

    public WorkingVideoCompressor(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify, MediaMuxerControl mediaMuxerControl, ExoPlayerCompression.VideoCompressionParameters compressionSettings) {
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
        Log.w(TAG, "Dropping output buffer at position " + presentationTimeUs);
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
            Log.w(TAG, "Skipping output buffer at position " + presentationTimeUs);
        }
        // never skip a buffer
    }

    @Override
    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs, boolean shouldSkip) throws ExoPlaybackException {
        return super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags, bufferPresentationTimeUs, shouldSkip && compressionSettings.isAllowSkippingFrames());
    }

    @Override
    protected boolean shouldForceRenderOutputBuffer(long earlyUs, long elapsedSinceLastRenderUs) {
        if (compressionSettings.isAllowSkippingFrames()) {
            return super.shouldForceRenderOutputBuffer(earlyUs, elapsedSinceLastRenderUs);
        }
        return true;
    }

    @Override
    public boolean isReady() {
        return /*isEncoderReadyForData() && */super.isReady();
    }

    @Override
    protected void onStopped() {
        super.onStopped();
    }

    @Override
    protected void releaseCodec() {
        if (VERBOSE) {
            Log.d(TAG, "releasing decoding codec");
        }
        super.releaseCodec();

    }

    private void releaseTranscoder() {
        if (VERBOSE) {
            Log.d(TAG, "stopping and releasing encoder");
        }
        if (outputSurface != null) {
            if (outputSurface.isFrameAvailable()) {
                Log.e(TAG, "output surface has unexpected frame available");
            }
            outputSurface.release();
            outputSurface = null;
        }
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
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

    private boolean isEncoderReadyForData() {
        return encoder != null;
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        if (VERBOSE) {
            Log.d(TAG, String.format("Checking video buffer at positionUs %1$d", positionUs));
        }

        if (processEncoderOutput()) {
            this.samplesTranscoded++;
        }

        /*if (isEncoderReadyForData()) */
        {
            super.render(positionUs, elapsedRealtimeUs);
        }

        if (processEncoderOutput()) {
            this.samplesTranscoded++;
        }
    }

    @Override
    protected void renderOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
        if (VERBOSE) {
            Log.d(TAG, String.format("Rendering output buffer with presentation time %1$d", presentationTimeUs));
        }
        super.renderOutputBuffer(codec, index, presentationTimeUs);
        moveDecoderOutputToEncoderInput(presentationTimeUs);
    }

    @Override
    protected void renderOutputBufferV21(MediaCodec codec, int index, long presentationTimeUs, long releaseTimeNs) {
        renderOutputBuffer(codec, index, presentationTimeUs);
//        if (VERBOSE) {
//            Log.d(TAG, String.format("Rendering output buffer with presentation time %1$d and release time %2$d", presentationTimeUs, releaseTimeNs));
//        }
//        super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
//        moveDecoderOutputToEncoderInput(presentationTimeUs);
    }

    private void moveDecoderOutputToEncoderInput(long presentationTimeUs) {
//        if (presentationTimeUs >= 0)
//        {
        if (VERBOSE) {
            Log.d(TAG, String.format("Moving Sample to encoder - render time %1$d", presentationTimeUs));
        }

        try {
            // await for new data from the decoder
            outputSurface.awaitNewImage(10000);
        } catch (RuntimeException e) {
            //TODO this will occur if the thread is killed off - need to deal with this cleaning the mess left on file system!
            Log.w(TAG, "Timeout waiting for image to become available was interrupted");
        }

        // try and render what has been sent so far to the decoder.
        outputSurface.drawImage();

        // publish the frame to the encoder surface from the decoder outputSurface
        inputSurface.setPresentationTime(presentationTimeUs * 1000);

        // move the frame to encoder
        inputSurface.swapBuffers();
//        }
    }

    @Override
    protected void onStreamChanged(Format[] formats, long offsetUs) throws ExoPlaybackException {
        SampleStream stream = getStream();
        if (!(stream instanceof SampleStreamWrapper)) {
            replaceStream(formats, new SampleStreamWrapper(stream, mediaMuxerControl) {
                @Override
                boolean isMediaMuxerBeingConfigured(MediaMuxerControl mediaMuxerControl) {
                    return mediaMuxerControl.isVideoConfigured() && !mediaMuxerControl.isConfigured();
                }
            }, offsetUs);
        }
        super.onStreamChanged(formats, offsetUs);
    }

    private boolean processEncoderOutput() {

        final int TIMEOUT_USEC = 1000;

        if (encoder == null) {
            Log.d(TAG, "No encoder to pass data to");
            return false;
        }

        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();


        boolean wroteData = false;
        boolean encoderOutputAvailable = true;
        int loopCnt = 0;
        boolean outputDone = false;
        while (encoderOutputAvailable) {
            loopCnt++;
            if (VERBOSE) {
                Log.d(TAG, "reading output from encoder if available [" + loopCnt + "]");
            }
            // Start by draining any pending output from the encoder.  It's important to
            // do this before we try to stuff any more data in.
            int encoderOutputBufferId = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
            //MediaFormat bufferFormat = encoder.getOutputFormat(outputBufferId);


            if (encoderOutputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (VERBOSE) {
                    Log.d(TAG, "no output from encoder available [" + loopCnt + "]");
                }
                encoderOutputAvailable = false;
            } else if (encoderOutputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                encoderOutputBuffers = encoder.getOutputBuffers();
                if (VERBOSE) {
                    Log.d(TAG, "encoder output buffers changed [" + loopCnt + "]");
                }
            } else if (encoderOutputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = encoder.getOutputFormat();
                if (VERBOSE) {
                    Log.d(TAG, "encoder output format changed: " + newFormat + " [" + loopCnt + "]");
                }
                if (mediaMuxerControl.hasVideoTrack()) {
                    if (VERBOSE) {
                        Log.e(TAG, "Skipping encoder output format swap to : " + newFormat);
                    }
                }
            } else if (encoderOutputBufferId < 0) {
                throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderOutputBufferId + " [" + loopCnt + "]");
            } else { // encoderStatus >= 0

                // There is data available to read from the encoder output buffer.
                if (VERBOSE) {
                    Log.d(TAG, "Output from encoder available [" + loopCnt + "]");
                }

                ByteBuffer encodedData = encoderOutputBuffers[encoderOutputBufferId];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderOutputBufferId + " was null  [" + loopCnt + "]");
                }
                // Write the data to the output media muxer
                if (info.size != 0) {
                    encodedData.position(info.offset);
                    encodedData.limit(info.offset + info.size);

                    if (!mediaMuxerControl.hasVideoTrack()) {
                        mediaMuxerControl.addVideoTrack(encoder.getOutputFormat());
                        mediaMuxerControl.markVideoConfigured();
                    }

                    if (!mediaMuxerStarted) {
                        try {
                            mediaMuxerStarted = true;
                            // the media muxer is ready to start accepting data now from the encoder
                            mediaMuxerControl.startMediaMuxer();
                        } catch (IllegalStateException e) {
                            Log.d(TAG, "video: error starting media muxer", e);
                        }
                    }
                    if (VERBOSE) {
                        Log.d(TAG, String.format("writing sample video data for frame %1$d : [%2$d]", samplesTranscoded, info.presentationTimeUs));
                    }
                    currentPositionUs = info.presentationTimeUs;


                    encodedBytes += encodedData.remaining();
                    mediaMuxerControl.writeSampleData(mediaMuxerControl.getVideoTrackId(), encodedData, info);
                    wroteData = true;

                    //TODO do something with the encoder output! Maybe save it to disk? It will need to go to a Muxer to add the audio back!
                    if (VERBOSE) {
                        Log.d(TAG, "encoder output " + info.size + " bytes");
                    }
                }

                if (!outputDone) {
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                }
                encoder.releaseOutputBuffer(encoderOutputBufferId, false);
            }
        }

        if (outputDone) {
            if (outputSurface.isFrameAvailable()) {
                Log.w(TAG, "warning - output surface has unexpected frame available");
            }
            if (VERBOSE) {
                Log.d(TAG, "video encoder: EOS");
            }
            mediaMuxerControl.videoRendererStopped();
        }

        if (VERBOSE) {
            Log.d(TAG, "finished rendering and processing frame");
        }

        if (isEnded()) {
            if (VERBOSE) {
                Log.d(TAG, "Has ended : " + encodedBytes);
            }
            releaseTranscoder();
            inputMediaFormat = null;
        }
        return wroteData;
    }

    @Override
    protected void onEnabled(boolean joining) throws ExoPlaybackException {
        super.onEnabled(joining);
        mediaMuxerControl.setHasVideo();
    }

    @Override
    public boolean isEnded() {
        return super.isEnded() && !mediaMuxerControl.isHasVideo();
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

    @Override
    public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
        if (messageType == C.MSG_SET_SCALING_MODE) {
            scalingMode = (Integer) message;
        }
        // now do the normal call
        super.handleMessage(messageType, message);
    }

    @Override
    protected void onOutputFormatChanged(MediaCodec codec, MediaFormat outputFormat) {
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
                int rotatedHeight = currentWidth;
                currentWidth = currentHeight;
                currentHeight = rotatedHeight;
                currentPixelWidthHeightRatio = 1 / currentPixelWidthHeightRatio;
            }
        } else {
            // On API level 20 and below the decoder does not apply the rotation.
            currentUnappliedRotationDegrees = pendingRotationDegrees;
        }
        if (VERBOSE) {
            Log.d(TAG, "media format changing to : " + outputFormat);
        }
        releaseTranscoder();
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
        try {
            configureCompressor(getCodecMaxValues(codecInfo, format, getStreamFormats()));
        } catch (IOException e) {
            throw new RuntimeException("Unable to find desired video compression codec");
        } catch (ExoPlaybackException e) {
            throw new RuntimeException("Unable to set surface to that tied to the transcoder");
        }
    }

    private boolean configureCompressor(CodecMaxValues codecMaxValues) throws IOException, ExoPlaybackException {
        if (VERBOSE) {
            Log.d(TAG, "configuring compressor using thread : " + Thread.currentThread().getName());
        }

        currentCodecMaxValues = codecMaxValues;
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


        DecodeEditEncodeTest.VideoChunks outputData = new DecodeEditEncodeTest.VideoChunks();
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

//                            MediaFormat trackInputTrackFormat = null;
//                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
//                                trackInputTrackFormat = getCodec().getInputFormat();
//                            }

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
        if (inputMediaFormat.containsKey(MediaFormat.KEY_ROTATION)) {
            // it gets rotated by exoplayer so this isn't needed now.
            outputFormat.setInteger(MediaFormat.KEY_ROTATION, inputMediaFormat.getInteger(MediaFormat.KEY_ROTATION));
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (inputMediaFormat.containsKey(MediaFormat.KEY_BITRATE_MODE)) {
                // it gets rotated by exoplayer so this isn't needed now.
                outputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, inputMediaFormat.getInteger(MediaFormat.KEY_BITRATE_MODE));
            } else {
                outputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, wantedBitRateModeV21);
            }
        }


        outputData.setMediaFormat(outputFormat);
        encoder = MediaCodec.createEncoderByType(MIME_TYPE);
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encodedBytes = 0;


//                            mediaMuxer.setOrientationHint(0);
        //mediaMuxer.setLocation(lat, long);

        if (inputSurface != null) {
            inputSurface.release();
        }
        if (outputSurface != null) {
            outputSurface.release();
        }
        inputSurface = new InputSurface(encoder.createInputSurface());
        inputSurface.makeCurrent();
        // the encoder is now ready to start accepting data from the input surface as it arrives (one frame at a time)
        encoder.start();

        outputSurface = new OutputSurface();
        // ensure the decoded data gets written to this surface
        handleMessage(C.MSG_SET_SURFACE, outputSurface.getSurface());

        return true;
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
    public MediaClock getMediaClock() {
        if (compressionSettings.isNeedMediaClock()) {
            return this;
        } else {
            return super.getMediaClock();
        }
    }

    @Override
    public long getPositionUs() {
        return currentPositionUs;
    }

    @Override
    public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
        return this.playbackParameters;
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
        return playbackParameters;
    }
}
