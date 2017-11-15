package delit.piwigoclient.model.piwigo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 * <p>
 */
public class PiwigoAlbum implements Serializable {

    public static final CategoryItem ROOT_ALBUM = new CategoryItem(0, "--------", null, false, null, 0, 0, 0, null);

    private int subAlbumCount;
    private int spacerAlbums;
    private int advertCount;

    private SortedSet<Integer> pagesLoaded = new TreeSet<>();

    private Long id;
    /**
     * An array of items in the gallery.
     */
    private final ArrayList<GalleryItem> items = new ArrayList<>();

    private final transient Comparator<GalleryItem> itemComparator = new Comparator<GalleryItem>() {
        @Override
        public int compare(GalleryItem o1, GalleryItem o2) {
            boolean firstIsCategory = o1 instanceof CategoryItem;
            boolean secondIsCategory = o2 instanceof CategoryItem;
            if(firstIsCategory && secondIsCategory) {
                if(o1 == CategoryItem.ADVERT) {
                    return -1;
                } else if(o2 == CategoryItem.ADVERT) {
                    return 1;
                } else {
                    return 0;
                }
            } else if(!firstIsCategory && !secondIsCategory) {
                if(o1 == GalleryItem.ADVERT) {
                    return -1;
                } else if(o2 == GalleryItem.ADVERT) {
                    return 1;
                } else {
                    return 0;
                }
            } else if(firstIsCategory) {
                return -1;
            } else{
                return 1;
            }
        }
    };


    public Long getId() {
        return id;
    }

    public void addItem(CategoryItem item) {
        if(item != CategoryItem.ADVERT) {
            id = item.getParentId();
            subAlbumCount++;
        } else {
            advertCount++;
        }
        items.add(item);
        // ensure these are always placed first.
        Collections.sort(items, itemComparator);
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
            } else {
                break;
            }
        }
        return earlierPages;
    }

    public void addItemPage(int page, int pageSize, List<GalleryItem> newItems) {

        if(newItems.size() > 0) {
            int insertPosition = earlierLoadedPages(page) * pageSize;
            insertPosition += subAlbumCount;
            insertPosition += spacerAlbums;
            insertPosition += advertCount;
            id = newItems.get(0).getParentId();
            for (GalleryItem item : newItems) {
                if(item == GalleryItem.ADVERT) {
                    advertCount++;
                }
                items.add(insertPosition, item);
                insertPosition++;
            }
        }
        pagesLoaded.add(page);
    }

    public void clear() {
        items.clear();
        pagesLoaded.clear();
        subAlbumCount = 0;
        spacerAlbums = 0;
        advertCount = 0;
    }

    public List<GalleryItem> getItems() {
        return items;
    }

    public int getPagesLoaded() {
        return pagesLoaded.size();
    }

    public long getResourcesCount() {
        return items.size() - subAlbumCount - spacerAlbums - advertCount;
    }

    public int getSubAlbumCount() {
        return subAlbumCount;
    }

    public void setSpacerAlbumCount(int spacerAlbumsNeeded) {
        // remove all spacers
        while(items.remove(CategoryItem.BLANK)){};
        spacerAlbums = spacerAlbumsNeeded;
        if(spacerAlbumsNeeded > 0) {
            // add correct number of spacers
            for (int i = 0; i < spacerAlbumsNeeded; i++) {
                items.add(CategoryItem.BLANK);
            }
            // ensure spacers are always placed before images etc.
            Collections.sort(items, itemComparator);
        }
    }

    public ResourceItem getResourceItemById(long selectedItemId) {
        for (GalleryItem item : items) {
            if(item.getId() == selectedItemId) {
                if(item instanceof ResourceItem) {
                    return (ResourceItem) item;
                }
                throw new RuntimeException("Item is present, but is not an album resource, is a " + item.getClass().getName());
            }
        }
        throw new IllegalArgumentException("No resource item present with id : " + selectedItemId);
    }
}
