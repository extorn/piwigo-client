package delit.piwigoclient.ui;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.squareup.picasso.MyPicasso;
import com.squareup.picasso.Picasso;

import java.lang.ref.WeakReference;
import java.util.Objects;

import delit.libs.core.util.Logging;
import delit.piwigoclient.business.CustomImageDownloader;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.picasso.EnhancedPicassoListener;
import delit.piwigoclient.picasso.PicassoErrorHandler;
import delit.piwigoclient.picasso.PlayableMediaRequestHandler;
import delit.piwigoclient.picasso.ResourceRequestHandler;

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
                        .addRequestHandler(new PlayableMediaRequestHandler(appContext))
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

}
