package delit.piwigoclient.business;

import android.widget.ImageView;

import com.squareup.picasso.RequestCreator;

public class ResizingPicassoLoader<T extends ImageView> extends PicassoLoader<T> {

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
        RequestCreator reqCreator = placeholder.resize(widthPx, heightPx);
        if (centerCrop) {
            reqCreator = reqCreator.centerCrop();
        } else {
            reqCreator = reqCreator.centerInside();
        }
        return reqCreator;
    }

    public void setResizeTo(int imgWidth, int imgHeight) {
        this.widthPx = imgWidth;
        this.heightPx = imgHeight;
    }
}