package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.util.Utils;

/**
 * Created by gareth on 02/01/18.
 */

public abstract class PagedList<T extends Parcelable> implements ItemStore<T>, Parcelable {

    private static final String TAG = "PagedList";
    public static final int CHANGE_ALL_ITEMS_REMOVED = 0;
    public static final int CHANGE_SORT_ORDER = 1;
    protected static final int NOT_TRACKED_PAGE_ID = -2;
    public static int MISSING_ITEMS_PAGE = -1;
    private String itemType;
    private final SortedMap<Integer,Integer> pagesLoadedIdxToSizeMap = new TreeMap<>();
    private ArrayList<T> items;
    private ArrayList<T> sortedItems;
    //FIXME improvement - add an index (adds safety on add) - private LongSparseArray<T> itemIdToItemMap = new LongSparseArray<>();
    private final HashMap<Integer, Long> pagesLoadingPageIdxToLoadIdMap = new HashMap<>();
    private final HashSet<Integer> pagesFailedToLoad = new HashSet<>();
    private boolean fullyLoaded;
    private ReentrantLock pageLoadLock;
    private boolean retrieveItemsInReverseOrder;
    private boolean isGrowingOrganically;
    private Set<ChangeListener> changeListeners = new HashSet<>();
    private int totalPages = -1;

    public PagedList(String itemType) {
        this(itemType, 10);
    }

    public PagedList(String itemType, int maxExpectedItemCount) {
        this.itemType = itemType;
        this.items = new ArrayList<>(Math.max(0,maxExpectedItemCount));
        this.sortedItems = new ArrayList<>(Math.max(0,maxExpectedItemCount));
        this.pageLoadLock = new ReentrantLock();
    }

    public String getItemType() {
        return itemType;
    }

    public boolean isRetrieveItemsInReverseOrder() {
        return retrieveItemsInReverseOrder;
    }

    /**
     *
     * @param retrieveItemsInReverseOrder
     * @return true if the items were resorted
     */
    public boolean setRetrieveItemsInReverseOrder(boolean retrieveItemsInReverseOrder) {
        if(this.retrieveItemsInReverseOrder != retrieveItemsInReverseOrder) {
            this.retrieveItemsInReverseOrder = retrieveItemsInReverseOrder;
            if (items.size() > 0) {
                sortItems();
                notifyListenersOfChange(CHANGE_SORT_ORDER);
                return true;
            }
        }
        return false;
    }

    public void addChangeListener(ChangeListener l) {
        changeListeners.add(l);
    }

    public void removeChangeListener(ChangeListener l) {
        changeListeners.remove(l);
    }


    public void notifyListenersOfChange(int changeType) {
        for(ChangeListener l : changeListeners) {
            l.onChange(changeType);
        }
    }

    protected void resetSortOrder() {
        sortedItems.clear();
        getUnsortedItems().addAll(items);
    }

    protected void sortItems(List<T> items) {
        Collections.sort(sortedItems, Collections.reverseOrder());
    }

    protected void sortItems() {
        sortItems(sortedItems);
    }

    public Integer getAMissingPage() {
        if(!pagesFailedToLoad.isEmpty()) {
            return getNextPageToReload();
        }
        if(!fullyLoaded) {
            int page = 0;
            if(pagesLoadedIdxToSizeMap.size() > 0) {
                if (pagesLoadedIdxToSizeMap.firstKey() == 0) {
                    page = pagesLoadedIdxToSizeMap.lastKey() + 1;
                } else {
                    page = pagesLoadedIdxToSizeMap.firstKey() - 1;
                    if (page < 0) {
                        Logging.log(Log.ERROR, TAG, "Model of %1$s thinks a negative page is missing! - This should be impossible", Utils.getId(this));
                        return null;
                    }
                }
            }
            if(!pagesLoadingPageIdxToLoadIdMap.containsKey(page)) {
                return page;
            }
        }
        return null;
    }

