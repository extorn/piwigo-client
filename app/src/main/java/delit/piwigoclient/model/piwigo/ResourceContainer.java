package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by gareth on 06/04/18.
 */

public abstract class ResourceContainer<S extends Identifiable&Parcelable, T extends Identifiable&Parcelable> extends IdentifiablePagedList<T> implements Identifiable, Parcelable {

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
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(containerDetails, flags);
    }

    public ResourceItem getResourceItemById(long itemId) {
        T item = getItemById(itemId);
        if (item instanceof ResourceItem) {
            return (ResourceItem) item;
        }
        throw new RuntimeException("Item is present, but is not an album resource, is a " + item.getClass().getName());
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
}
