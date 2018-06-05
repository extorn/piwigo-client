package delit.piwigoclient.business;

import android.content.Context;
import android.support.annotation.DrawableRes;
import android.support.annotation.IdRes;
import android.widget.ImageView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.File;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.PicassoFactory;

/**
 * Created by gareth on 11/10/17.
 */
public class PicassoLoader implements Callback {

    private String uriToLoad;
    private final ImageView loadInto;
    private File fileToLoad;
    private final static int DEFAULT_AUTO_RETRIES = 1;
    public final static int INFINITE_AUTO_RETRIES = -1;
    private int maxRetries = DEFAULT_AUTO_RETRIES;
    private int retries = 0;
    private boolean imageLoaded;
    private boolean imageLoading;
    private float rotation = 0;
    private int resourceToLoad = Integer.MIN_VALUE;
    private boolean imageUnavailable;
    public static final String PICASSO_REQUEST_TAG = "PIWIGO";
    private String placeholderUri;
    private boolean placeholderLoaded;
    private @DrawableRes int errorResourceId = R.drawable.ic_error_black_240px;

    public PicassoLoader(ImageView loadInto) {
        this.loadInto = loadInto;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public final void onSuccess() {
        imageLoading = false;

        if(placeholderUri != null && !placeholderLoaded) {
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
        if(imageLoading) {
            return;
        }
        imageLoading = true;
        //TODO this hack isn't needed if the resource isnt a vector drawable... later version of com.squareup.picasso?
        if(resourceToLoad != Integer.MIN_VALUE) {
            getLoadInto().setImageResource(getResourceToLoad());
            onSuccess();
        } else {
            PicassoFactory.getInstance().getPicassoSingleton(getContext()).cancelRequest(loadInto);
            customiseLoader(buildLoader()).into(loadInto, this);
        }
    }

    public void cancelImageLoadIfRunning() {
        if(loadInto != null) {
            PicassoFactory.getInstance().getPicassoSingleton(getContext()).cancelRequest(loadInto);
        }
    }

    private Context getContext() {
        Context context = loadInto.getContext();
        if(context == null) {
            throw new IllegalStateException("Context is not available in the view at this time");
        }
        return context;
    }

    protected RequestCreator buildLoader() {
        RequestCreator rc = buildRequestCreator(PicassoFactory.getInstance().getPicassoSingleton(getContext())).error(errorResourceId);
        if(placeholderLoaded) {
            rc.noPlaceholder();
        } else {
            rc.placeholder(R.drawable.blank);
        }

        if(Math.abs(rotation) > Float.MIN_NORMAL) {
            rc.rotate(rotation);
        }
        rc.tag(PICASSO_REQUEST_TAG);
        return rc;
    }

    private RequestCreator buildRequestCreator(Picasso picassoSingleton) {
        if(placeholderUri != null) {
            return picassoSingleton.load(placeholderUri);
        }
        if (uriToLoad != null) {
            return picassoSingleton.load(uriToLoad);
        } else if (fileToLoad != null) {
            return picassoSingleton.load(fileToLoad);
        } else if(resourceToLoad != Integer.MIN_VALUE) {
            return picassoSingleton.load(resourceToLoad);
        }
        throw new IllegalStateException("No valid source specified from which to load image");
    }

    protected RequestCreator customiseLoader(RequestCreator placeholder) {
        return placeholder;
    }

    public void setFileToLoad(File fileToLoad) {
        this.uriToLoad = null;
        this.resourceToLoad = Integer.MIN_VALUE;
        this.fileToLoad = fileToLoad;
        resetLoadState();
    }

    public void setUriToLoad(String uriToLoad) {
        this.uriToLoad = uriToLoad;
        this.resourceToLoad = Integer.MIN_VALUE;
        this.fileToLoad = null;
        resetLoadState();
    }

    public void setResourceToLoad(int resourceToLoad) {
        this.uriToLoad = null;
        this.resourceToLoad = resourceToLoad;
        this.fileToLoad = null;
        resetLoadState();
    }

    protected void resetImageToLoad() {
        fileToLoad = null;
        resourceToLoad = Integer.MIN_VALUE;
        uriToLoad = null;
    }

    public void resetAll() {
        resetImageToLoad();
        imageLoaded = false;
        rotation = 0;
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
    }

    protected int getResourceToLoad() {
        return resourceToLoad;
    }

    protected File getFileToLoad() {
        return fileToLoad;
    }

    protected String getUriToLoad() {
        return uriToLoad;
    }

    protected ImageView getLoadInto() {
        return loadInto;
    }

    public void setPlaceholderImageUri(String placeholderUri) {
        this.placeholderUri = placeholderUri;
    }
}
