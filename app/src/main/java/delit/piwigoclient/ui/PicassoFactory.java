package delit.piwigoclient.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.squareup.picasso.Downloader;
import com.squareup.picasso.MyPicasso;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

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

    public Picasso getPicassoSingleton(Context context) {
        synchronized (MyApplication.class) {
            if (picasso == null) {
                errorHandler = new PicassoErrorHandler();
                // request handler would work but it cant because it doesnt get in before the broken one!
                picasso = new MyPicasso.Builder(context).addRequestHandler(new ResourceRequestHandler(context)).addRequestHandler(new VideoRequestHandler()).listener(errorHandler).downloader(getDownloader(context)).build();
            }
            return picasso;
        }
    }

    private Downloader getDownloader(Context context) {
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

    class VideoRequestHandler extends RequestHandler {

        public final String SCHEME_VIDEO="video";
        @Override
        public boolean canHandleRequest(Request data)
        {
            MimeTypeMap map = MimeTypeMap.getSingleton();
            String ext = MimeTypeMap.getFileExtensionFromUrl(data.uri.getPath());
            String mimeType = map.getMimeTypeFromExtension(ext);
            String scheme = data.uri.getScheme();
            boolean mimeTypeMatches = false;
            if(mimeType != null) {
                mimeTypeMatches = mimeType.startsWith(SCHEME_VIDEO+'/');
            }
            return mimeTypeMatches || (SCHEME_VIDEO.equals(scheme));
        }

        @Override
        public Result load(Request data, int networkPolicy) throws IOException
        {
            Bitmap bm = ThumbnailUtils.createVideoThumbnail(data.uri.getPath(), MediaStore.Images.Thumbnails.MINI_KIND);
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

        public Result load(Request data, int networkPolicy) throws IOException {
            Drawable d = ContextCompat.getDrawable(context, data.resourceId);
            return new Result(drawableToBitmap(d), Picasso.LoadedFrom.DISK);
        }



        public Bitmap drawableToBitmap (Drawable drawable) {
            Bitmap bitmap;

            if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                if(bitmapDrawable.getBitmap() != null) {
                    return bitmapDrawable.getBitmap();
                }
            }

            if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
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

    public synchronized void registerListener(Context context, Uri uri, Picasso.Listener listener) {
        if(errorHandler == null) {
            getPicassoSingleton(context);
        }
        errorHandler.addListener(uri, listener);
    }

    public synchronized void deregisterListener(Context context, Uri uri) {
        if(errorHandler == null) {
            getPicassoSingleton(context);
        }
        errorHandler.removeListener(uri);
    }

    private class PicassoErrorHandler implements Picasso.Listener {

        private final ConcurrentHashMap<Uri, Picasso.Listener> listeners = new ConcurrentHashMap<>();

        public void addListener(Uri uri, Picasso.Listener listener) {
            listeners.put(uri, listener);
        }

        public void removeListener(Uri uri) {
            listeners.remove(uri);
        }

        @Override
        public void onImageLoadFailed(Picasso picasso, Uri uri, Exception e) {
            if(uri != null) {
                Picasso.Listener listener = listeners.get(uri);
                if (listener != null) {
                    listener.onImageLoadFailed(picasso, uri, e);
                } else if(BuildConfig.DEBUG) {
                    Log.e(TAG, String.format("Error loading uri %1$s", uri), e);
                }
            } else if(BuildConfig.DEBUG) {
                Log.e(TAG, "Error loading uri null", e);
            }
        }
    }

    public boolean clearPicassoCache(Context context, boolean forceClear) {
        synchronized(PicassoFactory.class) {
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
}
