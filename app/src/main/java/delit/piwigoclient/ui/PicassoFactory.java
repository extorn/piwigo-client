package delit.piwigoclient.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
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
import com.squareup.picasso.MyPicasso;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Objects;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.business.CustomImageDownloader;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.video.ExoPlayerEventAdapter;
import delit.piwigoclient.business.video.capture.ExoPlayerFrameCapture;
import delit.piwigoclient.business.video.capture.FrameHandler;
import delit.piwigoclient.business.video.compression.OutputSurface;

import static android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC;

/**
 * Created by gareth on 13/07/17.
 */

public class PicassoFactory {
    private static final String TAG = "PicassoFactory";
    private static PicassoFactory instance;
    private MyPicasso picasso;
    private PicassoErrorHandler errorHandler;
    private WeakReference<Context> appContextRef;

    public PicassoFactory() {
    }

    public synchronized static PicassoFactory initialise() {
        instance = new PicassoFactory();
        return instance;
    }

    public synchronized static PicassoFactory getInstance() {
        return instance;
    }

    public MyPicasso getPicassoSingleton() {
        return picasso;
    }

    public Picasso getPicassoSingleton(Context context) {
        synchronized (MyApplication.class) {
            Context appContext = context.getApplicationContext();
            appContextRef = new WeakReference<>(appContext);
            if (picasso == null) {
                errorHandler = new PicassoErrorHandler();
                // request handler would work but it can't because it doesn't get in before the broken one!
                picasso = new MyPicasso.Builder(appContext)
                        .addRequestHandler(new ResourceRequestHandler(appContext))
                        .addRequestHandler(new VideoRequestHandler(appContext))
                        .listener(errorHandler).downloader(getDownloader()).build();
            }
            return picasso;
        }
    }

    private @NonNull
    Context getAppContext() {
        return Objects.requireNonNull(appContextRef.get());
    }

    public CustomImageDownloader getDownloader() {
        return new CustomImageDownloader(getAppContext());
    }

    public int getCacheSizeBytes() {
        if (picasso == null) {
            return 0;
        } else {
            return picasso.getCacheSize();
        }
    }

    public synchronized void registerListener(Context context, Uri uri, EnhancedPicassoListener listener) {
        if (errorHandler == null) {
            getPicassoSingleton(context);
        }
        EnhancedPicassoListener old = errorHandler.addListener(uri, listener);
        if (old != null) {
            Logging.log(Log.ERROR, TAG, String.format("There was already a Uri Load Listener registered for Uri %1$s", uri));
        }
//        if (BuildConfig.DEBUG) {
//            Log.d(TAG, String.format("There are %1$d Uri Load Listeners registered", errorHandler.listeners.size()));
//        }
    }

    public synchronized void deregisterListener(Context context, Uri uri) {
        if (errorHandler == null) {
            getPicassoSingleton(context);
        }
        if (errorHandler.removeListener(uri)) {
//            if (BuildConfig.DEBUG) {
//                Log.d(TAG, String.format("There are %1$d Uri Load Listeners registered", errorHandler.listeners.size()));
//            }
        }
    }

    public interface EnhancedPicassoListener extends Picasso.Listener {
        boolean isLikelyStillNeeded();

        String getListenerPurpose();
    }

    public boolean clearPicassoCache(Context context, boolean forceClear) {
        synchronized (PicassoFactory.class) {
            if (picasso != null && (forceClear || picasso.getCacheSize() > (1024 * 1024 * 5))) { // if over 5mb of cache used
                getPicassoSingleton(context).cancelTag(PicassoLoader.PICASSO_REQUEST_TAG);
                getPicassoSingleton(context).shutdown();
                picasso = null;
                initialise();
                return true;
            }
        }
        return false;
    }

    public boolean clearPicassoCache(Context context) {
        return clearPicassoCache(context, false);
    }

    static class VideoRequestHandler extends RequestHandler {

