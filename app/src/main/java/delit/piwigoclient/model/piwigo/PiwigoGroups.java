package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.List;

import delit.libs.core.util.Logging;

/**
 *
 * Created by gareth on 02/01/18.
 */

public class PiwigoGroups extends IdentifiablePagedList<Group> {
    private static final String TAG = "PiwigoGroups";

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
        //no-op don't sort the items.
        Logging.log(Log.DEBUG, TAG, "Unable to sort list of Groups");
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
