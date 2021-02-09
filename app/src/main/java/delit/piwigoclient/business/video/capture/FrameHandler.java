package delit.piwigoclient.business.video.capture;

import android.graphics.Bitmap;
import android.net.Uri;

public abstract class FrameHandler implements ExoPlayerFrameCapture.FrameCaptureListener {
    private Uri videoFileUri;
    private ExoPlayerFrameCapture.FrameCaptureListener listener;
    private final int framesToCapture;
    private int framesCaptured;

    public FrameHandler(Uri videoFileUri, int framesToCapture) {
        this.videoFileUri = videoFileUri;
        this.framesToCapture = framesToCapture;
    }

    public void setListener(ExoPlayerFrameCapture.FrameCaptureListener listener) {
        this.listener = listener;
    }

    public void onFrameReady(long frameTimeUs, Bitmap frame) {
        framesCaptured++;
        handleFrame(frameTimeUs, frame);
    }

    public abstract void handleFrame(long frameTimeUs, Bitmap frame);

    public boolean isCaptureComplete() {
        boolean complete = framesCaptured == framesToCapture;
        if(complete) {
            listener.onCaptureComplete(videoFileUri);
        }
        return complete;
    }

    public Uri getVideoFileUri() {
        return videoFileUri;
    }

    public ExoPlayerFrameCapture.FrameCaptureListener getListener() {
        return listener;
    }

    @Override
    public void onCaptureStarted(Uri inputFile) {
        // do nothing by default
    }

    @Override
    public void onCaptureComplete(Uri inputFile) {
        // do nothing by default
    }

    @Override
    public void onCaptureError(Uri inputFile, Exception e) {
        // do nothing by default
    }
}