        private Context context;

        private VideoRequestHandler(Context context) {
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
            return buildBitmapForVideoNewWay(data);
//            return buildBitmapForVideoOldWay(data);
        }

        private Bitmap buildBitmapForVideoOldWay(Request data) {
            MediaMetadataRetriever mediaRetriever = new MediaMetadataRetriever();
            Bitmap bm;
            try {
                mediaRetriever.setDataSource(context, data.uri);
                int scaleToHeight = data.targetHeight;
                int scaleToWidth = data.targetWidth;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    bm = mediaRetriever.getScaledFrameAtTime(1000, OPTION_CLOSEST_SYNC, scaleToWidth, scaleToHeight);
                } else {
                    bm = mediaRetriever.getFrameAtTime(1000);
                    if (bm != null) {
                        bm = getResizedCenterFittedBitmap(bm, scaleToWidth, scaleToHeight);
                    }
                }
            } finally {
                mediaRetriever.release();
            }
            if (bm == null) {
                Logging.log(Log.ERROR, TAG, "Unable to create a video thumbnail for file : " + data.uri.getPath());
                return null;
            } else {
                Log.d(TAG, "Created a video thumbnail for file : " + data.uri.getPath());
            }
            return bm;
        }

        private Bitmap buildBitmapForVideoNewWay(Request data) {
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
                bm = getResizedCenterFittedBitmap(bm, data.targetWidth, data.targetHeight);
                Log.d(TAG, "Created a video thumbnail for file : " + data.uri.getPath());
            }
            return bm;
        }

        static class FrameCapturer extends FrameHandler {
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

        class ExoPlayerThread extends HandlerThread {

            private Bitmap bitmap;
            private final Request data;

            public ExoPlayerThread(String name, Request data) {
                super(name);
                this.data = data;
            }

            @Override
            protected void onLooperPrepared() {
                getBitmapUsingExoPlayer(data);
            }

            public Bitmap getBitmap() {
                return bitmap;
            }

            class PlayerListener extends ExoPlayerEventAdapter {
                private final SimpleExoPlayer player;
                private final Request data;

                public PlayerListener(SimpleExoPlayer player, Request data) {
                    this.player = player;
                    this.data = data;
                }

                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    super.onPlayerStateChanged(playWhenReady, playbackState);
                    if(playbackState == 3) {
                        OutputSurface os = new OutputSurface(player.getVideoFormat().width, player.getVideoFormat().height, new Handler(getLooper()));
                        player.setVideoSurface(os.getSurface());
                        os.makeCurrent();


                        player.seekTo(Math.min(1000, player.getDuration()));
                        player.setPlayWhenReady(true);
                        if(player.getCurrentPosition() <= 100) {
                            return;
                        }

//                        boolean frameAv = os.checkForNewImage(10000);
//                        os.awaitNewImage();
                        os.drawImage();
                        player.setPlayWhenReady(false);

                        int width = player.getVideoFormat().width;
                        int height = player.getVideoFormat().height;
                        ByteBuffer pixelBuf = ByteBuffer.allocateDirect(width * height * 4);
                        pixelBuf.order(ByteOrder.LITTLE_ENDIAN);
                        GLES20.glReadPixels(0, 0, width, height,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf);
//                        byte[] pixels = pixelBuf.array();

//                        Bitmap frame = BitmapFactory.decodeByteArray(pixels,0,pixels.length);
                        Bitmap frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        pixelBuf.rewind();
                        frame.copyPixelsFromBuffer(pixelBuf);

//                        Bitmap frame = tv.getBitmap();
                        if (frame != null) {
                            int scaleToHeight = data.targetHeight;
                            int scaleToWidth = data.targetWidth;
                            frame = getResizedCenterFittedBitmap(frame, scaleToWidth, scaleToHeight);
                            ExoPlayerThread.this.bitmap = frame;
                        }
                        player.stop();
                        player.release();
                    }

                }
            }

            private void getBitmapUsingExoPlayer(Request data) {
                Uri uri = IOUtils.getTreeLinkedDocFile(context, IOUtils.getTreeUri(data.uri), data.uri).getUri();
                TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(new DefaultBandwidthMeter());
                DefaultTrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
                SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(new DefaultRenderersFactory(context), trackSelector);
                TextureView tv = new TextureView(context);
//                OutputSurface os = new OutputSurface(data.targetWidth, data.targetHeight, new Handler(getLooper()));
//                tv.setSurfaceTexture(os.getSurfaceTexture());
//                player.setVideoTextureView(tv);
//                player.setVideoSurface(os.getSurface());

                player.addListener(new PlayerListener(player, data));
                player.prepare(new ExtractorMediaSource.Factory(new DefaultDataSourceFactory(context, "na")).createMediaSource(uri));

//                player.setPlayWhenReady(true);
            }
        }

        public Bitmap getResizedCenterFittedBitmap(Bitmap bm, int newWidth, int newHeight) {
            int width = bm.getWidth();
            int height = bm.getHeight();

            // CREATE A MATRIX FOR THE MANIPULATION
            Matrix matrix = new Matrix();
            RectF from = new RectF();
            from.set(new Rect(0,0,width, height));
            RectF to = new RectF();
            to.set(new Rect(0,0,newWidth, newHeight));
            matrix.setRectToRect(from, to, Matrix.ScaleToFit.CENTER);

            // "RECREATE" THE NEW BITMAP
            Bitmap resizedBitmap = Bitmap.createBitmap(
                    bm, 0, 0, width, height, matrix, false);
            bm.recycle();
            return resizedBitmap;
        }

    }

    static class ResourceRequestHandler extends RequestHandler {
        private final Context context;

        ResourceRequestHandler(Context context) {
            this.context = context;
        }

        public boolean canHandleRequest(Request data) {
            return data.resourceId != 0 || "android.resource".equals(data.uri.getScheme());
        }

        public Result load(Request data, int networkPolicy) {
            Drawable d = AppCompatResources.getDrawable(context, data.resourceId);
            return new Result(drawableToBitmap(d), Picasso.LoadedFrom.DISK);
        }


        public Bitmap drawableToBitmap(Drawable drawable) {
            Bitmap bitmap;

            if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                if (bitmapDrawable.getBitmap() != null) {
                    return bitmapDrawable.getBitmap();
                }
            }

            if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single colors bitmap will be created of 1x1 pixel
            } else {
                bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            }

            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        }


    }

    private static class PicassoErrorHandler implements Picasso.Listener {

        private final HashMap<Uri, EnhancedPicassoListener> listeners = new HashMap<>();

        public EnhancedPicassoListener addListener(Uri uri, EnhancedPicassoListener listener) {
            synchronized (this) {
                EnhancedPicassoListener old = listeners.put(uri, listener);
                for (EnhancedPicassoListener l : listeners.values()) {
                    if (!l.isLikelyStillNeeded()) {
                        Logging.log(Log.ERROR, TAG, String.format("Listener is probably obsolete: %1$s", l.getListenerPurpose()));
                    }
                }
                return old;
            }
        }

        public boolean removeListener(Uri uri) {
            synchronized (this) {
                return null != listeners.remove(uri);
            }
        }

        @Override
        public void onImageLoadFailed(Picasso picasso, Uri uri, Exception e) {
            synchronized (this) {
                if (uri != null) {
                    EnhancedPicassoListener listener = listeners.get(uri);
                    if (listener != null) {
                        listener.onImageLoadFailed(picasso, uri, e);
                    } else if (BuildConfig.DEBUG) {
                        Log.e(TAG, String.format("Error loading uri %1$s", uri), e);
                    }
                } else if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error loading uri null", e);
                }
            }
        }
    }
}
