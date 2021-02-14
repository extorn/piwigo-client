package delit.piwigoclient.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.core.content.MimeTypeFilter;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.video.capture.ExoPlayerFrameCapture;
import delit.piwigoclient.business.video.capture.FrameCapturer;
import delit.piwigoclient.util.BitmapUtils;

public class PlayableMediaRequestHandler extends ResourceRequestHandler {

    private static final String TAG = "VideoRequestHandler";

    public PlayableMediaRequestHandler(Context context) {
        super(context);
    }

    @Override
    public boolean canHandleRequest(Request data) {
        String mimeType = IOUtils.getMimeType(getContext(), data.uri);
        return IOUtils.isPlayableMedia(mimeType);
    }

    @Override
    public Result load(Request data, int networkPolicy) {
        Bitmap bm;
        /*if(NetworkPolicy.shouldReadFromDiskCache(networkPolicy)) {
            bm = getPicassoSingleton().getCache().get(data.stableKey);
        }*/
        String mimeType = IOUtils.getMimeType(getContext(), data.uri);
        if(MimeTypeFilter.matches(mimeType, "video/*")) {
            bm = buildBitmapForVideo(data);
        } else if(MimeTypeFilter.matches(mimeType, "audio/*")) {
            bm = buildBitmapForAudio(data);
        } else {
            bm = null;
        }
//            if(NetworkPolicy.shouldWriteToDiskCache(networkPolicy)) {
//                    getPicassoSingleton().getCache().set(data.stableKey, bm);
//            }
        if(bm == null) {
            return null;
        }
        return new Result(bm, Picasso.LoadedFrom.DISK);
    }

    private Bitmap buildBitmapForAudio(Request data) {
        Bitmap bm = drawableToBitmap(ResourcesCompat.getDrawable(getContext().getResources(), R.drawable.ic_audio_file ,null), data.targetWidth, data.targetHeight);
        Log.d(TAG, "Created an audio thumbnail for file : " + data.uri.getPath());
        return bm;
    }

    private Bitmap buildBitmapForVideo(Request data) {
        ExoPlayerFrameCapture frameCapture = new ExoPlayerFrameCapture();
        FrameCapturer frameCapturer = new FrameCapturer(data.uri, 1);
        try {
            frameCapture.invokeFrameCapture(getContext(), frameCapturer, true).join();
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
