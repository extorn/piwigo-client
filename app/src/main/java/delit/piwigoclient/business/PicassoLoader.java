package delit.piwigoclient.business;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import com.crashlytics.android.Crashlytics;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Transformation;

import java.io.File;
import java.io.IOException;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.util.DisplayUtils;
import pl.droidsonroids.gif.GifDrawable;

/**
 * Created by gareth on 11/10/17.
 */
public class PicassoLoader<T extends ImageView> implements Callback, DownloaderListener, PicassoFactory.EnhancedPicassoListener {

    public final static int INFINITE_AUTO_RETRIES = -1;
    public static final String PICASSO_REQUEST_TAG = "PIWIGO";
    private final static int DEFAULT_AUTO_RETRIES = 1;
    private final T loadInto;
    private String uriToLoad;
    private File fileToLoad;
    private int maxRetries = DEFAULT_AUTO_RETRIES;
    private int retries = 0;
    private boolean imageLoaded;
    private boolean imageLoading;
    private float rotation = 0;
    private int resourceToLoad = Integer.MIN_VALUE;
    private boolean imageUnavailable;
    private @DrawableRes
    int placeholderPlaceholderId = R.drawable.ic_file_gray_24dp;
    private String placeholderUri;
    private boolean placeholderLoaded;
    private @DrawableRes
    int errorResourceId = R.drawable.ic_error_black_240px;
    private Transformation transformation;
    private PictureItemImageLoaderListener listener;
    private boolean usePlaceholderIfError = false;
    private String lastLoadError;
    private boolean waitForErrorMessage;

    public PicassoLoader(T loadInto) {
        this(loadInto, null);
    }

    public PicassoLoader(T loadInto, PictureItemImageLoaderListener listener) {
        this.loadInto = loadInto;
        this.listener = listener;
    }

    @Override
    public void onImageDownloadError(String message) {
        lastLoadError = message;
    }

    public String getLastLoadError() {
        return lastLoadError;
    }

