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

    public PagedList(String itemType) {
        this(itemType, 10);
    }

    public PagedList(String itemType, int maxExpectedItemCount) {
        this.itemType = itemType;
        this.items = new ArrayList<>(maxExpectedItemCount);
        this.sortedItems = new ArrayList<>(maxExpectedItemCount);
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
                return true;
            }
        }
        return false;
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
        ParcelUtils.writeArrayList(dest, items);
        ParcelUtils.writeMap(dest, pagesLoadingPageIdxToLoadIdMap);
        ParcelUtils.writeIntSet(dest, pagesFailedToLoad);
        ParcelUtils.writeBool(dest, fullyLoaded);
        ParcelUtils.writeBool(dest, retrieveItemsInReverseOrder);
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
    public int addItemPage(int page, /*int pages, */ int pageSize, List<T> itemsToAdd) {
        List<T> newItems = itemsToAdd;
        if(pageSize < newItems.size()) {
            String errorMessage = String.format(Locale.UK, "Expected page size (%1$d) did not match number of items contained in page (%2$d) for page %3$d of %4$s", pageSize, itemsToAdd.size(), page, Utils.getId(this));
            Logging.log(Log.ERROR, TAG, errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        recordPageLoadSucceeded(page, newItems.size());
        newItems = prePageInsert(newItems);
        int firstInsertPos = 0;
        try {
            if (newItems.size() > 0) {
                firstInsertPos = Math.min(Math.max(0, getPageInsertPosition(page, pageSize)), items.size());
                items.addAll(firstInsertPos, newItems);
                sortedItems.addAll(firstInsertPos, newItems);
            }
            pagesLoadedIdxToSizeMap.put(page, pageSize);
            fullyLoaded = internalIsFullyLoadedCheck(page, pageSize, itemsToAdd);
        } catch(IllegalStateException e) {
            // page already loaded (can occur after resume...)
            Logging.log(Log.DEBUG, TAG, "ignoring page already loaded in %1$s", Utils.getId(this));
        }
        postPageInsert(sortedItems, newItems);
        return firstInsertPos;
    }

    /**
     * This method is called internally. It make no sense to call it directly.
     * @param page page just loaded
     * @param pageSize size of page loaded
     * @param itemsToAdd items in the page (some might have been filtered out prior to load, but this is the full list provided)
     * @return boolean if the list is fully loaded
     */
    protected boolean internalIsFullyLoadedCheck(int page, int pageSize, List<T> itemsToAdd) {
        if(isRetrieveItemsInReverseOrder()) {
            if(page == 0 && getNextPageToReload() == null) {
                // we're processing page 0 and no pages need reloaded
                return true;
            }
        } else {
            if(itemsToAdd.size() < pageSize && pagesLoadedIdxToSizeMap.size() == page + 1) {
                // the current page contained less items than it could and all pages with a number less than this one are already loaded.
                return true;
            }
        }
        return false;
    }

    private void postPageInsert(ArrayList<T> sortedItems, List<T> newItems) {
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
    }

    /**
     * Should return the total number of items expected to be present in items once all pages are loaded.
     *
     * @return
     */
    public long getMaxResourceCount() {
        throw new UnsupportedOperationException("Implement this if needed");
    }

    @Override
    public T getItemByIdx(int idx) {
        if (retrieveItemsInReverseOrder) {
            return sortedItems.get(getReverseItemIdx(idx));
        }
        return sortedItems.get(idx);
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
        updatePageLoadedCount(insertAtIdx -1, +1);
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

    protected void postItemInsert(T item) {
        sortItems();
    }

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
    public T remove(int idx) {
        updatePageLoadedCount(idx, -1);
        T item = sortedItems.remove(idx);
        items.remove(item);//FIXME - this is potentially not the right item if we used id (not available here!) to find the index and sort order isn't original
        return item;
    }

    @Override
    public boolean removeByEquality(T item) {
        int idx = sortedItems.indexOf(item);
        if(idx >= 0) {
            remove(idx);
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(T item) {
        int idx = getItemIdx(item);
        if(idx >= 0) {
            remove(idx);
            return true;
        }
        return false;
    }

    private void updatePageLoadedCount(int idx, int change) {
        int pageIdx = getPageIndexContaining(idx);
        if(pageIdx < 0) {
            Logging.log(Log.WARN, TAG, "Unable to alter page loaded count by %1$+d in %2$s. Affected page not found", change, Utils.getId(this));
        } else {
            int pageLoadedCount = pagesLoadedIdxToSizeMap.get(pageIdx) + change;
            if(pageLoadedCount > 0) {
                pagesLoadedIdxToSizeMap.put(pageIdx, pageLoadedCount);
            } else {
                pagesLoadedIdxToSizeMap.remove(pageIdx);
            }
        }
    }

    protected int getPageIndexContaining(int resourceIdx) {
        if(resourceIdx >= 0) {
            int resourceIdxs = 0;
            for (Map.Entry<Integer, Integer> pageIdxToSizeEntry : pagesLoadedIdxToSizeMap.entrySet()) {
                int itemsInThisPage = pageIdxToSizeEntry.getValue();
                resourceIdxs += itemsInThisPage;
                if (resourceIdx <= resourceIdxs) {
                    // this page contains the resource index in question
                    return pageIdxToSizeEntry.getKey();
                }
            }
            Logging.log(Log.WARN, TAG, "Unable to find a page with the resource index %1$d present in %4$s. There are %2$d resources loaded across %3$d pages", resourceIdx, resourceIdxs, pagesLoadedIdxToSizeMap.size(), Utils.getId(this));
        } else {
            Logging.log(Log.WARN, TAG, "resource index provided cannot ever be in a page of loaded items in %4$s. Pages : %1$d, itemCount %2$d, resourceIdx : %3$d", pagesLoadedIdxToSizeMap.size(), getItemCount(), resourceIdx, Utils.getId(this));
        }
        return -1;
    }

    @Override
    public boolean removeAllByEquality(Collection<T> itemsForDeletion) {
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

    public boolean addMissingItems(Collection<? extends T> newItems) {
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
}
