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

public class PiwigoUsernames implements Serializable {

    private SortedSet<Integer> pagesLoaded = new TreeSet<>();
    private boolean fullyLoaded;
    /**
     * An array of items in the gallery.
     */
    private final ArrayList<Username> items = new ArrayList<>();

    private int earlierLoadedPages(int page) {
        Iterator<Integer> iter = pagesLoaded.iterator();
        int earlierPages = 0;
        while(iter.hasNext()) {
            int curPage = iter.next();
            if(curPage < page) {
                earlierPages++;
            } else if(curPage == page) {
                throw new IllegalStateException("Attempting to add page already loaded (" + curPage+")");
            } else {
                break;
            }
        }
        return earlierPages;
    }

    public int addItemPage(int page, int pageSize, Collection<Username> newItems) {

        int firstInsertPos = 0;
        if(newItems.size() > 0) {
            int insertPosition = earlierLoadedPages(page) * pageSize;
            firstInsertPos = insertPosition;
            for (Username item : newItems) {
                items.add(insertPosition, item);
                insertPosition++;
            }
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

    public List<Username> getItems() {
        return items;
    }

    public int getPagesLoaded() {
        return pagesLoaded.size();
    }

    public long getResourcesCount() {
        return items.size();
    }

    public boolean isFullyLoaded() {
        return fullyLoaded;
    }

    public Username getUsernameById(Long selectedItemId) {
        for (Username item : items) {
            if(item.getId() == selectedItemId) {
                return item;
            }
        }
        throw new IllegalArgumentException("No Username present with id : " + selectedItemId);
    }
}
