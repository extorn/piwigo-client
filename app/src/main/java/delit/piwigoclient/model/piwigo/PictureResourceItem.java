package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.IntRange;

import java.util.Date;

import delit.libs.core.util.Logging;

/**
 * Created by gareth on 12/07/17.
 */
public class PictureResourceItem extends ResourceItem implements Parcelable {
    private static final String TAG = "PicResItem";

    public PictureResourceItem(long id, String name, String description, Date dateCreated, Date lastAltered, String baseResourceUrl) {
        super(id, name, description, dateCreated, lastAltered, baseResourceUrl);
    }

    public PictureResourceItem(Parcel in) {
        super(in);
    }

    public ResourceFile getBestFitFile(@IntRange(from = 1) int availableWidth, @IntRange(from = 1) int availableHeight) {
        ResourceFile bestFile = null;
        if(getAvailableFiles().size() == 1) {
            return getAvailableFiles().get(0);
        }
        double bestScale = 0;
        for(ResourceFile file : getAvailableFiles()) {
            if(file.getName().equals("square")) { // This is an internal server enum type
                continue;
            }
            double scaleW = ((double)availableWidth) / file.getWidth();
            double scaleH = ((double)availableHeight) / file.getHeight();
            boolean fitToWidth = scaleW < scaleH;
            double thisScale = fitToWidth ? scaleW : scaleH;
            if(bestFile == null) {
                bestFile = file;
                bestScale = fitToWidth ? scaleW : scaleH;
                continue;
            }
            if(bestScale < 10 && thisScale < 1) {
                // this image is bigger than the screen and our current best image scales less than x times to fit screen.
                continue;
            }
            if(thisScale < bestScale) {
                bestFile = file;
                bestScale = thisScale;
            }
        }
        return bestFile;
    }


    public int getType() {
        return PICTURE_RESOURCE_TYPE;
    }

    public void copyFrom(PictureResourceItem other, boolean copyParentage) {
        super.copyFrom(other, copyParentage);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
    }

    public static final Parcelable.Creator<PictureResourceItem> CREATOR
            = new Parcelable.Creator<PictureResourceItem>() {
        public PictureResourceItem createFromParcel(Parcel in) {
            try {
                return new PictureResourceItem(in);
            } catch(RuntimeException e) {
                Logging.log(Log.ERROR, TAG, "Unable to create pic resource item from parcel: " + in.toString());
                throw e;
            }
        }

        public PictureResourceItem[] newArray(int size) {
            return new PictureResourceItem[size];
        }
    };
}
