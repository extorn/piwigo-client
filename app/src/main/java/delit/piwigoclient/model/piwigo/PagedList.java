package delit.piwigoclient.model.piwigo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by gareth on 02/01/18.
 */

public abstract class PagedList<T> implements IdentifiableItemStore<T>, Serializable {

    private final String itemType;
    private final SortedSet<Integer> pagesLoaded = new TreeSet<>();
    private boolean fullyLoaded;
    private final ArrayList<T> items;

    public PagedList(String itemType) {
        this(itemType, 10);
    }

    public PagedList(String itemType, int maxExpectedItemCount) {
        this.itemType = itemType;
        this.items = new ArrayList<>(maxExpectedItemCount);
    }

    public void updateMaxExpectedItemCount(int newCount) {
        items.ensureCapacity(newCount);
    }

    private int earlierLoadedPages(int page) {
        Iterator<Integer> iter = pagesLoaded.iterator();
        int earlierPages = 0;
        while(iter.hasNext()) {
            int curPage = iter.next();
            if(curPage < page) {
                earlierPages++;
            } else if(curPage == page) {
                throw new IllegalStateException("Attempting to add page already loaded (" + curPage+")");
            }
        }
        return earlierPages;
    }

    protected int getPageInsertPosition(int page, int pageSize) {
        return earlierLoadedPages(page) * pageSize;
    }

    public int addItemPage(int page, int pageSize, Collection<T> newItems) {

        int firstInsertPos = 0;
        if(newItems.size() > 0) {
            firstInsertPos = Math.min(getPageInsertPosition(page, pageSize), items.size());
            items.addAll(firstInsertPos, newItems);
        }
        pagesLoaded.add(page);
        if(newItems.size() < pageSize) {
            fullyLoaded = true;
        }
        return firstInsertPos;
    }

    public void clear() {
        items.clear();
        pagesLoaded.clear();
    }

    /**
     * Should return the total number of items expected to be present in items once all pages are loaded.
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

    @Override
    public T getItemById(long selectedItemId) {
        for (T item : items) {
            if(getItemId(item) == selectedItemId) {
                return item;
            }
        }
        throw new IllegalArgumentException("No " + itemType + " present with id : " + selectedItemId);
    }

    /**
     * Add an item to the end of the list.
     * Note that this won't affect the paging calculations as they are done on the fly.
     * @param item
     */
    @Override
    public void addItem(T item) {
        items.add(item);
    }

    public T remove(int idx) {
        return items.remove(idx);
    }

    public boolean addMissingItems(List<? extends T> newItems) {
        if(newItems == null) {
            return false;
        }
        boolean changed = false;
        for(T c : newItems) {
            if(!items.contains(c)) {
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
}
