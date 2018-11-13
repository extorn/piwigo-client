package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 * <p>
 */
public class PiwigoTag extends ResourceContainer<Tag, GalleryItem> {

    public PiwigoTag(Tag tag) {
        super(tag, "ResourceItem", tag.getUsageCount());
    }

    public PiwigoTag(Parcel in) {
        super(in);
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
