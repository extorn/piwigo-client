package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import delit.libs.core.util.Logging;

/**
 * Created by gareth on 06/04/18.
 */

public abstract class ResourceContainer<S extends Identifiable&Parcelable, T extends Identifiable&Parcelable> extends IdentifiablePagedList<T> implements Identifiable, Parcelable {

    private static final String TAG = "ResourceContainer";
    private S containerDetails;

    public ResourceContainer(S containerDetails, String itemType) {
        this(containerDetails, itemType, 10);
    }

    public ResourceContainer(S containerDetails, String itemType, int maxExpectedItemCount) {
        super(itemType, maxExpectedItemCount);
        this.containerDetails = containerDetails;
    }

    public ResourceContainer(Parcel in) {
        super(in);
        containerDetails = in.readParcelable(getClass().getClassLoader());
        if (containerDetails == null) {
            Logging.log(Log.WARN, TAG, "Resource container details was loaded as null for item of type " + getItemType());
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(containerDetails, flags);
        if (containerDetails == null) {
            Logging.log(Log.WARN, TAG, "Resource container details was saved as null for item of type " + getItemType());
        }
    }

    public int getResourcesCount() {
        return getItemCount();
    }

    public abstract int getImgResourceCount();

    public S getContainerDetails() {
        return containerDetails;
    }

    public void setContainerDetails(S item) {
        containerDetails = item;
    }

    @Override
    public long getId() {
        return containerDetails.getId();
    }

    @Override
    protected List<T> prePageInsert(List<T> newItems) {
        //TODO remove this once the server allows reversing the sort order as a webservice option!
        if(isRetrieveItemsInReverseOrder()) {
            ArrayList<T> sortedItems = new ArrayList<>(newItems);
            Collections.reverse(sortedItems);
            return sortedItems;
        }
        return newItems;
    }

    public int getFirstResourceIdx() {
        if(getItemCount() > 0) {
            return 0;
        } else {
            return -1;
        }
    }
}
