package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

/**
 *
 * Created by gareth on 02/01/18.
 */

public class PiwigoGroups extends IdentifiablePagedList<Group> {
    public PiwigoGroups() {
        super("Group");
    }

    public PiwigoGroups(Parcel in) {
        super(in);
    }

    @Override
    public boolean setRetrieveItemsInReverseOrder(boolean retrieveItemsInReverseOrder) {
        throw new UnsupportedOperationException("cannot reverse the order");
    }

    @Override
    protected void sortItems(List<Group> items) {
        throw new UnsupportedOperationException("cannot sort the items");
    }

    public static final Parcelable.Creator<PiwigoGroups> CREATOR
            = new Parcelable.Creator<PiwigoGroups>() {
        public PiwigoGroups createFromParcel(Parcel in) {
            return new PiwigoGroups(in);
        }

        public PiwigoGroups[] newArray(int size) {
            return new PiwigoGroups[size];
        }
    };
}
