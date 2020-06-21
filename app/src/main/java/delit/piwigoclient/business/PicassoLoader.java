package delit.piwigoclient.business;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.util.SparseIntArray;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.res.ResourcesCompat;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;
import com.squareup.picasso.Transformation;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;

import cz.msebera.android.httpclient.HttpStatus;
import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.ObjectUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.PicassoFactory;
import pl.droidsonroids.gif.GifDrawable;

/**
 * Created by gareth on 11/10/17.
 */
public class PicassoLoader<T extends ImageView> implements Callback, PicassoFactory.EnhancedPicassoListener {

    public final static int INFINITE_AUTO_RETRIES = -1;
    public static final String PICASSO_REQUEST_TAG = "PIWIGO";
    private final static int DEFAULT_AUTO_RETRIES = 1;
    private static final String TAG = "PicassoLoader";
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
    private boolean usePlaceholderIfNothingToLoad;
    private final SparseIntArray errorDrawables = new SparseIntArray();
    private CustomResponseException lastResponseException;
    private PostProcessorTarget postProcessor;


    public PicassoLoader(T loadInto) {
        this(loadInto, null);
    }

    public PicassoLoader(T loadInto, PictureItemImageLoaderListener listener) {
        this.loadInto = loadInto;
        this.listener = listener;
        addErrorDrawable(HttpStatus.SC_UNAUTHORIZED, R.drawable.ic_image_locked_black_240dp);
        addErrorDrawable(HttpStatus.SC_NOT_FOUND, R.drawable.ic_broken_image_black_240dp);
    }

    public void setUsePlaceholderIfNothingToLoad(boolean usePlaceholderIfNothingToLoad) {
        this.usePlaceholderIfNothingToLoad = usePlaceholderIfNothingToLoad;
    }

