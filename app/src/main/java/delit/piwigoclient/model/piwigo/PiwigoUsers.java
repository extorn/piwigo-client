package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

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
