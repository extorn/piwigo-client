package delit.piwigoclient.business;

import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.squareup.picasso.RequestCreator;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;

public class ResizingPicassoLoader<T extends ImageView> extends PicassoLoader<T> {

    private static final String TAG = "ResizePicLoad";
    private boolean centerCrop = true;
    private int widthPx;
    private int heightPx;

    public ResizingPicassoLoader(T loadInto, int widthPx, int heightPx) {
        super(loadInto);
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    public ResizingPicassoLoader(T loadInto, PicassoLoader.PictureItemImageLoaderListener listener, int widthPx, int heightPx) {
        super(loadInto, listener);
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    public void setCenterCrop(boolean centerCrop) {
        this.centerCrop = centerCrop;
    }


    @Override
    protected RequestCreator customiseLoader(RequestCreator placeholder) {
        try {
            RequestCreator reqCreator = placeholder.resize(widthPx, heightPx);
            if (centerCrop) {
                reqCreator = reqCreator.centerCrop();
            } else {
                reqCreator = reqCreator.centerInside();
            }
            return reqCreator;
        } catch (IllegalArgumentException e) {
            String pathToView = DisplayUtils.getPathToView(getLoadInto());
            Logging.log(Log.ERROR, TAG, "ERROR: " + e.getMessage() + "\nViewId: " + getLoadInto().getId() + "\nURI: " + getUriToLoad() + "\nPathToView : " + pathToView);
            throw e;
        }
    }

    public boolean setResizeTo(int imgWidth, int imgHeight) {
        boolean loadNeeded = false;
        if(imgWidth != widthPx || imgHeight != heightPx) {
            loadNeeded = true;
        }
        this.widthPx = imgWidth;
        this.heightPx = imgHeight;
        return loadNeeded;
    }

    @Override
    protected void load(boolean forceServerRequest) {
        if(widthPx > 0 && heightPx > 0) {
            super.load(forceServerRequest);
        } else if(getLoadInto().getWidth() > 0 && getLoadInto().getHeight() > 0) {
            setResizeTo(getLoadInto().getWidth(), getLoadInto().getHeight());
            super.load(forceServerRequest);
        } else {
            getLoadInto().addOnLayoutChangeListener(new LoadIntoLayoutListener(this));
        }
    }

    private static class LoadIntoLayoutListener<T extends ImageView> implements View.OnLayoutChangeListener {
        private final ResizingPicassoLoader<T> imageLoader;

        public LoadIntoLayoutListener(ResizingPicassoLoader<T> imageLoader) {
            this.imageLoader = imageLoader;
        }
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            // now have width and height
            if(imageLoader.setResizeTo(v.getWidth(), v.getHeight()) || !imageLoader.isImageLoaded()) {
                if(!imageLoader.hasResourceToLoad()) {
                    return;
                }
                imageLoader.load();
            }
        }
    }
}