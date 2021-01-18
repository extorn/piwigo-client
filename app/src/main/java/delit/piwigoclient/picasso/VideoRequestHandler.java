package delit.piwigoclient.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.TextureView;

import androidx.core.content.MimeTypeFilter;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.business.video.ExoPlayerEventAdapter;
import delit.piwigoclient.business.video.capture.FrameCapturer;
import delit.piwigoclient.business.video.capture.ExoPlayerFrameCapture;
import delit.piwigoclient.business.video.opengl.OutputSurface;
import delit.piwigoclient.util.BitmapUtils;

public class VideoRequestHandler extends RequestHandler {

    private static final String TAG = "VideoRequestHandler";
    private final Context context;

    public VideoRequestHandler(Context context) {
        this.context = context;
    }

    @Override
    public boolean canHandleRequest(Request data) {
        String mimeType = IOUtils.getMimeType(context, data.uri);
        return MimeTypeFilter.matches(mimeType, "video/*");
    }

    @Override
    public Result load(Request data, int networkPolicy) {
        Bitmap bm;
        /*if(NetworkPolicy.shouldReadFromDiskCache(networkPolicy)) {
            bm = getPicassoSingleton().getCache().get(data.stableKey);
        }*/
        bm = buildBitmapForVideo(data);
//            if(NetworkPolicy.shouldWriteToDiskCache(networkPolicy)) {
//                    getPicassoSingleton().getCache().set(data.stableKey, bm);
//            }
        if(bm == null) {
            return null;
        }
        return new Result(bm, Picasso.LoadedFrom.DISK);
    }

    private Bitmap buildBitmapForVideo(Request data) {
        ExoPlayerFrameCapture frameCapture = new ExoPlayerFrameCapture();
        FrameCapturer frameCapturer = new FrameCapturer(data.uri, 1);
        try {
            frameCapture.invokeFrameCapture(context, frameCapturer, true).join();
        } catch (InterruptedException e) {
            Logging.log(Log.ERROR, TAG, "Unable to create a video thumbnail for file : " + data.uri.getPath());
            return null;
        }
        Bitmap bm = frameCapturer.getFrame();
        if (bm == null) {
            Logging.log(Log.ERROR, TAG, "Unable to create a video thumbnail for file : " + data.uri.getPath());
            return null;
        } else {
            Bitmap originalBm = bm;
            bm = BitmapUtils.getResizedCenterFittedBitmap(originalBm, data.targetWidth, data.targetHeight);
            originalBm.recycle();

            Log.d(TAG, "Created a video thumbnail for file : " + data.uri.getPath());
        }
        return bm;
    }

}