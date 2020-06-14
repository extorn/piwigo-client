package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

/**
 * Created by gareth on 12/07/17.
 */
public class ResourceItem extends AbstractBaseResourceItem {

    private static final long serialVersionUID = -1726930101244306279L;

    public ResourceItem(long id, String name, String description, Date created, Date lastAltered, String thumbnailUrl) {
        super(id, name, description, created, lastAltered, thumbnailUrl);
    }

    public ResourceItem(Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<ResourceItem> CREATOR
            = new Parcelable.Creator<ResourceItem>() {
        public ResourceItem createFromParcel(Parcel in) {
            return new ResourceItem(in);
        }

        public ResourceItem[] newArray(int size) {
            return new ResourceItem[size];
        }
    };
}
