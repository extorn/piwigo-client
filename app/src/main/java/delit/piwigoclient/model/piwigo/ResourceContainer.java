package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import delit.libs.core.util.Logging;
import delit.libs.util.Utils;
import delit.piwigoclient.BuildConfig;

/**
 * Created by gareth on 06/04/18.
 */

public abstract class ResourceContainer<S extends Identifiable&Parcelable, T extends GalleryItem> extends IdentifiablePagedList<T> implements Identifiable, Parcelable {

    private static final String TAG = "ResourceContainer";
    public static final int CHANGE_SORT_BY = 2;
    public static final int CHANGE_RESOURCES_CLEARED = 3;
    private S containerDetails;
    AlbumSortingResourceComparator resourceComparator;

    public ResourceContainer(S containerDetails, String itemType) {
        this(containerDetails, itemType, 10);
    }

    public ResourceContainer(S containerDetails, String itemType, int maxExpectedItemCount) {
        super(itemType, maxExpectedItemCount);
        this.containerDetails = containerDetails;
        resourceComparator = new AlbumSortingResourceComparator();
    }

    public ResourceContainer(Parcel in) {
        super(in);
        resourceComparator = new AlbumSortingResourceComparator();
        resourceComparator.setSortOrder(in.readString());
        containerDetails = in.readParcelable(getClass().getClassLoader());
        if (containerDetails == null) {
            Logging.log(Log.WARN, TAG, "Resource container details was loaded as null for item of type " + getItemType());
        }
    }

    public AlbumSortingResourceComparator getResourceComparator() {
        return resourceComparator;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(resourceComparator.getSortOrder());
        dest.writeParcelable(containerDetails, flags);
        if (containerDetails == null) {
            Logging.log(Log.WARN, TAG, "Resource container details was saved as null for item of type " + getItemType());
        }
    }

    @Override
    protected boolean calculateIsFullyLoadedCheck(int pageIdx, int pageSize, List<T> itemsToAdd) {
        if(getImgResourceCount() == getResourcesCount()) {
            return true;
        }
        return super.calculateIsFullyLoadedCheck(pageIdx, pageSize, itemsToAdd);
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
    protected void sortItems(List<T> items) {
        boolean flipResources = resourceComparator.setFlipResourceOrder(false);
        Collections.sort(items, resourceComparator);
        if(flipResources) {
            reverseTheResourceOrder();
        }
    }

    protected void reverseTheResourceOrder() {
        int fromIdx = getFirstResourceIdx();
        int toIdxExclusive = fromIdx + getResourcesCount();
        Collections.reverse(getItems().subList(fromIdx, toIdxExclusive));
    }

    @Override
    protected List<T> prePageInsert(List<T> newItems) {
        //TODO remove this once the server allows reversing the sort order as a webservice option!
        if(super.isRetrieveItemsInReverseOrder()) {
            ArrayList<T> sortedItems = new ArrayList<>(newItems);
            Collections.reverse(sortedItems);
            return sortedItems;
        }
        return newItems;
    }

    /**
     * This is not supported.
     * use {@link #setRetrieveResourcesInReverseOrder(boolean)}
     *
     * @param retrieveInReverseOrder ignored - will not alter the result
     * @return false
     * @throws UnsupportedOperationException if {@link BuildConfig#DEBUG}
     */
    @Override
    public final boolean setRetrieveItemsInReverseOrder(boolean retrieveInReverseOrder) {
        if(BuildConfig.DEBUG) {
            throw new UnsupportedOperationException("use retrieve resources or albums in reverse order. This makes no sense");
        }
        Logging.log(Log.WARN,TAG, "Invocation of unsupported method %1$s.setRetrieveItemsInReverseOrder", Utils.getId(this));
        return false;
    }

    /**
     * This will do this for the resource items.
     */
    public boolean setRetrieveResourcesInReverseOrder(boolean retrieveItemsInReverseOrder) {

        boolean currentSortOrder = super.isRetrieveItemsInReverseOrder();
        if (currentSortOrder != retrieveItemsInReverseOrder) {
            if(getResourcesCount() > 0) {
                if(isFullyLoaded()) {
                    resourceComparator.setFlipResourceOrder(true);
                } else {
                    throw new IllegalStateException("Reversing the resource order is not possible when only some resources are loaded");
                }
            }
        }
        return super.setRetrieveItemsInReverseOrder(retrieveItemsInReverseOrder);
    }

    public boolean isRetrieveResourcesInReverseOrder() {
        return super.isRetrieveItemsInReverseOrder();
    }

    /**
     * This doesn't make sense to be called on a container where the overall container should never be reversed.
     * Here it will return the value of {@link #isRetrieveResourcesInReverseOrder()}
     * @return the value of isRetrieveResourcesInReverseOrder
     */
    @Override
    public final boolean isRetrieveItemsInReverseOrder() {
        return isRetrieveResourcesInReverseOrder();
    }

    public int getFirstResourceIdx() {
        if(getItemCount() > 0) {
            return 0;
        } else {
            return -1;
        }
    }

    public void removeAllResources() {
        int idx = getFirstResourceIdx();
        while(idx > 0) {
            remove(idx);
            idx = getFirstResourceIdx();
        }
        if(getPagesLoadedIdxToSizeMap() > 0 && !hasOnlyEmptyFirstPage()) {
            Logging.log(Log.ERROR, TAG, toString());
            throw new IllegalStateException("If there are no resources, there should be no pages loaded");
        }
        notifyListenersOfChange(CHANGE_RESOURCES_CLEARED);
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ResourceContainer{");
        sb.append("containerDetails=").append(containerDetails);
        sb.append(", resourceSortOrder='").append(resourceComparator.getSortOrder()).append('\'');
        sb.append(", super=").append(super.toString());
        sb.append('}');
        return sb.toString();
    }

    /**
     * If the resource order changes, the album must be cleared of resources
     * otherwise the resource paging calculations will all be incorrect.
     *
     * @param resourceSortOrder the server sort order
     * @return true if the resource order changed
     */
    public boolean setResourceSortOrder(String resourceSortOrder) {
        if(resourceComparator.setSortOrder(resourceSortOrder)) {
            notifyListenersOfChange(CHANGE_SORT_BY);
            return true;
        }
        return false;
    }
}
