package delit.piwigoclient.picasso;

import android.net.Uri;
import android.util.Log;

import com.squareup.picasso.Picasso;

import java.util.HashMap;

import delit.libs.core.util.Logging;
import delit.piwigoclient.BuildConfig;

public class PicassoErrorHandler implements Picasso.Listener {

    private static final String TAG = "PicassoErrorHandler";
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
