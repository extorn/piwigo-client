package delit.piwigoclient.business.video.capture;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.MediaCodec;
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
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import delit.piwigoclient.business.video.compression.OutputSurface;

//import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;

/**
 * This doesn't seem to work - issue with the link to exoplayer I think... It's kind of there, but not completely. Very weird error.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoFrameCaptureRenderer extends MediaCodecVideoRenderer {

    private final static boolean VERBOSE_LOGGING = true;
    private static final String TAG = "FrameCaptureVidRenderer";
    private static final String KEY_ROTATION = "rotation-degrees"; // MediaFormat.KEY_ROTATION @since Api.23
    private OutputSurface decoderOutputSurface;
    private boolean codecNeedsInit = true;
    private HandlerThread ht = new HandlerThread("surface callbacks handler");
    private MediaFormat mediaFormat;
    private FrameHandler frameListener;
    private int pendingRotationDegrees;

    public VideoFrameCaptureRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify, FrameHandler frameListener) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, maxDroppedFramesToNotify);
        this.frameListener = frameListener;
    }

    @Override
    protected void onDisabled() {
        super.onDisabled();
        releaseTranscoder(); // can't do when we release the codec as that is called when we set surface (in configureTranscoder)
        ht.quitSafely();
        Log.d(TAG, "Codec released");
    }

    @Override
    public boolean isEnded() {
        return super.isEnded();
    }

    @Override
    protected boolean shouldDropBuffersToKeyframe(long earlyUs, long elapsedRealtimeUs) {
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
        return false; // never drop a buffer
    }

    @Override
    protected void skipOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
        // never skip a buffer
    }

    @Override
    protected boolean shouldForceRenderOutputBuffer(long earlyUs, long elapsedSinceLastRenderUs) {
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

    /**
     * Renders the output buffer with the specified index. This method is only called if the platform
     * API version of the device is less than 21.
     *
     * @param codec              The codec that owns the output buffer.
     * @param index              The index of the output buffer to drop.
     * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
     */
    protected void renderOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
        super.renderOutputBuffer(codec, index, presentationTimeUs);
        processDecodedSurfaceData(presentationTimeUs);
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
        super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
        processDecodedSurfaceData(presentationTimeUs);
    }


    private void releaseTranscoder() {
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "stopping and releasing decoder");
        }
        if (decoderOutputSurface != null) {
            decoderOutputSurface.release();
            decoderOutputSurface = null;
        }
    }

    private void processDecodedSurfaceData(long presentationTimeUs) {
        if(frameListener.isCaptureComplete()) {
           return; //Do nothing to save processing and battery
        }
        try {
            // await for new data from the decoder
            decoderOutputSurface.awaitNewImage();
        } catch (RuntimeException e) {
            //TODO this will occur if the thread is killed off - need to deal with this
            Log.w(TAG, "Timeout waiting for image to become available was interrupted");
        }

        // try and render what has been sent so far to the decoder.
        decoderOutputSurface.drawImage();
        if(!frameListener.isCaptureComplete()) {
            frameListener.onFrameReady(presentationTimeUs, takeCopyOfFrame());
        }
    }

    private Bitmap takeCopyOfFrame() {
        int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        ByteBuffer pixelBuf = ByteBuffer.allocateDirect(width * height * 4);
        pixelBuf.order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glReadPixels(0, 0, width, height,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf);
        //TRANSFORM BY pendingRotationDegrees
        Bitmap frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        pixelBuf.rewind();
        frame.copyPixelsFromBuffer(pixelBuf);
        if(pendingRotationDegrees != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(pendingRotationDegrees);
            Bitmap rotatedBitmap = Bitmap.createBitmap(frame, 0, 0, frame.getWidth(), frame.getHeight(), matrix, true);
            frame.recycle();
            frame = rotatedBitmap;
        }
        return frame;
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        try {
            if (isEnded()) {
                return;
            }

            if (!super.isEnded()) {
                super.render(positionUs, elapsedRealtimeUs);
            } else {
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Extractor and decoder have already ended! Nothing to do for position : " + positionUs);
                }
            }
        } catch (ExoPlaybackException e) {
            throw e;
        } catch (Exception e) {
            throw ExoPlaybackException.createForRenderer(e, getIndex());
        }
    }

    @Override
    protected void onEnabled(boolean joining) throws ExoPlaybackException {
        try {
            super.onEnabled(joining);
            ht.start();
        } catch (ExoPlaybackException e) {
            throw e;
        } catch (Exception e) {
            throw ExoPlaybackException.createForRenderer(e, getIndex());
        }
    }

    @Override
    protected MediaFormat getMediaFormat(Format format, CodecMaxValues codecMaxValues, boolean deviceNeedsAutoFrcWorkaround, int tunnelingAudioSessionId) {
        MediaFormat inputMediaFormat = super.getMediaFormat(format, codecMaxValues, deviceNeedsAutoFrcWorkaround, tunnelingAudioSessionId);
        this.mediaFormat = inputMediaFormat;

        if (inputMediaFormat.containsKey(KEY_ROTATION)) {
            //pendingRotationDegrees = inputMediaFormat.getInteger(KEY_ROTATION);

            if (Util.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // On API level 21 and above the decoder applies the rotation when rendering to the surface - prevent this auto-rotation.
                if (inputMediaFormat.containsKey(KEY_ROTATION)) {
                    boolean isLandscape = inputMediaFormat.getInteger(MediaFormat.KEY_WIDTH) > inputMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    if(inputMediaFormat.getInteger(KEY_ROTATION) == 0 && isLandscape) {
                        pendingRotationDegrees = 90;
                    }
                }
            }
        }

        return inputMediaFormat;
    }

    @Override
    protected MediaCodecInfo getDecoderInfo(MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder) throws MediaCodecUtil.DecoderQueryException {
        MediaCodecInfo info = super.getDecoderInfo(mediaCodecSelector, format, requiresSecureDecoder);
        if(decoderOutputSurface == null) {
            decoderOutputSurface = new OutputSurface(format.width, format.height, new Handler(ht.getLooper()));
            // ensure the decoded data gets written to this surface
            try {
                handleMessage(C.MSG_SET_SURFACE, decoderOutputSurface.getSurface());
            } catch (ExoPlaybackException e) {
                // this shouldn't ever occur as its an internal well used library call
                throw new RuntimeException("Error setting decoder output surface", e);
            }
        }
        return info;
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
}