    public PagedList(Parcel in) {
        try {
            itemType = in.readString();
            ParcelUtils.readMap(in, pagesLoadedIdxToSizeMap);
            items = ParcelUtils.readArrayList(in, getClass().getClassLoader());
            sortedItems = new ArrayList<>(items);
            ParcelUtils.readMap(in, pagesLoadingPageIdxToLoadIdMap);
            ParcelUtils.readIntSet(in, pagesFailedToLoad);
            fullyLoaded = ParcelUtils.readBool(in);
            retrieveItemsInReverseOrder = ParcelUtils.readBool(in);
            isGrowingOrganically = ParcelUtils.readBool(in);
            totalPages = in.readInt();

            if (pageLoadLock == null) {
                this.pageLoadLock = new ReentrantLock();
            }
            sortItems();
        } catch(RuntimeException e) {
            if(itemType == null) {
                itemType = "???";
            }
            if(items == null) {
                items = new ArrayList<>(0);
            }
            Logging.log(Log.ERROR, TAG, "Unable to load %1$s from parcel", Utils.getId(this));
            Logging.recordException(e);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(itemType);
        ParcelUtils.writeMap(dest, pagesLoadedIdxToSizeMap);
        //NOTE: I've chosen to write the sorted items here. The basis being that this the order the user expects to see.
        // technically, this may be right for the classes like piwigo album but not be so good for pagedlist as a standalone.
        ParcelUtils.writeArrayList(dest, sortedItems);
        ParcelUtils.writeMap(dest, pagesLoadingPageIdxToLoadIdMap);
        ParcelUtils.writeIntSet(dest, pagesFailedToLoad);
        ParcelUtils.writeBool(dest, fullyLoaded);
        ParcelUtils.writeBool(dest, retrieveItemsInReverseOrder);
        ParcelUtils.writeBool(dest, isGrowingOrganically);
        dest.writeInt(totalPages);
    }

    public void updateMaxExpectedItemCount(int newCount) {
        items.ensureCapacity(newCount);
        sortedItems.ensureCapacity(newCount);
    }

    public void acquirePageLoadLock() {
        pageLoadLock.lock();
    }

    public void releasePageLoadLock() {
        pageLoadLock.unlock();
    }

    public void recordPageBeingLoaded(long loaderId, int pageNum) {
        pagesLoadingPageIdxToLoadIdMap.put(pageNum, loaderId);
    }

    public boolean isPageLoadedOrBeingLoaded(int pageNum) {
        return pagesLoadedIdxToSizeMap.containsKey(pageNum) || pagesLoadingPageIdxToLoadIdMap.containsKey(pageNum);
    }

    public boolean hasNoFailedPageLoads() {
        return pagesFailedToLoad.isEmpty();
    }

    public Integer getNextPageToReload() {
        Integer retVal = null;
        if (!pagesFailedToLoad.isEmpty()) {
            Iterator<Integer> iter = pagesFailedToLoad.iterator();
            retVal = iter.next();
            iter.remove();
        }
        return retVal;
    }

    protected void recordPageLoadSucceeded(int pageIdx, int itemsLoaded) {
        pagesLoadingPageIdxToLoadIdMap.remove(pageIdx);
        pagesLoadedIdxToSizeMap.put(pageIdx, itemsLoaded);
    }

    public boolean isTrackingPageLoaderWithId(long loaderId) {
        return pagesLoadingPageIdxToLoadIdMap.containsValue(loaderId);
    }

    public void recordPageLoadFailed(long loaderId) {
        for(Map.Entry<Integer,Long> pageLoadingEntry : pagesLoadingPageIdxToLoadIdMap.entrySet()) {
            if(pageLoadingEntry.getValue().equals(loaderId)) {
                pagesFailedToLoad.add(pageLoadingEntry.getKey());
            }
        }
    }

    protected int getPageInsertPosition(int page, int pageSize) {
        int insertAtIdx = 0;
        for(Map.Entry<Integer,Integer> pageIdxToSizeEntry : pagesLoadedIdxToSizeMap.entrySet()) {
            if((isRetrieveItemsInReverseOrder() && pageIdxToSizeEntry.getKey() > page) || !isRetrieveItemsInReverseOrder() && pageIdxToSizeEntry.getKey() < page) {
                insertAtIdx += pageIdxToSizeEntry.getValue();
            }
        }
        return insertAtIdx;
    }

    /**
     * WARNING: duplicates WILL be added here if provided.
     * @param page
     * @param pageSize
     * @param itemsToAdd
     * @return
     */
    public synchronized int addItemPage(int page, /*int pages, */ int pageSize, List<T> itemsToAdd) {
        List<T> newItems = itemsToAdd;
        if(pageSize < newItems.size()) {
            String errorMessage = String.format(Locale.UK, "Expected page size (%1$d) did not match number of items contained in page (%2$d) for page %3$d of %4$s", pageSize, itemsToAdd.size(), page, Utils.getId(this));
            Logging.log(Log.ERROR, TAG, errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        newItems = prePageInsert(newItems);
        int firstInsertPos = 0;
        try {
            if (newItems.size() > 0) {
                firstInsertPos = Math.min(Math.max(0, getPageInsertPosition(page, pageSize)), items.size());
                items.addAll(firstInsertPos, newItems);
                sortedItems.addAll(firstInsertPos, newItems);
            }
            recordPageLoadSucceeded(page, newItems.size());
            isGrowingOrganically = calculateIfGrowingOrganically();
            fullyLoaded = calculateIsFullyLoadedCheck(page, pageSize, itemsToAdd);
        } catch(IllegalStateException e) {
            // page already loaded (can occur after resume...)
            Logging.log(Log.DEBUG, TAG, "ignoring page already loaded in %1$s", Utils.getId(this));
        }
        postPageInsert(sortedItems, newItems);
        return firstInsertPos;
    }

    protected boolean calculateIfGrowingOrganically() {
        // if any page is loaded and at that point, not all pages are present from 0 or from x (if reversed)
        int lastPageIdx = -1;
        List<Integer> orderedPagesLoaded = new ArrayList<>(pagesLoadedIdxToSizeMap.keySet());
        int firstPage = 0;
        int nextPageOffset = 1;
        int lastPage = orderedPagesLoaded.size() -1;
        if(isRetrieveItemsInReverseOrder()) {
            firstPage = lastPage;
            lastPage = 0;
            nextPageOffset = -1;
        }
        if(lastPage > 0) {
            // more than a single page loaded.
            for (int i = firstPage; i <= lastPage; i += nextPageOffset) {
                Integer pageIdx = orderedPagesLoaded.get(i);
                if (lastPageIdx >= 0 && pageIdx != lastPageIdx + nextPageOffset) {
                    return true;
                }
                lastPageIdx = pageIdx;
            }
        }
        return false;
    }

    /**
     * This method is called internally. It make no sense to call it directly.
     * @param pageIdx page just loaded
     * @param pageSize size of page loaded
     * @param itemsToAdd items in the page (some might have been filtered out prior to load, but this is the full list provided)
     * @return boolean if the list is fully loaded
     */
    protected boolean calculateIsFullyLoadedCheck(int pageIdx, int pageSize, List<T> itemsToAdd) {
        boolean isFullyLoaded;
        if(isRetrieveItemsInReverseOrder()) {
            // we're processing page 0 and no pages need reloaded
            isFullyLoaded = pageIdx == 0 && getNextPageToReload() == null;
        } else {
            // all pages with a number less than this one are already loaded.
            boolean loadingHighestPageIdxSoFar = pagesLoadedIdxToSizeMap.size() == pageIdx + 1;
            // AND EITHER the totalPages field has been set and matches the current pageIdx -1
            boolean loadingLastPage = pageIdx == getTotalPages() -1;
            // OR the current page contained less items than it could
            isFullyLoaded = loadingHighestPageIdxSoFar && (loadingLastPage || itemsToAdd.size() < pageSize);
        }
        return isFullyLoaded;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public void resetTotalPages() {
        totalPages = -1;
    }

    protected void postPageInsert(ArrayList<T> sortedItems, List<T> newItems) {
        // do nothing as we assume the pages are arriving sorted before insert and being inserted in the correct place
    }

    /**
     * Perform a simple sort, then reversing.
     * @param newItems
     * @return
     */
    protected List<T> prePageInsert(List<T> newItems) {
        return newItems;
    }


    public void markAsFullyLoaded() {
        fullyLoaded = true;
    }

    public void clear() {
        items.clear();
        sortedItems.clear();
        pagesLoadedIdxToSizeMap.clear();
        fullyLoaded = false;
        pagesLoadingPageIdxToLoadIdMap.clear();
        pagesFailedToLoad.clear();
        notifyListenersOfChange(CHANGE_ALL_ITEMS_REMOVED);
    }

    /**
     * Should return the total number of items expected to be present in items once all pages are loaded.
     *
     * @return
     */
    public long getMaxItemCount() {
        return -1;
    }

    @Override
    public T getItemByIdx(int idx) {
        int wantedIdx = idx;
        if (retrieveItemsInReverseOrder) {
            wantedIdx = getReverseItemIdx(idx);
        }
        if(!isFullyLoaded() && isGrowingOrganically()) {
            int adjustedIdx = adjustIdxForWhatIsLoaded(wantedIdx);
            try {
                return sortedItems.get(adjustedIdx);
            } catch(ArrayIndexOutOfBoundsException e) {
                IndexOutOfBoundsException ioobe = new IndexOutOfBoundsException("Item position "+idx+" out of range");
                ioobe.addSuppressed(e);
                throw ioobe;
            }
        }
        return sortedItems.get(wantedIdx);
    }

    protected boolean isGrowingOrganically() {
        return isGrowingOrganically;
    }

    /**
     * The server idx would exactly equal local idx if we had a copy, but if pages are missing
     * local idx may be very different.
     * @param serverIdx the idx of the resource if all pages were loaded
     * @return the actual list idx given current load status.
     */
    protected int getLocalIdxFromServerIdx(int serverIdx) {
        if(serverIdx >= 0) {
            int listIdx = -1;
            int resourceCountAllToAndIncCurrentPage = 0;
            int firstPageInListResourceCount = -1; // this might be smaller than the rest if reverse ordered (and item count not perfectly divisible by page size)
            int desiredIdxOffsetFromFirstPage = 0;

            for (Map.Entry<Integer, Integer> pageIdxToSizeEntry : pagesLoadedIdxToSizeMap.entrySet()) {
                int thisPageIdx = pageIdxToSizeEntry.getKey();
                int itemsInThisPage = pageIdxToSizeEntry.getValue();
                if(firstPageInListResourceCount < 0) {
                    firstPageInListResourceCount = itemsInThisPage;
                    resourceCountAllToAndIncCurrentPage = itemsInThisPage;
                    desiredIdxOffsetFromFirstPage = serverIdx - firstPageInListResourceCount;
                } else {
                    resourceCountAllToAndIncCurrentPage = firstPageInListResourceCount + (itemsInThisPage * thisPageIdx);
                }
                int pageIdxContainingServerIdx = 1;
                if(itemsInThisPage > 0) {
                    pageIdxContainingServerIdx += (desiredIdxOffsetFromFirstPage / itemsInThisPage);
                }
                listIdx += itemsInThisPage;
                if (serverIdx < resourceCountAllToAndIncCurrentPage && thisPageIdx <= pageIdxContainingServerIdx) {
                    int overshoot = resourceCountAllToAndIncCurrentPage - (serverIdx + 1);//+1 because idx is zero based
                    listIdx -= overshoot;
                    return listIdx;
                }
            }
            Logging.log(Log.WARN, TAG, "Unable to find a page with the resource index %1$d present in %4$s. There are %2$d resources loaded across %3$d pages", serverIdx, resourceCountAllToAndIncCurrentPage, pagesLoadedIdxToSizeMap.size(), Utils.getId(this));
        } else {
            Logging.log(Log.WARN, TAG, "resource index provided cannot ever be in a page of loaded items in %4$s. Pages : %1$d, itemCount %2$d, resourceIdx : %3$d", pagesLoadedIdxToSizeMap.size(), getItemCount(), serverIdx, Utils.getId(this));
        }
        return -1;
    }

    protected int adjustIdxForWhatIsLoaded(int wantedIdx) {
        return getLocalIdxFromServerIdx(wantedIdx);
    }

    @Override
    public ArrayList<T> getItems() {
        return sortedItems;
    }

    public ArrayList<T> getUnsortedItems() {
        return items;
    }

    public int getPagesLoadedIdxToSizeMap() {
        return pagesLoadedIdxToSizeMap.size();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public boolean isFullyLoaded() {
        return fullyLoaded;
    }

    public abstract Long getItemId(T item);

    public boolean containsItem(T item) {
        return getItemIdx(item) >= 0;
    }

    /**
     * WARNING: duplicates WILL be added here if provided.
     *
     * Add an item to the end of the list.
     * Note that this won't affect the paging calculations as they are done on the fly.
     *
     * @param item
     */
    @Override
    public void addItem(T item) {
        int insertAtIdx = getItemInsertPosition(item);
        addItem(insertAtIdx, item);
    }

    protected void addItem(int insertAtIdx, T item) {
        if(!updatePageLoadedCount(insertAtIdx -1, +1)) {
            Logging.log(Log.WARN, TAG, "AddItem (%1$s) at idx %2$d. Unable to alter page loaded count in %3$s.", item, insertAtIdx, Utils.getId(this));
        }
        items.add(insertAtIdx, item);
        sortedItems.add(insertAtIdx, item);
        postItemInsert(item);
    }

    protected int getItemInsertPosition(T item) {
        if(isRetrieveItemsInReverseOrder()) {
            return 0;
        } else {
            return sortedItems.size();
        }
    }

    protected void postItemRemove(T item) {
        // do nothing.
    }

    protected void postItemInsert(T item) {
        sortItems();
    }

    @Override
    public boolean replace(T item, T newItem) {
        int idx = getItemIdx(item);
        if(idx >= 0) {
            remove(idx);
            addItem(idx, newItem);
            return true;
        }
        return false;
    }

    @Override
    public synchronized T remove(int idx) {
        T item = sortedItems.remove(idx);
        items.remove(item);//FIXME - this is potentially not the right item if we used id (not available here!) to find the index and sort order isn't original
        if(!updatePageLoadedCount(idx, -1)) {
            Logging.log(Log.WARN, TAG, "RemoveItem (%1$s) at idx %2%d Unable to alter page loaded count in %3$s.", item, idx, Utils.getId(this));
        }
        postItemRemove(item);
        return item;
    }

    @Override
    public synchronized boolean removeByEquality(T item) {
        int idx = sortedItems.indexOf(item);
        if(idx >= 0) {
            remove(idx);
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean remove(T item) {
        int idx = getItemIdx(item);
        if(idx >= 0) {
            remove(idx);
            return true;
        }
        return false;
    }

    /**
     *
     * @param idx index of item where change occurred
     * @param change the change to effect on the tracked page
     * @return true if the operation was deemed a success
     */
    private boolean updatePageLoadedCount(int idx, int change) {
        int pageIdx = getPageIndexContaining(idx);
        if(pageIdx >= 0) {
            int pageLoadedCount = pagesLoadedIdxToSizeMap.get(pageIdx) + change;
            if(pageLoadedCount > 0) {
                pagesLoadedIdxToSizeMap.put(pageIdx, pageLoadedCount);
            } else {
                pagesLoadedIdxToSizeMap.remove(pageIdx);
            }
            return true;
        }
        // if the item with this index isn't tracked in pages, then we succeeded
        return pageIdx == NOT_TRACKED_PAGE_ID;
    }

    protected int getPageIndexContaining(int resourceIdx) {
        if(resourceIdx >= 0) {
            int serverResourceIdx = -1;
            int resourceCountAllToAndIncCurrentPage = 0;
            int firstPageInListResourceCount = -1; // this might be smaller than the rest if reverse ordered (and item count not perfectly divisible by page size)
            for (Map.Entry<Integer, Integer> pageIdxToSizeEntry : pagesLoadedIdxToSizeMap.entrySet()) {
                int itemsInThisPage = pageIdxToSizeEntry.getValue();
                if(firstPageInListResourceCount < 0) {
                    firstPageInListResourceCount = itemsInThisPage;
                    resourceCountAllToAndIncCurrentPage = itemsInThisPage;
                } else {
                    resourceCountAllToAndIncCurrentPage = firstPageInListResourceCount + (itemsInThisPage * pageIdxToSizeEntry.getKey());
                }
                serverResourceIdx += itemsInThisPage;
                if (resourceIdx <= resourceCountAllToAndIncCurrentPage) {
//                    int overshoot = resourceIdx - resourceCountAllToAndIncCurrentPage;
//                    serverResourceIdx -= overshoot;
//                    return serverResourceIdx;
                    // this page contains the resource index in question
                    return pageIdxToSizeEntry.getKey();
                }
            }
            Logging.log(Log.WARN, TAG, "Unable to find a page with the resource index %1$d present in %4$s. There are %2$d resources loaded across %3$d pages", resourceIdx, resourceCountAllToAndIncCurrentPage, pagesLoadedIdxToSizeMap.size(), Utils.getId(this));
        } else {
            Logging.log(Log.WARN, TAG, "resource index provided cannot ever be in a page of loaded items in %4$s. Pages : %1$d, itemCount %2$d, resourceIdx : %3$d", pagesLoadedIdxToSizeMap.size(), getItemCount(), resourceIdx, Utils.getId(this));
        }
        return -1;
    }

    @Override
    public synchronized boolean removeAllByEquality(Collection<T> itemsForDeletion) {
        boolean changed = false;
        for(T item : itemsForDeletion) {
            boolean removed;
            do {
                removed = removeByEquality(item);
                changed |= removed;
            } while (removed);
        }
        return changed;
    }

    public synchronized boolean addMissingItems(Collection<? extends T> newItems) {
        if (newItems == null) {
            return false;
        }
        boolean changed = false;
        for (T c : newItems) {
            if(getItemIdx(c) < 0) {
                addItem(c);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public int getItemIdx(T item) {
        long seekId = getItemId(item);
        int idx = 0;
        for (T c : sortedItems) {
            if(seekId == getItemId(c)) {
                if(retrieveItemsInReverseOrder) {
                    return getReverseItemIdx(idx);
                }
                return idx;
            }
            idx++;
        }
        return -1;
    }

    private int getNonReversedItemIdx(int idx) {
        return idx - getReverseOffset(idx);
    }

    private int getReverseItemIdx(int idx) {
        if(getReverseOffset(idx) == 0) {
            return idx;
        }
        return getReverseOffset(idx) - idx;
    }

    /**
     * Override this for non standard ordering of items when reversed.
     * (If the whole list is flipped, this will be okay as it is).
     *
     * NOTE: if you implement reverse sorting of the comparator, you MUST ensure this returns zero
     *
     * @param idx index of the the item to 'reposition' by the offset
     * @return the offset from zero to subtract the idx from to get its new idx when reversing the list
     */
    protected int getReverseOffset(int idx) {
        return 0;
    }

    public boolean isPageLoaded(int pageNum) {
        return pagesLoadedIdxToSizeMap.containsKey(pageNum);
    }

    /**
     * Should be called if all the items are removed. Not otherwise.
     */
    protected void clearPagesLoaded() {
        pagesLoadedIdxToSizeMap.clear();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    protected boolean hasOnlyEmptyFirstPage() {
        return pagesLoadedIdxToSizeMap.size() == 1 && Objects.equals(pagesLoadedIdxToSizeMap.get(0),0);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PagedList{");
        sb.append("itemType='").append(itemType).append('\'');
        sb.append(", pagesLoadedIdxToSizeMap=").append(pagesLoadedIdxToSizeMap);
        if(sortedItems.size() < 50) {
            sb.append(", items=").append(items);
            sb.append(", sortedItems=").append(sortedItems);
        } else {
            sb.append(", itemsCount=").append(items.size());
            sb.append(", sortedItemsCount=").append(sortedItems.size());
        }
        sb.append(", pagesLoadingPageIdxToLoadIdMap=").append(pagesLoadingPageIdxToLoadIdMap);
        sb.append(", pagesFailedToLoad=").append(pagesFailedToLoad);
        sb.append(", fullyLoaded=").append(fullyLoaded);
        sb.append(", pageLoadLock=").append(pageLoadLock);
        sb.append(", retrieveItemsInReverseOrder=").append(retrieveItemsInReverseOrder);
        sb.append('}');
        return sb.toString();
    }

    public interface ChangeListener {

        void onChange(int changeType);
    }
}
