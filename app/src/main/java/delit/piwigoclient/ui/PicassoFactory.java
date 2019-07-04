package delit.piwigoclient.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.core.content.ContextCompat;

import com.crashlytics.android.Crashlytics;
import com.squareup.picasso.MyPicasso;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import java.util.HashMap;

import cz.msebera.android.httpclient.HttpStatus;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.CustomImageDownloader;
import delit.piwigoclient.business.PicassoLoader;

/**
 * Created by gareth on 13/07/17.
 */

public class PicassoFactory {
    private static final String TAG = "PicassoFactory";
    private static PicassoFactory instance;
    private transient MyPicasso picasso;
    private transient PicassoErrorHandler errorHandler;

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
            if (picasso == null) {
                errorHandler = new PicassoErrorHandler();
                // request handler would work but it cant because it doesnt get in before the broken one!
                picasso = new MyPicasso.Builder(context)
                        .addRequestHandler(new ResourceRequestHandler(context))
                        .addRequestHandler(new VideoRequestHandler())
                        .listener(errorHandler).downloader(getDownloader(context)).build();
            }
            return picasso;
        }
    }

    public CustomImageDownloader getDownloader(Context context) {
        CustomImageDownloader dldr = new CustomImageDownloader(context);
        dldr.addErrorDrawable(HttpStatus.SC_UNAUTHORIZED, R.drawable.ic_image_locked_black_240dp);
        dldr.addErrorDrawable(HttpStatus.SC_NOT_FOUND, R.drawable.ic_broken_image_black_240dp);
        return dldr;
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
            Crashlytics.log(Log.ERROR, TAG, String.format("There was already a Uri Load Listener registered for Uri %1$s", uri));
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

    class VideoRequestHandler extends RequestHandler {

        public final String SCHEME_VIDEO = "video";

        @Override
        public boolean canHandleRequest(Request data) {
            MimeTypeMap map = MimeTypeMap.getSingleton();
            String ext = MimeTypeMap.getFileExtensionFromUrl(data.uri.getPath());
            String mimeType = map.getMimeTypeFromExtension(ext.toLowerCase());
            String scheme = data.uri.getScheme();
            boolean mimeTypeMatches = false;
            if (mimeType != null) {
                mimeTypeMatches = mimeType.startsWith(SCHEME_VIDEO + '/');
            }
            return mimeTypeMatches || (SCHEME_VIDEO.equals(scheme));
        }

        @Override
        public Result load(Request data, int networkPolicy) {
            Bitmap bm = ThumbnailUtils.createVideoThumbnail(data.uri.getPath(), MediaStore.Images.Thumbnails.MINI_KIND);
            if (bm == null) {
                Log.e(TAG, "Unable to create a video thumbnail for file : " + data.uri.getPath());
                return null;
            } else {
                Log.d(TAG, "Created a video thumbnail for file : " + data.uri.getPath());
            }
            return new Result(bm, Picasso.LoadedFrom.DISK);
        }
    }

    class ResourceRequestHandler extends RequestHandler {
        private final Context context;

        ResourceRequestHandler(Context context) {
            this.context = context;
        }

        public boolean canHandleRequest(Request data) {
            return data.resourceId != 0 || "android.resource".equals(data.uri.getScheme());
        }

        public Result load(Request data, int networkPolicy) {
            Drawable d = ContextCompat.getDrawable(context, data.resourceId);
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
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
            } else {
                bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            }

            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        }


    }

    private class PicassoErrorHandler implements Picasso.Listener {

        private final HashMap<Uri, EnhancedPicassoListener> listeners = new HashMap<>();

        public EnhancedPicassoListener addListener(Uri uri, EnhancedPicassoListener listener) {
            synchronized (this) {
                EnhancedPicassoListener old = listeners.put(uri, listener);
                for (EnhancedPicassoListener l : listeners.values()) {
                    if (!l.isLikelyStillNeeded()) {
                        Crashlytics.log(Log.ERROR, TAG, String.format("Listener is probably obsolete: %1$s", l.getListenerPurpose()));
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
