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
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.MimeTypeFilter;

import com.squareup.picasso.MyPicasso;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Objects;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.business.CustomImageDownloader;
import delit.piwigoclient.business.PicassoLoader;

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
                // request handler would work but it cant because it doesnt get in before the broken one!
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

        public static final int DEFAULT_WIDTH = 512;
        public static final int DEFAULT_HEIGHT = 384;
        private Context context;

        private VideoRequestHandler(Context context) {
            this.context = context;
        }

        @Override
        public boolean canHandleRequest(Request data) {
            MimeTypeMap map = MimeTypeMap.getSingleton();
            String mimeType = IOUtils.getMimeType(context, data.uri);
            if(mimeType == null) {
                String ext = MimeTypeMap.getFileExtensionFromUrl(data.uri.getPath());
                if (ext.length() == 0) {
                    ext = IOUtils.getFileExt(context, data.uri);
                }
                mimeType = map.getMimeTypeFromExtension(ext.toLowerCase());
            }
            return MimeTypeFilter.matches(mimeType, "video/*");
        }

        @Override
        public Result load(Request data, int networkPolicy) {
            Bitmap bm = null;
            /*if(NetworkPolicy.shouldReadFromDiskCache(networkPolicy)) {
                bm = getPicassoSingleton().getCache().get(data.stableKey);
            }*/
            if(bm == null) {
                bm = buildBitmapForVideo(data);
                if(NetworkPolicy.shouldWriteToDiskCache(networkPolicy)) {
//                    getPicassoSingleton().getCache().set(data.stableKey, bm);
                }
            }
            return new Result(bm, Picasso.LoadedFrom.DISK);
        }

        private Bitmap buildBitmapForVideo(Request data) {
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
                Log.e(TAG, "Unable to create a video thumbnail for file : " + data.uri.getPath());
                return null;
            } else {
                Log.d(TAG, "Created a video thumbnail for file : " + data.uri.getPath());
            }
            return bm;
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
