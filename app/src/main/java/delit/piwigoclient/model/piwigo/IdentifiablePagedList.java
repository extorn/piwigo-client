package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by gareth on 02/01/18.
 */

public class IdentifiablePagedList<T extends Identifiable&Parcelable> extends PagedList<T> {

    public IdentifiablePagedList(String itemType) {
        super(itemType);
    }

    public IdentifiablePagedList(String itemType, int maxExpectedItemCount) {
        super(itemType, maxExpectedItemCount);
    }

    public IdentifiablePagedList(Parcel in) {
        super(in);
    }

    @Override
    public Long getItemId(T item) {
        return item.getId();
    }

    public static final Parcelable.Creator<IdentifiablePagedList> CREATOR
            = new Parcelable.Creator<IdentifiablePagedList>() {
        public IdentifiablePagedList createFromParcel(Parcel in) {
            return new IdentifiablePagedList(in);
        }

        public IdentifiablePagedList[] newArray(int size) {
            return new IdentifiablePagedList[size];
        }
    };
}
