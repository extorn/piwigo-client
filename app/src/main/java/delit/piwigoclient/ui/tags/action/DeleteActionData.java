package delit.piwigoclient.ui.tags.action;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.ResourceItem;

public class DeleteActionData implements Parcelable {
    final HashSet<Long> selectedItemIds;
    final HashSet<Long> itemsUpdated;
    final HashSet<ResourceItem> selectedItems;
    boolean resourceInfoAvailable;
    private ArrayList<Long> trackedMessageIds = new ArrayList<>();

    public DeleteActionData(HashSet<Long> selectedItemIds, HashSet<ResourceItem> selectedItems) {
        this.selectedItemIds = selectedItemIds;
        this.selectedItems = selectedItems;
        this.resourceInfoAvailable = false; //FIXME when Piwigo provides this info as standard, this can be removed and the method simplified.
        itemsUpdated = new HashSet<>(selectedItemIds.size());
    }

    public DeleteActionData(Parcel in) {
        selectedItemIds = ParcelUtils.readLongSet(in);
        itemsUpdated = ParcelUtils.readLongSet(in);
        selectedItems = ParcelUtils.readHashSet(in, getClass().getClassLoader());
        resourceInfoAvailable = ParcelUtils.readBool(in);
        trackedMessageIds = ParcelUtils.readLongArrayList(in);
    }

    public static final Creator<DeleteActionData> CREATOR = new Creator<DeleteActionData>() {
        @Override
        public DeleteActionData createFromParcel(Parcel in) {
            return new DeleteActionData(in);
        }

        @Override
        public DeleteActionData[] newArray(int size) {
            return new DeleteActionData[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        ParcelUtils.writeLongSet(dest, selectedItemIds);
        ParcelUtils.writeLongSet(dest, itemsUpdated);
        ParcelUtils.writeSet(dest, selectedItems);
        ParcelUtils.writeBool(dest, resourceInfoAvailable);
        ParcelUtils.writeLongArrayList(dest, trackedMessageIds);
    }

    public void updateLinkedAlbums(ResourceItem item) {
        itemsUpdated.add(item.getId());
        if (itemsUpdated.size() == selectedItemIds.size()) {
            resourceInfoAvailable = true;
        }
        selectedItems.add(item); // will replace the previous with this one.
    }

    public boolean isResourceInfoAvailable() {
        return resourceInfoAvailable;
    }

    public HashSet<Long> getSelectedItemIds() {
        return selectedItemIds;
    }

    public HashSet<ResourceItem> getSelectedItems() {
        return selectedItems;
    }

    public void clear() {
        selectedItemIds.clear();
        selectedItems.clear();
        itemsUpdated.clear();
    }

    public Set<ResourceItem> getItemsWithoutLinkedAlbumData() {
        if (itemsUpdated.size() == 0) {
            return selectedItems;
        }
        Set<ResourceItem> itemsWithoutLinkedAlbumData = new HashSet<>();
        for (ResourceItem r : selectedItems) {
            if (!itemsUpdated.contains(r.getId())) {
                itemsWithoutLinkedAlbumData.add(r);
            }
        }
        return itemsWithoutLinkedAlbumData;
    }

    public boolean removeProcessedResource(ResourceItem resource) {
        selectedItemIds.remove(resource.getId());
        selectedItems.remove(resource);
        itemsUpdated.remove(resource.getId());
        return selectedItemIds.size() == 0;
    }

    public boolean removeProcessedResources(HashSet<Long> deletedItemIds) {
        for (Long deletedResourceId : deletedItemIds) {
            selectedItemIds.remove(deletedResourceId);
            itemsUpdated.remove(deletedResourceId);
        }
        PiwigoUtils.removeAll(selectedItems, deletedItemIds);
        return selectedItemIds.size() == 0;
    }

    public boolean isEmpty() {
        return selectedItemIds.isEmpty();
    }

    public void trackMessageId(long messageId) {
        trackedMessageIds.add(messageId);
    }

    public boolean isTrackingMessageId(long messageId) {
        return trackedMessageIds.remove(messageId);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