    /**
     * Called by the Loader registered with the PicassoFactory only! Otherwise we extract the error direct from the response handler
     *
     * @param picasso
     * @param uri
     * @param exception
     */
    @Override
    public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception) {
        lastLoadError = exception.getMessage();
        waitForErrorMessage = false;
        onError();
        if (!(exception instanceof Downloader.ResponseException)) {
            Crashlytics.log(Log.ERROR, "PicassoLoader", "Unexpected error loading image");
            Crashlytics.logException(exception);
        }
    }

    @Override
    public boolean isLikelyStillNeeded() {
        return loadInto != null;
    }

    @Override
    public String getListenerPurpose() {
        if (!placeholderLoaded && placeholderUri != null) {
            return "placeholder for " + loadInto.getContentDescription();
        }
        return "real image for " + loadInto.getContentDescription();
    }

    public void setUsePlaceholderIfError(boolean usePlaceholderIfError) {
        this.usePlaceholderIfError = usePlaceholderIfError;
    }

    @Override
    public final void onSuccess() {
        imageLoading = false;

        if (deregisterUriLoadListener(true)) {
            load();
            return;
        }
        imageLoaded = true;
        onImageLoad(true);

    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public final void onError() {
        if (waitForErrorMessage) {
            // this means we're registered as a listener with picasso to allow us to
            // extract the error message (will call onError once that is available)
            return;
        }
        deregisterUriLoadListener(false);
        imageLoading = false;
        onImageLoad(false);
        if (maxRetries >= 0 && retries >= maxRetries) {
            imageUnavailable = true;
            onImageUnavailable();
        } else {
            retries++;
            load();
        }
    }

    public void withErrorDrawable(@DrawableRes int errorDrawable) {
        this.errorResourceId = errorDrawable;
    }

    public boolean hasPlaceholder() {
        return placeholderLoaded;
    }

    public boolean isImageLoaded() {
        return imageLoaded;
    }

    public final void load() {
        if (listener != null) {
            listener.onBeforeImageLoad(this);
        }
        load(false);
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    public boolean isImageLoading() {
        return imageLoading;
    }

    public void loadNoCache() {
        if (listener != null) {
            listener.onBeforeImageLoad(this);
        }
        load(true);
    }

    protected final void onImageLoad(boolean success) {
        if (listener != null) {
            listener.onImageLoaded(this, success);
        }
    }

    protected void onImageUnavailable() {
        if (listener != null) {
            listener.onImageUnavailable(this, lastLoadError);
        }
    }

    protected final void load(boolean forceServerRequest) {
        synchronized (this) {
            if (imageLoading) {
                return;
            }
            imageLoading = true;
            //TODO this hack isn't needed if the resource isnt a vector drawable... later version of com.squareup.picasso?
            if (resourceToLoad != Integer.MIN_VALUE) {
                getLoadInto().setImageResource(getResourceToLoad());
                onSuccess();
            } else {
                PicassoFactory.getInstance().getPicassoSingleton(getContext()).cancelRequest(loadInto);
                if(placeholderUri != null && placeholderLoaded && isLoadingGif()) {
                    GifLoaderTask task = new GifLoaderTask();
                    if (DisplayUtils.isRunningOnUIThread()) {
                        task.execute(this);
                    } else {
                        task.doInBackground(this);
                    }
                } else {
                    runLoad(forceServerRequest);
                }
            }
        }
    }

    private void runLoad(boolean forceServerRequest) {
        try {
            waitForErrorMessage = true;
            RequestCreator loader = customiseLoader(buildLoader());
            if (forceServerRequest) {
                loader.memoryPolicy(MemoryPolicy.NO_CACHE);
                loader.networkPolicy(NetworkPolicy.NO_CACHE);
            }
            //                if(placeholderUri != null) {
            //                    Log.d("PicassoLoader", "Loading: " + placeholderUri, new Exception().fillInStackTrace());
            //                } else {
            //                    Log.d("PicassoLoader", "Loading: " + uriToLoad, new Exception().fillInStackTrace());
            //                }
            registerUriLoadListener();
            loader.into(loadInto, this);
        } catch (IllegalStateException e) {
            if (!placeholderLoaded) {
                throw e;
            }
        }
    }

    public void cancelImageLoadIfRunning() {
        synchronized (this) {
            deregisterUriLoadListener(false);
            if (!imageLoading) {
                return;
            }
            if (loadInto != null) {
                PicassoFactory.getInstance().getPicassoSingleton(getContext()).cancelRequest(loadInto);
            }
            imageLoading = false;
        }
    }

    private boolean isLoadingGif() {
        String uri = getUriToLoad();
        if(uri == null) {
            return false;
        }
        uri = uri.toLowerCase();
        int idx = uri.lastIndexOf(".gif");
        if(idx < 0) {
            return false;
        }
        return uri.length() == idx + 4 || uri.charAt(idx+4) == '?';
    }

    protected final RequestCreator buildLoader() {
        RequestCreator rc = buildRequestCreator(PicassoFactory.getInstance().getPicassoSingleton(getContext()));
        if (!placeholderLoaded || !usePlaceholderIfError) {
            rc.error(errorResourceId);
        }
        if (transformation != null) {
            rc.transform(transformation);
        }
        if (placeholderLoaded) {
            rc.noPlaceholder();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                rc.placeholder(placeholderPlaceholderId);
            } else {
                rc.placeholder(ContextCompat.getDrawable(loadInto.getContext(), placeholderPlaceholderId));
            }
        }

        if (placeholderLoaded && Math.abs(rotation) > Float.MIN_NORMAL) {
            rc.rotate(rotation);
        }
        rc.tag(PICASSO_REQUEST_TAG);
        return rc;
    }

    private Context getContext() {
        Context context = loadInto.getContext();
        if (context == null) {
            throw new IllegalStateException("Context is not available in the view at this time");
        }
        return context;
    }

    public void resetAll() {
        resetLoadState();
        resetImageToLoad();
        maxRetries = DEFAULT_AUTO_RETRIES;
    }

    private RequestCreator buildRequestCreator(Picasso picassoSingleton) {
        if (!placeholderLoaded && placeholderUri != null) {
            return picassoSingleton.load(placeholderUri);
        }


        if (uriToLoad != null) {
            return picassoSingleton.load(uriToLoad);
        } else if (fileToLoad != null) {
            return picassoSingleton.load(fileToLoad); // convert to uri to allow using MediaStore data (useful for video thumbnails)
        } else if (resourceToLoad != Integer.MIN_VALUE) {
            return picassoSingleton.load(resourceToLoad);
        }

        Crashlytics.log(Log.ERROR, "PicassoLoader", "No valid source specified from which to load image");
        throw new IllegalStateException("No valid source specified from which to load image");
    }

    protected RequestCreator customiseLoader(RequestCreator placeholder) {
        return placeholder;
    }

    protected void resetImageToLoad() {
        fileToLoad = null;
        resourceToLoad = Integer.MIN_VALUE;
        uriToLoad = null;
        rotation = 0;
    }

    public void resetLoadState() {
        cancelImageLoadIfRunning();
        retries = 0;
        imageLoaded = false;
        imageUnavailable = false;
        placeholderLoaded = false;
        lastLoadError = null;
    }

    public boolean isImageUnavailable() {
        return imageUnavailable;
    }

    public void setUriToLoad(String uriToLoad) {
        resetLoadState();
        this.uriToLoad = uriToLoad;
        this.resourceToLoad = Integer.MIN_VALUE;
        this.fileToLoad = null;
    }

    protected int getResourceToLoad() {
        return resourceToLoad;
    }

    public void setResourceToLoad(int resourceToLoad) {
        this.uriToLoad = null;
        this.resourceToLoad = resourceToLoad;
        this.fileToLoad = null;
        resetLoadState();
    }

    protected File getFileToLoad() {
        return fileToLoad;
    }

    public void setFileToLoad(File fileToLoad) {
        this.uriToLoad = null;
        this.resourceToLoad = Integer.MIN_VALUE;
        this.fileToLoad = fileToLoad;
        resetLoadState();
    }

    protected String getUriToLoad() {
        return uriToLoad;
    }

    protected T getLoadInto() {
        return loadInto;
    }

    public boolean hasResourceToLoad() {
        return uriToLoad != null || fileToLoad != null || resourceToLoad > Integer.MIN_VALUE;
    }

    /**
     * @return true if the placeholder was loaded so need to re-call load
     */
    private boolean deregisterUriLoadListener(boolean success) {
        if (placeholderUri != null && !placeholderLoaded) {
            placeholderLoaded = success;
            PicassoFactory.getInstance().deregisterListener(getContext(), Uri.parse(placeholderUri));
            return true;
        }
        if (uriToLoad != null) {
            PicassoFactory.getInstance().deregisterListener(getContext(), Uri.parse(uriToLoad));
        }
        return false;
    }

    public void setPlaceholderImageUri(String placeholderUri) {
        this.placeholderUri = placeholderUri;
    }

    public void withTransformation(Transformation transformation) {
        this.transformation = transformation;
    }

    private void registerUriLoadListener() {
        if (placeholderUri != null && !placeholderLoaded) {
            PicassoFactory.getInstance().registerListener(getContext(), Uri.parse(placeholderUri), this);
        } else if (uriToLoad != null) {
            PicassoFactory.getInstance().registerListener(getContext(), Uri.parse(uriToLoad), this);
        }
    }

    public interface PictureItemImageLoaderListener<T extends ImageView> {
        void onBeforeImageLoad(PicassoLoader<T> loader);

        void onImageLoaded(PicassoLoader<T> loader, boolean success);

        void onImageUnavailable(PicassoLoader<T> loader, String lastLoadError);
    }

    private void setWaitForErrorMessage(boolean waitForErrorMessage) {
        this.waitForErrorMessage = waitForErrorMessage;
    }

    private int getErrorResourceId() {
        return errorResourceId;
    }

    private static class GifLoaderTask extends AsyncTask<PicassoLoader, Void, GifDrawable> {

        private IOException error;
        private PicassoLoader loader;

        @Override
        protected GifDrawable doInBackground(PicassoLoader... picassoLoaders) {
            loader = picassoLoaders[0];
            loader.setWaitForErrorMessage(false);
            // load the gif straight into the image manually.
            CustomImageDownloader downloader = PicassoFactory.getInstance().getDownloader(loader.getLoadInto().getContext());
            downloader.setListener(loader);
            try {
                Downloader.Response rsp = downloader.load(Uri.parse(loader.getUriToLoad()), -1);
                if (rsp != null) {
                    //TODO create a custom video downloader that creates and uses a gif drawable as the basis for the stream decoder perhaps.
                    return new GifDrawable(rsp.getInputStream());
                }
            } catch (IOException e) {
                error = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(GifDrawable drawable) {
            try {
                if (drawable != null) {
                    loader.getLoadInto().setImageDrawable(drawable);
                    loader.onSuccess();
                } else {
                    Crashlytics.log(Log.ERROR, "PicassoLoader", "error downloading gif file");
                    loader.getLoadInto().setImageResource(loader.getErrorResourceId());
                    loader.onError();
                    if (error != null) {
                        Crashlytics.logException(error);
                    }
                }
            } finally {
                loader = null;
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            loader = null;
            error = null;
        }
    }
}
