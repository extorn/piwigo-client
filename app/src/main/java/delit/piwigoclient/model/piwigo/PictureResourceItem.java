package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntRange;

import java.util.Date;

/**
 * Created by gareth on 12/07/17.
 */
public class PictureResourceItem extends ResourceItem {

    public PictureResourceItem(long id, String name, String description, Date dateCreated, Date lastAltered, String thumbnailUrl) {
        super(id, name, description, dateCreated, lastAltered, thumbnailUrl);
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

    public static final Parcelable.Creator<PictureResourceItem> CREATOR
            = new Parcelable.Creator<PictureResourceItem>() {
        public PictureResourceItem createFromParcel(Parcel in) {
            return new PictureResourceItem(in);
        }

        public PictureResourceItem[] newArray(int size) {
            return new PictureResourceItem[size];
        }
    };
}
