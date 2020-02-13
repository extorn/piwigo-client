
package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by gareth on 02/01/18.
 */

public class PiwigoUsernames extends IdentifiablePagedList<Username> {
    public PiwigoUsernames() {
        super("Username");
    }

    public PiwigoUsernames(Parcel in) {
        super(in);
    }

    @Override
    public boolean setRetrieveItemsInReverseOrder(boolean retrieveItemsInReverseOrder) {
        throw new UnsupportedOperationException("cannot reverse the order");
    }

    @Override
    protected void sortItems() {
        throw new UnsupportedOperationException("cannot sort the items");
    }

    public static final Parcelable.Creator<PiwigoUsernames> CREATOR
            = new Parcelable.Creator<PiwigoUsernames>() {
        public PiwigoUsernames createFromParcel(Parcel in) {
            return new PiwigoUsernames(in);
        }

        public PiwigoUsernames[] newArray(int size) {
            return new PiwigoUsernames[size];
        }
    };
}