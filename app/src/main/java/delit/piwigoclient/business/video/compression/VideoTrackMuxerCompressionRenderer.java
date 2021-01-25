package delit.piwigoclient.business.video.compression;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
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
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import delit.piwigoclient.business.video.opengl.InputSurface;
import delit.piwigoclient.business.video.opengl.OutputSurface;

//import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;

/**
 * This doesn't seem to work - issue with the link to exoplayer I think... It's kind of there, but not completely. Very weird error.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoTrackMuxerCompressionRenderer extends MediaCodecVideoRenderer implements MediaClock {
    public static final String KEY_ROTATION = "rotation-degrees";
    private static final String MIME_TYPE = "video/avc"; //API19+ MediaFormat.MIMETYPE_VIDEO_AVC;    // H.264 Advanced Video Coding
    //    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_MPEG4; // MPEG4 format
    private final static boolean VERBOSE_LOGGING = false;
    private final MediaMuxerControl mediaMuxerControl;
    private static final String TAG = "CompressionVidRenderer";
    private final ExoPlayerCompression.VideoCompressionParameters compressionSettings;
    private int pendingRotationDegrees;
    private LinkedHashMap<Long,Integer> sampleTimeSizeMap = new LinkedHashMap<>(100);
    private OutputSurface decoderOutputSurface;
    private MediaCodec encoder;
    private boolean encoderInputEndedSignalled;
    private PlaybackParameters playbackParameters;
    private InputSurface encoderInputSurface;
    private Queue<Long> framesQueuedInEncoder = new LinkedList<>();
    private boolean codecNeedsInit = true;
    private HandlerThread ht = new HandlerThread("surface callbacks handler");
    private boolean processedSourceDataDuringRender;
    private int steppedWithoutActionCount;

    public VideoTrackMuxerCompressionRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify, MediaMuxerControl mediaMuxerControl, ExoPlayerCompression.VideoCompressionParameters compressionSettings) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, maxDroppedFramesToNotify);
        this.mediaMuxerControl = mediaMuxerControl;
        this.compressionSettings = compressionSettings;
    }

    @Override
    protected void onDisabled() {
        super.onDisabled();
        releaseTranscoder(); // can't do when we release the codec as that is called when we set surface (in configureTranscoder)
        ht.quitSafely();
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
        if (VERBOSE_LOGGING) {
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
            if (VERBOSE_LOGGING) {
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
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "releasing decoding codec");
        }
        codecNeedsInit = true;
        super.releaseCodec();
    }

    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
        sampleTimeSizeMap.put(buffer.timeUs, buffer.data.remaining());
        super.onQueueInputBuffer(buffer);
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private android.media.MediaCodecInfo selectEncoderCodecV21(String mimeType) {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (android.media.MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
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
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
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
        if (VERBOSE_LOGGING) {
            Log.e(TAG, "Rendering to encoder input surface " + presentationTimeUs + " " + releaseTimeNs);
        }
        processEncoderOutput();
        super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
        moveDecoderOutputToEncoderInput(presentationTimeUs);
//        renderOutputBuffer(codec, index, presentationTimeUs);
    }

    @Override
    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs, boolean shouldSkip) throws ExoPlaybackException {
        try {
            steppedWithoutActionCount = 0;

            if (mediaMuxerControl.isVideoConfigured() && !mediaMuxerControl.isConfigured()) {
                if (VERBOSE_LOGGING) {
                    Log.e(TAG, "Video Processor - deferring render until mediamuxer is configured: position " + positionUs);
                }
                return false;
            }
            if (mediaMuxerControl.isHasAudio() && mediaMuxerControl.hasVideoDataQueued()) {
                if (mediaMuxerControl.getAndResetIsSourceDataRead() && bufferPresentationTimeUs > positionUs + compressionSettings.getMaxInterleavingIntervalUs()) {
                    if (VERBOSE_LOGGING) {
                        Log.e(TAG, "Video Processor - Giving up render to audio at position " + positionUs);
                    }
                    return false;
                }
            }
        } catch (Exception e) {
            throw ExoPlaybackException.createForRenderer(e, getIndex());
        }
        processedSourceDataDuringRender = super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags, bufferPresentationTimeUs, shouldSkip && compressionSettings.isAllowSkippingFrames());
        return processedSourceDataDuringRender;
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
        if (VERBOSE_LOGGING) {
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
        try {
            super.renderToEndOfStream();
            if (super.isEnded()) { // if the output stream has finished.
                // if the encoder is running - signal end of encoding stream
                if (!encoderInputEndedSignalled) {
                    if (VERBOSE_LOGGING) {
                        Log.d(TAG, "Video encoder EOS signalled");
                    }
                    encoder.signalEndOfInputStream();
                    encoderInputEndedSignalled = true;
                }
            }
        } catch (ExoPlaybackException e) {
            throw e;
        } catch (Exception e) {
            throw ExoPlaybackException.createForRenderer(e, getIndex());
        }
    }

    private void moveDecoderOutputToEncoderInput(long presentationTimeUs) {

        framesQueuedInEncoder.add(presentationTimeUs);

        if (VERBOSE_LOGGING) {
            Log.d(TAG, String.format("Moving Sample to encoder - render time %1$d", presentationTimeUs));
        }

        try {
            // await for new data from the decoder
            decoderOutputSurface.awaitNewImage();
        } catch (RuntimeException e) {
            //TODO this will occur if the thread is killed off - need to deal with this cleaning the mess left on file system!
            Log.w(TAG, "Timeout waiting for image to become available was interrupted");
        }

        // try and render what has been sent so far to the decoder.
        decoderOutputSurface.drawImage();

        //takeCopyOfFrame();

        // publish the frame to the encoder surface from the decoder decoderOutputSurface
        encoderInputSurface.setPresentationTime(presentationTimeUs * 1000);

        // move the frame to encoder
        encoderInputSurface.swapBuffers();

//        }
    }

    private Bitmap takeCopyOfFrame() {
        MediaFormat mediaFormat = mediaMuxerControl.getTrueVideoInputFormat();
        int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        ByteBuffer pixelBuf = ByteBuffer.allocateDirect(width * height * 4);
        pixelBuf.order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glReadPixels(0, 0, width, height,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf);
//                        byte[] pixels = pixelBuf.array();

//                        Bitmap frame = BitmapFactory.decodeByteArray(pixels,0,pixels.length);
        Bitmap frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        pixelBuf.rewind();
        frame.copyPixelsFromBuffer(pixelBuf);
        return frame;
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        try {
            steppedWithoutActionCount++;
            processedSourceDataDuringRender = false;
            if (isEnded()) {
                return;
            }

            if (encoder != null) {
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Trying to render position " + positionUs + " isEnded : " + isEnded() + " isSuperEnded : " + super.isEnded());
                }
                processEncoderOutput();
            } else {
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Examining stream for format etc at position [" + positionUs + "].  isEnded : " + isEnded() + " isSuperEnded : " + super.isEnded());
                }
            }
            if (!super.isEnded()) {
                super.render(positionUs, elapsedRealtimeUs);
            } else {
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Extractor and decoder have already ended! Nothing to do for position : " + positionUs);
                }
            }
            mediaMuxerControl.markDataRead(processedSourceDataDuringRender);
            boolean lastRendererReadDatasource = mediaMuxerControl.getAndResetIsSourceDataRead();
            if (steppedWithoutActionCount > 300) {
                if (!lastRendererReadDatasource) {
                    // Compression has crashed. Why?!
                    throw ExoPlaybackException.createForRenderer(new Exception("Compression got stuck for some reason - stopping"), getIndex());
                }
            }
        } catch (ExoPlaybackException e) {
            throw e;
        } catch (Exception e) {
            throw ExoPlaybackException.createForRenderer(e, getIndex());
        }
    }

    /**
     *
     * @return true if data written
     */
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
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "reading output from encoder if available");
            }
            // Start by draining any pending output from the encoder.  It's important to
            // do this before we try to stuff any more data in.
            int encoderOutputBufferId = encoder.dequeueOutputBuffer(info, 10 * framesQueuedInEncoder.size()); // vary the timeout to stop the decoder rushing ahead

            if (encoderOutputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                encoderOutputAvailable = false;
            } else if (encoderOutputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = encoder.getOutputFormat();
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "encoder output format changed: " + newFormat + " [" + loopCnt + "]");
                }
                if (mediaMuxerControl.hasVideoTrack()) {
                    if (VERBOSE_LOGGING) {
                        Log.e(TAG, "Skipping encoder output format swap to : " + newFormat);
                    }
                } else {
                    if (!compressionSettings.isHardRotateVideo()) {
                        mediaMuxerControl.setOrientationHint(pendingRotationDegrees);
                    }
                    mediaMuxerControl.addVideoTrack(newFormat);
                    mediaMuxerControl.markVideoConfigured();
                }
            } else if (encoderOutputBufferId < 0 && encoderOutputBufferId != MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderOutputBufferId + " [" + loopCnt + "]");
            }

            if (encoderOutputBufferId > 0) {
                // have data to process.

                mediaMuxerControl.startMediaMuxer();

                // get the data buffer
                encodedDataBuffer = getEncoderOutputBuffer(encoder, encoderOutputBufferId);

                // Write the data to the output media muxer
                if (info.size != 0) {

                    Long lastFrameQueued = framesQueuedInEncoder.remove();
                    if (lastFrameQueued != info.presentationTimeUs) {
                        throw new RuntimeException(String.format("Expected to retrieve frame %1$d but was frame %2$d", lastFrameQueued, info.presentationTimeUs));
                    }

                    if (VERBOSE_LOGGING) {
                        Log.d(TAG, String.format("writing sample video data (%2$dbytes) for frame at time [%1$d]", info.presentationTimeUs, info.size));
                    }

                    Iterator<Map.Entry<Long,Integer>> iterator = sampleTimeSizeMap.entrySet().iterator();
                    long originalBytes = 0;
                    while(iterator.hasNext()) {
                        Map.Entry<Long, Integer> entry = iterator.next();
                        if(entry.getKey() > info.presentationTimeUs) {
                            break;
                        }
                        originalBytes += entry.getValue();
                        iterator.remove();
                    }
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
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "video encoder: EOS");
            }
            mediaMuxerControl.videoRendererStopped();
        }

        if (VERBOSE_LOGGING) {
            Log.d(TAG, "finished checking and processing encoder output");
        }

        if (isEnded()) {
            releaseTranscoder();
        }
        return wroteData;
    }

    private ByteBuffer getEncoderOutputBuffer(MediaCodec encoder, int encoderOutputBufferId) {
        ByteBuffer encodedDataBuffer = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            encodedDataBuffer = encoder.getOutputBuffer(encoderOutputBufferId);
        }
        if (encodedDataBuffer == null && encoderOutputBufferId >= 0) {
            ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
            encodedDataBuffer = encoderOutputBuffers[encoderOutputBufferId];
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "encoder output buffers changed");
            }
        }
        return encodedDataBuffer;
    }

    @Override
    protected void onEnabled(boolean joining) throws ExoPlaybackException {
        try {
            super.onEnabled(joining);
            ht.start();
            mediaMuxerControl.setHasVideo();
        } catch (ExoPlaybackException e) {
            throw e;
        } catch (Exception e) {
            throw ExoPlaybackException.createForRenderer(e, getIndex());
        }
    }

    @Override
    public boolean isEnded() {
        return super.isEnded() && !mediaMuxerControl.isVideoConfigured();
    }

    @Override
    protected MediaFormat getMediaFormat(Format format, CodecMaxValues codecMaxValues, boolean deviceNeedsAutoFrcWorkaround, int tunnelingAudioSessionId) {
        MediaFormat trueFormat = mediaMuxerControl.getTrueVideoInputFormat();

        MediaFormat inputMediaFormat = super.getMediaFormat(format, codecMaxValues, deviceNeedsAutoFrcWorkaround, tunnelingAudioSessionId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setIntegerMediaFormatKey(inputMediaFormat, trueFormat, MediaFormat.KEY_PROFILE, -1);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setIntegerMediaFormatKey(inputMediaFormat, trueFormat, MediaFormat.KEY_LEVEL, -1);
        }

        if (format.colorInfo == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setIntegerMediaFormatKey(inputMediaFormat, trueFormat, MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED);
                setIntegerMediaFormatKey(inputMediaFormat, trueFormat, MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
                setIntegerMediaFormatKey(inputMediaFormat, trueFormat, MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709);
            }
            setIntegerMediaFormatKey(inputMediaFormat, trueFormat, MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        }


        if (inputMediaFormat.containsKey(KEY_ROTATION)) {
            pendingRotationDegrees = inputMediaFormat.getInteger(KEY_ROTATION);

            if (Util.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // On API level 21 and above the decoder applies the rotation when rendering to the surface - prevent this auto-rotation.
                if (inputMediaFormat.containsKey(KEY_ROTATION)) {
                    if (!compressionSettings.isHardRotateVideo()) {
                        inputMediaFormat.setInteger(KEY_ROTATION, 0);
                    }
                }
            }
        }

        return inputMediaFormat;
    }

    private void setIntegerMediaFormatKey(MediaFormat output, MediaFormat input, String key, int defaultValue) {
        if (input != null && input.containsKey(key)) {
            output.setInteger(key, input.getInteger(key));
        } else {
            output.setInteger(key, defaultValue);
        }
    }


    /**
     * Called directly prior to decoder codec init!
     *
     * @param mediaCodecSelector
     * @param format
     * @param requiresSecureDecoder
     * @return
     * @throws MediaCodecUtil.DecoderQueryException
     */
    @Override
    protected MediaCodecInfo getDecoderInfo(MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder) throws MediaCodecUtil.DecoderQueryException {
        MediaCodecInfo decoderInfo = super.getDecoderInfo(mediaCodecSelector, format, requiresSecureDecoder);
        if (decoderOutputSurface == null) {
            try {
                CodecMaxValues codecMaxValues = getCodecMaxValues(decoderInfo, format, getStreamFormats());
                MediaFormat inputMediaFormat = getMediaFormat(format, codecMaxValues, false, getConfiguration().tunnelingAudioSessionId);
                configureCompressor(format.width, format.height, inputMediaFormat);
            } catch (IOException e) {
                throw new RuntimeException("Error reconfiguring compressor", e);
            } catch (ExoPlaybackException e) {
                throw new RuntimeException("Error reconfiguring compressor", e);
            }
        }
        return decoderInfo;
    }

    @Override
    protected boolean shouldInitCodec(MediaCodecInfo codecInfo) {
        return codecNeedsInit && super.shouldInitCodec(codecInfo);
    }

    @Override
    protected void onCodecInitialized(String name, long initializedTimestampMs, long initializationDurationMs) {
        codecNeedsInit = false;
        super.onCodecInitialized(name, initializedTimestampMs, initializationDurationMs);
    }

    private boolean configureCompressor(int inputVideoWidth, int inputVideoHeight, MediaFormat inputMediaFormat) throws IOException, ExoPlaybackException {
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "configuring compressor using thread : " + Thread.currentThread().getName());
        }
        int wantedWidthPx = compressionSettings.getWantedWidthPx();
        if (wantedWidthPx < 0) {
            wantedWidthPx = inputVideoWidth;
        }
        int wantedHeightPx = compressionSettings.getWantedHeightPx();
        if (wantedHeightPx < 0) {
            wantedHeightPx = inputVideoHeight;
        }
        int wantedFrameRate = compressionSettings.getWantedFrameRate();
        int wantedKeyFrameInterval = compressionSettings.getWantedKeyFrameInterval();
        int wantedBitRate = compressionSettings.getWantedBitRate(wantedWidthPx, inputVideoHeight, wantedFrameRate);
        int wantedBitRateModeV21 = compressionSettings.getWantedBitRateModeV21();

        android.media.MediaCodecInfo encoderCodecInfo = selectEncoderCodec(MIME_TYPE);
        if (encoderCodecInfo == null) {
            if (VERBOSE_LOGGING) {
                // Don't fail CTS if they don't have an AVC codec (not here, anyway).
                Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            }
            return false;
        }
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "found codec: " + encoderCodecInfo.getName());
        }

        if (compressionSettings.isHardRotateVideo() && pendingRotationDegrees != 0 && pendingRotationDegrees != 180) {
            int tmp = wantedWidthPx;
            wantedWidthPx = wantedHeightPx;
            wantedHeightPx = tmp;
        }

        // Create an encoder format that matches the input format.  (Might be able to just
        // re-use the format used to generate the video, since we want it to be the same.)
        MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, wantedWidthPx, wantedHeightPx);
        setIntegerMediaFormatKey(outputFormat, inputMediaFormat, MediaFormat.KEY_BIT_RATE, wantedBitRate); // average bitrate in bits per second
        setIntegerMediaFormatKey(outputFormat, inputMediaFormat, MediaFormat.KEY_FRAME_RATE, wantedFrameRate); // Frames per second
        setIntegerMediaFormatKey(outputFormat, inputMediaFormat, MediaFormat.KEY_I_FRAME_INTERVAL, wantedKeyFrameInterval); // interval in seconds between key frames
        setIntegerMediaFormatKey(outputFormat, inputMediaFormat, MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        // level and profile might be set automatically?!
//        setIntegerMediaFormatKey(outputFormat, inputMediaFormat, MediaFormat.KEY_LEVEL, -1);
//        setIntegerMediaFormatKey(outputFormat, inputMediaFormat, MediaFormat.KEY_PROFILE, -1);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.media.MediaCodecInfo.CodecCapabilities capabilities = encoderCodecInfo.getCapabilitiesForType(MIME_TYPE);
            if (capabilities.getEncoderCapabilities().isBitrateModeSupported(wantedBitRateModeV21)) {
                outputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, wantedBitRateModeV21);
            }
            if (capabilities.getEncoderCapabilities().isBitrateModeSupported(android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)) {
                outputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            } else if (capabilities.getEncoderCapabilities().isBitrateModeSupported(android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)) {
                outputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
            } else if (capabilities.getEncoderCapabilities().isBitrateModeSupported(android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)) {
                outputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            }
        }

//        android.media.MediaCodecInfo.CodecCapabilities capabilities = encoderCodecInfo.getCapabilitiesForType(MIME_TYPE);
//        for(int supportedFormat : capabilities.colorFormats) {
//            if(supportedFormat == android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
//                outputFormat.setInteger( MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
//                break;
//            }
////        }
//        if(!outputFormat.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
//            setIntegerMediaFormatKey(outputFormat, inputMediaFormat, MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//        }

        encoder = MediaCodec.createByCodecName(encoderCodecInfo.getName());
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

        decoderOutputSurface = new OutputSurface(new Handler(ht.getLooper()));//new OutputSurfaceBuilder().getOutputSurface(context);
        // ensure the decoded data gets written to this surface
        handleMessage(C.MSG_SET_SURFACE, decoderOutputSurface.getSurface());
        return true;
    }

    @Override
    public MediaClock getMediaClock() {
        return this;
        // the below doesn't work - i think because the audio renderer is enabled after the video one. :-(
//        if (mediaMuxerControl.isHasAudio()) {
//            return null; // don't act like a clock
//        } else {
//            return this;
//        }
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
}