    public final PicassoLoader<T> addErrorDrawable(int statusCode, @DrawableRes int drawable) {
        errorDrawables.put(statusCode, drawable);
        return this;
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
            Logging.log(Log.ERROR, "PicassoLoader", "Unexpected error loading image");
            Logging.recordException(exception);
        } else {
            lastResponseException = (CustomResponseException) exception;
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
        load(false);
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    public boolean isImageLoading() {
        return imageLoading;
    }

    public void loadNoCache() {
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
        if (!placeholderLoaded || !usePlaceholderIfError) {
            int errDrawableId = errorResourceId;
            if (lastResponseException != null) {
                errDrawableId = errorDrawables.get(lastResponseException.getResponseCode());
                if (errDrawableId == 0) {
                    errDrawableId = errorResourceId;
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // attempt to work around suspected issue with loading error drawables on android 4.4.2
                loadInto.setImageDrawable(ResourcesCompat.getDrawable(getContext().getResources(), errDrawableId, getContext().getTheme()));
            } else {
                RequestCreator loader = PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(errDrawableId);
                loader.into(loadInto, null);
            }
        }
    }

    protected void load(final boolean forceServerRequest) {
        if (listener != null) {
            listener.onBeforeImageLoad(this);
        }
        synchronized (this) {
            if (imageLoading) {
                return;
            }
            imageLoading = true;
            //TODO this hack isn't needed if the resource isn't a vector drawable... later version of com.squareup.picasso?
            if (resourceToLoad != Integer.MIN_VALUE) {
                getLoadInto().setImageResource(getResourceToLoad());
                onSuccess();
            } else {
                PicassoFactory.getInstance().getPicassoSingleton(getContext()).cancelRequest(loadInto);
                if(placeholderUri != null && placeholderLoaded && isLoadingGif()) {
                    GifLoaderTask task = new GifLoaderTask(this);
                    if (DisplayUtils.isRunningOnUIThread()) {
                        task.execute();
                    } else {
                        task.doInBackgroundSafely();
                    }
                } else {
                    DisplayUtils.postOnUiThread(() -> runLoad(forceServerRequest)); // this is sensible because if called in a predraw method for example, we don't want to take too long at this point.
                }
            }
        }
    }

    private void runLoad(boolean forceServerRequest) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "entering picasso loader - runLoad");
            }
            waitForErrorMessage = true;
            RequestCreator loader = customiseLoader(buildLoader());
            if (forceServerRequest) {
                loader.memoryPolicy(MemoryPolicy.NO_CACHE);
//                loader.memoryPolicy(MemoryPolicy.NO_STORE);
                loader.networkPolicy(NetworkPolicy.NO_CACHE);
//                loader.networkPolicy(NetworkPolicy.NO_STORE);
            }
            //                if(placeholderUri != null) {
            //                    Log.d("PicassoLoader", "Loading: " + placeholderUri, new Exception().fillInStackTrace());
            //                } else {
            //                    Log.d("PicassoLoader", "Loading: " + uriToLoad, new Exception().fillInStackTrace());
            //                }
            registerUriLoadListener();
            if(postProcessor != null) {
                loader.into(postProcessor);
            } else {
                loader.into(loadInto, this);
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "exiting picasso loader - runLoad");
            }
        } catch (RejectedExecutionException e) {
            Logging.log(Log.WARN, TAG, "All picasso loaders are currently busy, or picasso is not started. Please retry later");
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
            if(postProcessor != null) {
                PicassoFactory.getInstance().getPicassoSingleton(getContext()).cancelRequest(postProcessor);
            } else if (loadInto != null) {
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
        if (transformation != null) {
            rc.transform(transformation);
        }
        if (placeholderLoaded) {
            rc.noPlaceholder();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                rc.placeholder(placeholderPlaceholderId);
            } else {
                rc.placeholder(AppCompatResources.getDrawable(loadInto.getContext(), placeholderPlaceholderId));
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

        if (usePlaceholderIfNothingToLoad) {
            return picassoSingleton.load(placeholderPlaceholderId);
        }

        Logging.log(Log.ERROR, "PicassoLoader", "No valid source specified from which to load image");
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

    public T getLoadInto() {
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

    public boolean isRotateToFitScreen() {
        return postProcessor instanceof MatchScreenAspectRatioPostProcessorTarget;
    }

    public void rotateToFitScreen(boolean fitScreen) {
        if(fitScreen) {
            postProcessor = new MatchScreenAspectRatioPostProcessorTarget(this);
            if(loadInto != null) {
                postProcessor.setPostProcessTarget(loadInto);
            }
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

    private static class GifLoaderTask extends OwnedSafeAsyncTask<PicassoLoader, Void, Void, GifDrawable> {

        private IOException error;

        public GifLoaderTask(PicassoLoader owner) {
            super(owner);
        }


        @Override
        protected GifDrawable doInBackgroundSafely(Void... nothing) {
            getOwner().setWaitForErrorMessage(false);
            // load the gif straight into the image manually.
            CustomImageDownloader downloader = PicassoFactory.getInstance().getDownloader();
            try {
                Downloader.Response rsp = downloader.load(Uri.parse(getOwner().getUriToLoad()), 0);
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
        protected void onPostExecuteSafely(GifDrawable drawable) {
            PicassoLoader loader;
            try {
                loader = getOwner(); // NPE if null
            } catch(NullPointerException e) {
                // no longer on screen. ignore error.
                return;
            }
            if (drawable != null) {
                loader.getLoadInto().setImageDrawable(drawable);
                loader.onSuccess();

            } else {
                Logging.log(Log.ERROR, "PicassoLoader", "error downloading gif file");
                loader.getLoadInto().setImageResource(loader.getErrorResourceId());
                loader.onError();
                if (error != null) {
                    Logging.recordException(error);
                }
            }
        }

        @Override
        protected void onCancelledSafely() {
            error = null;
        }
    }

    private static class MatchScreenAspectRatioPostProcessorTarget extends PostProcessorTarget {
        public MatchScreenAspectRatioPostProcessorTarget(Callback callback) {
            super(callback);
        }

        @Override
        protected Bitmap postProcess(Bitmap bitmap) {

            int appAspect = DisplayUtils.getAspect(getImageView().getRootView());
            int imageAspect = DisplayUtils.getAspect(bitmap);
            if(appAspect != imageAspect) {
                /*Matrix matrix = new Matrix();
                matrix.postRotate(90);
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth(), bitmap.getHeight(), true);
                Bitmap rotatedBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
                //NOTE: we can't recycle the original bitmap as it's cached in picasso.*/

//                return super.postProcess(rotatedBitmap);
            }
            return super.postProcess(bitmap);

        }
    }

    private static class PostProcessorTarget implements Target {
        private ImageView imageView;
        private Callback callback;

        protected ImageView getImageView() {
            return imageView;
        }

        public PostProcessorTarget(Callback callback) {
            this.callback = callback;
        }

        public PostProcessorTarget(ImageView imageView) {
            this.imageView = imageView;
        }

        public void setPostProcessTarget(ImageView imageView) {
            this.imageView = imageView;
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            /*imageView.setImageBitmap(postProcess(bitmap));
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            imageView.getImageMatrix().set(matrix);*/
            if(callback != null) {
                callback.onSuccess();
            }
        }

        protected Bitmap postProcess(Bitmap bitmap) {
            return bitmap;
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
            imageView.setImageDrawable(errorDrawable);
            if(callback != null) {
                callback.onError();
            }
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
            imageView.setImageDrawable(placeHolderDrawable);
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if(obj instanceof PostProcessorTarget) {
                PostProcessorTarget other = (PostProcessorTarget) obj;
                return ObjectUtils.areEqual(imageView, other.imageView);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return super.hashCode() + (imageView == null ? 0 : (3 * imageView.hashCode()));
        }
    }
}
