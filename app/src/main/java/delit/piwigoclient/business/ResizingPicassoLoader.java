package delit.piwigoclient.business;

import android.util.Log;
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

    public void setResizeTo(int imgWidth, int imgHeight) {
        this.widthPx = imgWidth;
        this.heightPx = imgHeight;
    }
}