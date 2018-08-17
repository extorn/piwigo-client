package delit.piwigoclient.business;

import android.content.Context;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.util.Log;
import android.widget.ImageView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.File;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.util.picasso.RoundedTransformation;

/**
 * Created by gareth on 11/10/17.
 */
public class PicassoLoader implements Callback {

    public final static int INFINITE_AUTO_RETRIES = -1;
    public static final String PICASSO_REQUEST_TAG = "PIWIGO";
    private final static int DEFAULT_AUTO_RETRIES = 1;
    private final ImageView loadInto;
    private String uriToLoad;
    private File fileToLoad;
    private int maxRetries = DEFAULT_AUTO_RETRIES;
    private int retries = 0;
    private boolean imageLoaded;
    private boolean imageLoading;
    private float rotation = 0;
    private int resourceToLoad = Integer.MIN_VALUE;
    private boolean imageUnavailable;
    private String placeholderUri;
    private boolean placeholderLoaded;
    private @DrawableRes
    int errorResourceId = R.drawable.ic_error_black_240px;
    private RoundedTransformation transformation;

    public PicassoLoader(ImageView loadInto) {
        this.loadInto = loadInto;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public final void onSuccess() {
        imageLoading = false;

        if (placeholderUri != null && !placeholderLoaded) {
            placeholderUri = null;
            placeholderLoaded = true;
            load();
            return;
        }

        imageLoaded = true;
        onImageLoad(true);
    }

    public void withErrorDrawable(@DrawableRes int errorDrawable) {
        this.errorResourceId = errorDrawable;
    }

    public boolean isImageLoaded() {
        return imageLoaded;
    }

    @Override
    public final void onError() {
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

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    protected void onImageLoad(boolean success) {
    }

    protected void onImageUnavailable() {
    }

    public boolean isImageLoading() {
        return imageLoading;
    }

    public void load() {
        load(false);
    }

    public void load(boolean forceServerRequest) {
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
                loader.into(loadInto, this);
            }
        }
    }

    public void cancelImageLoadIfRunning() {
        synchronized (this) {
            if(!imageLoading) {
                return;
            }
            if (loadInto != null) {
                PicassoFactory.getInstance().getPicassoSingleton(getContext()).cancelRequest(loadInto);
            }
            imageLoading = false;
        }
    }

    private Context getContext() {
        Context context = loadInto.getContext();
        if (context == null) {
            throw new IllegalStateException("Context is not available in the view at this time");
        }
        return context;
    }

    protected RequestCreator buildLoader() {
        RequestCreator rc = buildRequestCreator(PicassoFactory.getInstance().getPicassoSingleton(getContext())).error(errorResourceId);
        if (transformation != null) {
            rc.transform(transformation);
        }
        if (placeholderLoaded) {
            rc.noPlaceholder();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                rc.placeholder(R.drawable.ic_file_gray_24dp);
            } else {
                loadInto.setImageResource(R.drawable.ic_file_gray_24dp);
            }
        }

        if (Math.abs(rotation) > Float.MIN_NORMAL) {
            rc.rotate(rotation);
        }
        rc.tag(PICASSO_REQUEST_TAG);
        return rc;
    }

    private RequestCreator buildRequestCreator(Picasso picassoSingleton) {
        if (placeholderUri != null) {
            return picassoSingleton.load(placeholderUri);
        }
        if (uriToLoad != null) {
            return picassoSingleton.load(uriToLoad);
        } else if (fileToLoad != null) {
            return picassoSingleton.load(fileToLoad);
        } else if (resourceToLoad != Integer.MIN_VALUE) {
            return picassoSingleton.load(resourceToLoad);
        }
        throw new IllegalStateException("No valid source specified from which to load image");
    }

    protected RequestCreator customiseLoader(RequestCreator placeholder) {
        return placeholder;
    }

    protected void resetImageToLoad() {
        fileToLoad = null;
        resourceToLoad = Integer.MIN_VALUE;
        uriToLoad = null;
    }

    public void resetAll() {
        resetImageToLoad();
        maxRetries = DEFAULT_AUTO_RETRIES;
        resetLoadState();
    }

    public boolean isImageUnavailable() {
        return imageUnavailable;
    }

    public void resetLoadState() {
        retries = 0;
        imageLoaded = false;
        imageUnavailable = false;
        cancelImageLoadIfRunning();
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

    public void setUriToLoad(String uriToLoad) {
        this.uriToLoad = uriToLoad;
        this.resourceToLoad = Integer.MIN_VALUE;
        this.fileToLoad = null;
        resetLoadState();
    }

    public boolean hasResourceToLoad() {
        return uriToLoad != null || fileToLoad != null || resourceToLoad > Integer.MIN_VALUE;
    }

    protected ImageView getLoadInto() {
        return loadInto;
    }

    public void setPlaceholderImageUri(String placeholderUri) {
        this.placeholderUri = placeholderUri;
    }

    public void withTransformation(RoundedTransformation transformation) {
        this.transformation = transformation;
    }

    public void loadNoCache() {

        load(true);
    }
}
