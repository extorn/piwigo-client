package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

import delit.piwigoclient.ui.common.util.ParcelUtils;

/**
 * Created by gareth on 02/01/18.
 */

public abstract class PagedList<T extends Parcelable> implements IdentifiableItemStore<T>, Parcelable {

    public static int MISSING_ITEMS_PAGE = -1;
    private final String itemType;
    private final SortedSet<Integer> pagesLoaded = new TreeSet<>();
    private final ArrayList<T> items;
    private final HashMap<Long, Integer> pagesBeingLoaded = new HashMap<>();
    private final HashSet<Integer> pagesFailedToLoad = new HashSet<>();
    private boolean fullyLoaded;
    private transient ReentrantLock pageLoadLock;

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

    public Integer getAMissingPage() {
        if(!pagesFailedToLoad.isEmpty()) {
            return getNextPageToReload();
        }
        if(!fullyLoaded) {
            int page = 0;
            if(pagesLoaded.size() > 0) {
                page = pagesLoaded.last() + 1;
            }
            if(!pagesBeingLoaded.containsValue(page)) {
                return page;
            }
        }
        return null;
    }

    public PagedList(Parcel in) {
        itemType = in.readString();
        ParcelUtils.readIntSet(in, pagesLoaded, null);
        items = in.readArrayList(null);
        in.readMap(pagesBeingLoaded, getClass().getClassLoader());
        ParcelUtils.readIntSet(in, pagesFailedToLoad, null);
        fullyLoaded = (boolean) in.readValue(null);

        if(pageLoadLock == null) {
            this.pageLoadLock = new ReentrantLock();
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(itemType);
        ParcelUtils.writeIntSet(dest, pagesLoaded);
        dest.writeList(items);
        dest.writeMap(pagesBeingLoaded);
        ParcelUtils.writeIntSet(dest, pagesFailedToLoad);
        dest.writeValue(fullyLoaded);
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

    public int addItemPage(int page, int pageSize, Collection<T> newItems) {

        int firstInsertPos = 0;
        if (newItems.size() > 0) {
            firstInsertPos = Math.min(Math.max(0, getPageInsertPosition(page, pageSize)), items.size());
            items.addAll(firstInsertPos, newItems);
        }
        pagesLoaded.add(page);
        if (newItems.size() < pageSize) {
            fullyLoaded = true;
        }
        return firstInsertPos;
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

    @Override
    public T getItemByIdx(int idx) {
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
        return items.contains(item);
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
    public void remove(T item) {
        items.remove(item);
    }

    @Override
    public boolean removeAll(Collection<T> itemsForDeletion) {
        return items.removeAll(itemsForDeletion);
    }

    public boolean addMissingItems(List<? extends T> newItems) {
        if (newItems == null) {
            return false;
        }
        boolean changed = false;
        for (T c : newItems) {
            if (!items.contains(c)) {
                addItem(c);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public int getItemIdx(T newTag) {
        return items.indexOf(newTag);
    }

    public boolean isPageLoaded(int pageNum) {
        return pagesLoaded.contains(pageNum);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
