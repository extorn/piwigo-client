package delit.piwigoclient.business.video.capture;

import android.graphics.Bitmap;
import android.net.Uri;

import delit.piwigoclient.business.video.capture.FrameHandler;

public class FrameCapturer extends FrameHandler {
    private Bitmap frame;
    private long frameTimeUs;

    public FrameCapturer(Uri videoFileUri, int framesToCapture) {
        super(videoFileUri, framesToCapture);
    }

    @Override
    public void handleFrame(long frameTimeUs, Bitmap frame) {
        this.frameTimeUs = frameTimeUs;
        this.frame = frame;
    }

    public Bitmap getFrame() {
        return frame;
    }

    public long getFrameTimeUs() {
        return frameTimeUs;
    }
}
