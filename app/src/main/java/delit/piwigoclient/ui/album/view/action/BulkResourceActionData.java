package delit.piwigoclient.ui.album.view.action;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;

public class BulkResourceActionData implements Parcelable {
    public static final Creator<BulkResourceActionData> CREATOR = new Creator<BulkResourceActionData>() {
        @Override
        public BulkResourceActionData createFromParcel(Parcel in) {
            return new BulkResourceActionData(in);
        }

        @Override
        public BulkResourceActionData[] newArray(int size) {
            return new BulkResourceActionData[size];
        }
    };
    public final static int ACTION_DELETE = 1;
    public final static int ACTION_UPDATE_PERMISSIONS = 2;
    public final static int ACTION_DOWNLOAD_ALL = 3;
    private final static int maxHttpRequestsQueued = 20;
    final HashSet<Long> selectedItemIds;
    final HashSet<Long> itemsUpdated;
    final HashMap<Long, Long> itemsUpdating;
    final HashSet<ResourceItem> selectedItems;
    boolean resourceInfoAvailable;
    private ArrayList<Long> trackedMessageIds = new ArrayList<>();
    private final int action;

    public BulkResourceActionData(HashSet<Long> selectedItemIds, HashSet<ResourceItem> selectedItems, int action) {
        this.selectedItemIds = selectedItemIds;
        this.selectedItems = selectedItems;
        this.resourceInfoAvailable = false; //FIXME when Piwigo provides this info as standard, this can be removed and the method simplified.
        itemsUpdated = new HashSet<>(selectedItemIds.size());
        itemsUpdating = new HashMap<>();
        this.action = action;
    }

    BulkResourceActionData(Parcel in) {
        selectedItemIds = ParcelUtils.readLongSet(in);
        itemsUpdated = ParcelUtils.readLongSet(in);
        itemsUpdating = ParcelUtils.readMap(in);
        selectedItems = ParcelUtils.readHashSet(in, getClass().getClassLoader());
        resourceInfoAvailable = ParcelUtils.readBool(in);
        trackedMessageIds = ParcelUtils.readLongArrayList(in);
        action = in.readInt();
    }

    public int getAction() {
        return action;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        ParcelUtils.writeLongSet(dest, selectedItemIds);
        ParcelUtils.writeLongSet(dest, itemsUpdated);
        ParcelUtils.writeSet(dest, selectedItems);
        ParcelUtils.writeBool(dest, resourceInfoAvailable);
        ParcelUtils.writeLongArrayList(dest, trackedMessageIds);
        dest.writeInt(action);
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

    Set<ResourceItem> getItemsWithoutLinkedAlbumData() {
        if (itemsUpdated.size() == 0) {
            return selectedItems;
        }
        Set<ResourceItem> itemsWithoutLinkedAlbumData = new HashSet<>();
        Iterator<Map.Entry<Long, Long>> entriesIter = itemsUpdating.entrySet().iterator();
        while (entriesIter.hasNext()) { // remove any supposedly updating but that whose service calls are not running.
            Map.Entry<Long, Long> itemUpdating = entriesIter.next();
            if (!trackedMessageIds.contains(itemUpdating.getValue())) {
                entriesIter.remove();
            }
        }
        for (ResourceItem r : selectedItems) {
            if (!itemsUpdated.contains(r.getId()) && itemsUpdating.get(r.getId()) == null) {
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

    public boolean removeProcessedResources(HashSet<? extends ResourceItem> deletedItems) {
        HashSet<Long> deletedItemIds = PiwigoUtils.toSetOfIds(deletedItems);
        selectedItemIds.removeAll(deletedItemIds);
        itemsUpdated.removeAll(deletedItemIds);
        PiwigoUtils.removeAll(selectedItems, deletedItemIds);
        return selectedItemIds.size() == 0;
    }

    public boolean isEmpty() {
        return selectedItemIds.isEmpty();
    }

    public long trackMessageId(long messageId) {
        trackedMessageIds.add(messageId);
        return messageId;
    }

    public boolean isTrackingMessageId(long messageId) {
        return trackedMessageIds.remove(messageId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public int getTrackedMessageIdsCount() {
        return trackedMessageIds.size();
    }

    public void getResourcesInfoIfNeeded(AbstractViewAlbumFragment<?, ?> fragment) {
        int simultaneousCalls = trackedMessageIds.size();
        if (maxHttpRequestsQueued > simultaneousCalls) {
            for (ResourceItem item : getItemsWithoutLinkedAlbumData()) {
                simultaneousCalls++;
                itemsUpdating.put(item.getId(), trackMessageId(fragment.addActiveServiceCall(R.string.progress_loading_resource_details, new ImageGetInfoResponseHandler<>(item))));
                if (simultaneousCalls >= maxHttpRequestsQueued) {
                    break;
                }
            }
        }
    }
}
