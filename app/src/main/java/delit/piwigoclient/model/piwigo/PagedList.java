package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;

/**
 * Created by gareth on 02/01/18.
 */

public abstract class PagedList<T extends Parcelable> implements IdentifiableItemStore<T>, Parcelable {

    private static final String TAG = "PagedList";
    public static int MISSING_ITEMS_PAGE = -1;
    private String itemType;
    private final SortedSet<Integer> pagesLoaded = new TreeSet<>();
    private ArrayList<T> items;
    private final HashMap<Long, Integer> pagesBeingLoaded = new HashMap<>();
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

    protected abstract void sortItems();

    public Integer getAMissingPage() {
        if(!pagesFailedToLoad.isEmpty()) {
            return getNextPageToReload();
        }
        if(!fullyLoaded) {
            int page = 0;
            if(pagesLoaded.size() > 0) {
                if (pagesLoaded.first() == 0) {
                    page = pagesLoaded.last() + 1;
                } else {
                    page = pagesLoaded.first() - 1;
                    if (page < 0) {
                        Logging.log(Log.ERROR, TAG, "Model thinks a negative page is missing! - This should be impossible");
                        return null;
                    }
                }
            }
            if(!pagesBeingLoaded.containsValue(page)) {
                return page;
            }
        }
        return null;
    }

    public PagedList(Parcel in) {
        try {
            itemType = in.readString();
            ParcelUtils.readIntSet(in, pagesLoaded);
            items = ParcelUtils.readArrayList(in, getClass().getClassLoader());
            ParcelUtils.readMap(in, pagesBeingLoaded, getClass().getClassLoader());
            ParcelUtils.readIntSet(in, pagesFailedToLoad);
            fullyLoaded = ParcelUtils.readBool(in);
            retrieveItemsInReverseOrder = ParcelUtils.readBool(in);

            if (pageLoadLock == null) {
                this.pageLoadLock = new ReentrantLock();
            }
        } catch(RuntimeException e) {
            if(itemType == null) {
                itemType = "???";
            }
            if(items == null) {
                items = new ArrayList<>(0);
            }
            Logging.log(Log.ERROR, TAG, "Unable to load from parcel");
            Logging.recordException(e);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(itemType);
        ParcelUtils.writeIntSet(dest, pagesLoaded);
        ParcelUtils.writeArrayList(dest, items);
        ParcelUtils.writeMap(dest, pagesBeingLoaded);
        ParcelUtils.writeIntSet(dest, pagesFailedToLoad);
        ParcelUtils.writeBool(dest, fullyLoaded);
        ParcelUtils.writeBool(dest, retrieveItemsInReverseOrder);
    }

    public void updateMaxExpectedItemCount(int newCount) {
        items.ensureCapacity(newCount);
    }

    public void acquirePageLoadLock() {
        pageLoadLock.lock();
    }

    public void releasePageLoadLock() {
        pageLoadLock.unlock();
    }

    public void recordPageBeingLoaded(long loaderId, int pageNum) {
        pagesBeingLoaded.put(loaderId, pageNum);
    }

    public boolean isPageLoadedOrBeingLoaded(int pageNum) {
        return pagesLoaded.contains(pageNum) || pagesBeingLoaded.containsValue(pageNum);
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

    public void recordPageLoadSucceeded(long loaderId) {
        pagesBeingLoaded.remove(loaderId);
    }

    public boolean isTrackingPageLoaderWithId(long loaderId) {
        return pagesBeingLoaded.containsKey(loaderId);
    }

    public void recordPageLoadFailed(long loaderId) {
        Integer pageNum = pagesBeingLoaded.remove(loaderId);
        if (pageNum != null) {
            pagesFailedToLoad.add(pageNum);
        }
    }

    private int earlierLoadedPages(int page) {
        Iterator<Integer> iter = pagesLoaded.iterator();
        int earlierPages = 0;
        while (iter.hasNext()) {
            int curPage = iter.next();
            if (curPage < page) {
                earlierPages++;
            } else if (curPage == page) {
                throw new IllegalStateException("Attempting to add page already loaded (" + curPage + ")");
            }
        }
        return earlierPages;
    }

    protected int getPageInsertPosition(int page, int pageSize) {
        return earlierLoadedPages(page) * pageSize;
    }

    /**
     * WARNING: duplicates WILL be added here if provided.
     * @param page
     * @param pageSize
     * @param newItems
     * @return
     */
    public int addItemPage(int page, /*int pages, */ int pageSize, Collection<T> newItems) {

        int firstInsertPos = 0;
        try {
            if (newItems.size() > 0) {
                firstInsertPos = Math.min(Math.max(0, getPageInsertPosition(page, pageSize)), items.size());
                items.addAll(firstInsertPos, newItems);
            }
            pagesLoaded.add(page);
            if (newItems.size() < pageSize && pagesLoaded.size() == page + 1) {
                fullyLoaded = true;
            }
        } catch(IllegalStateException e) {
            // page already loaded (can occur after resume...)
            Logging.log(Log.DEBUG, TAG, "ignoring page already loaded");
        }
        return firstInsertPos;
    }

    public void markAsFullyLoaded() {
        fullyLoaded = true;
    }

    public void clear() {
        items.clear();
        pagesLoaded.clear();
        fullyLoaded = false;
        pagesBeingLoaded.clear();
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

    protected int getAdjustedIdx(int idx) {
        return items.size() - 1 - idx;
    }

    public int getDisplayIdx(T item) {
        int rawIdx = getItemIdx(item);
        if (retrieveItemsInReverseOrder) {
            return getAdjustedIdx(rawIdx);
        }
        return rawIdx;
    }

    @Override
    public T getItemByIdx(int idx) {
        if (retrieveItemsInReverseOrder) {
            return items.get(getAdjustedIdx(idx));
        }
        return items.get(idx);
    }

    @Override
    public ArrayList<T> getItems() {
        return items;
    }

    public int getPagesLoaded() {
        return pagesLoaded.size();
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

    @Override
    public T getItemById(long selectedItemId) {
        for (T item : items) {
            if (getItemId(item) == selectedItemId) {
                return item;
            }
        }
        throw new IllegalArgumentException("No " + itemType + " present with id : " + selectedItemId);
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
        items.add(item);
    }

    public T remove(int idx) {
        return items.remove(idx);
    }

    @Override
    public boolean remove(T item) {
        int idx = getItemIdx(item);
        if(idx >= 0) {
            items.remove(idx);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeAll(Collection<T> itemsForDeletion) {
        boolean changed = false;
        for(T item : itemsForDeletion) {
            changed = remove(item);
        }
        return changed;
    }

    public boolean addMissingItems(List<? extends T> newItems) {
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
        for (T c : items) {
            if(seekId == getItemId(c)) {
                return idx;
            }
            idx++;
        }
        return -1;
    }

    public boolean isPageLoaded(int pageNum) {
        return pagesLoaded.contains(pageNum);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
