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
import android.webkit.MimeTypeMap;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import delit.piwigoclient.business.CustomImageDownloader;

/**
 * Created by gareth on 13/07/17.
 */

public class PicassoFactory {
    private static PicassoFactory instance;
    private final Context context;
    private transient Picasso picasso;
    private transient PicassoErrorHandler errorHandler;

    public PicassoFactory(Context context) {
        this.context = context;
    }

    public synchronized static PicassoFactory initialise(Context context) {
        instance = new PicassoFactory(context);
        return instance;
    }

    ;

    public synchronized static PicassoFactory getInstance() {
        return instance;
    }

    public Picasso getPicassoSingleton() {
        synchronized (MyApplication.class) {
            if (picasso == null) {
                errorHandler = new PicassoErrorHandler();
                // request handler would work but it cant because it doesnt get in before the broken one!
                picasso = new Picasso.Builder(context).addRequestHandler(new VideoRequestHandler())/*.addRequestHandler(new ResourceRequestHandler(context))*/.listener(errorHandler).downloader(new CustomImageDownloader(context)).build();
            }
            return picasso;
        }
    }

    class VideoRequestHandler extends RequestHandler {

        public String SCHEME_VIDEO="video";
        @Override
        public boolean canHandleRequest(Request data)
        {
            MimeTypeMap map = MimeTypeMap.getSingleton();
            String ext = map.getFileExtensionFromUrl(data.uri.getPath());
            String mimeType = map.getMimeTypeFromExtension(ext);
            String scheme = data.uri.getScheme();
            boolean mimeTypeMatches = false;
            if(mimeType != null) {
                mimeTypeMatches = mimeType.startsWith(SCHEME_VIDEO+'/');
            }
            return mimeTypeMatches || (SCHEME_VIDEO.equals(scheme));
        }

        @Override
        public Result load(Request data) throws IOException
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

        public Result load(Request data) throws IOException {
            Drawable d = ContextCompat.getDrawable(context, data.resourceId);
            return new Result(drawableToBitmap(d), Picasso.LoadedFrom.DISK);
        }



        public Bitmap drawableToBitmap (Drawable drawable) {
            Bitmap bitmap = null;

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

    public synchronized void registerListener(Uri uri, Picasso.Listener listener) {
        if(errorHandler == null) {
            getPicassoSingleton();
        }
        errorHandler.addListener(uri, listener);
    }

    public synchronized void deregisterListener(Uri uri) {
        if(errorHandler == null) {
            getPicassoSingleton();
        }
        errorHandler.removeListener(uri);
    }

    private class PicassoErrorHandler implements Picasso.Listener {

        private ConcurrentHashMap<Uri, Picasso.Listener> listeners = new ConcurrentHashMap<>();

        public void addListener(Uri uri, Picasso.Listener listener) {
            listeners.put(uri, listener);
        }

        public void removeListener(Uri uri) {
            listeners.remove(uri);
        }

        @Override
        public void onImageLoadFailed(Picasso picasso, Uri uri, Exception e) {
            Picasso.Listener listener = listeners.get(uri);
            if(listener != null) {
                listener.onImageLoadFailed(picasso, uri, e);
            }
        }
    }

    public void clearPicassoCache(Context context) {
        synchronized(PicassoFactory.class) {
            if (picasso != null) {
                getPicassoSingleton().shutdown();
                picasso = null;
                initialise(context);
            }
        }
    }
}
