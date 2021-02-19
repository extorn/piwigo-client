package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.List;

import delit.libs.core.util.Logging;

/**
 * Created by gareth on 02/01/18.
 */

public class PiwigoUsers extends IdentifiablePagedList<User> {
    private static final String TAG = "PiwigoUsers";

    public PiwigoUsers() {
        super("User");
    }

    public PiwigoUsers(Parcel in) {
        super(in);
    }

    @Override
    public boolean setRetrieveItemsInReverseOrder(boolean retrieveItemsInReverseOrder) {
        throw new UnsupportedOperationException("cannot reverse the order");
    }

    @Override
    protected void sortItems(List<User> items) {
        //no-op don't sort the items.
        Logging.log(Log.DEBUG, TAG, "Unable to sort list of users");
    }

    public static final Parcelable.Creator<PiwigoUsers> CREATOR
            = new Parcelable.Creator<PiwigoUsers>() {
        public PiwigoUsers createFromParcel(Parcel in) {
            return new PiwigoUsers(in);
        }

        public PiwigoUsers[] newArray(int size) {
            return new PiwigoUsers[size];
        }
    };
}
