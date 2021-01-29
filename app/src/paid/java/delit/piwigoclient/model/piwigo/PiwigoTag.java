package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.List;

import delit.libs.core.util.Logging;

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 * <p>
 */
public class PiwigoTag extends ResourceContainer<Tag, GalleryItem> {

    private static final String TAG = "PwgTag";

    public PiwigoTag(Tag tag) {
        super(tag, "ResourceItem", tag.getUsageCount());
    }

    public PiwigoTag(Parcel in) {
        super(in);
    }

    @Override
    public boolean setRetrieveItemsInReverseOrder(boolean retrieveItemsInReverseOrder) {
        Logging.log(Log.WARN, TAG, "Unable to reverse the order of items attached to a tag");
        return false;

        //throw new UnsupportedOperationException("cannot reverse the order");
    }

    @Override
    protected void sortItems(List<GalleryItem> items) {
        throw new UnsupportedOperationException("cannot sort the items");
    }

    @Override
    public int getImgResourceCount() {
        return getContainerDetails().getUsageCount();
    }

    @Override
    public int describeContents() {
        return super.describeContents();
    }

    public static final Parcelable.Creator<PiwigoTag> CREATOR
            = new Parcelable.Creator<PiwigoTag>() {
        public PiwigoTag createFromParcel(Parcel in) {
            return new PiwigoTag(in);
        }

        public PiwigoTag[] newArray(int size) {
            return new PiwigoTag[size];
        }
    };
}
