package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

/**
 * Created by gareth on 02/01/18.
 */

public class PiwigoUsers extends IdentifiablePagedList<User> {
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
        throw new UnsupportedOperationException("cannot sort the items");
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
