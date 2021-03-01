package delit.piwigoclient.model.piwigo;

import java.io.Serializable;
import java.util.Comparator;

/**
 * sorts resources relative to the categories and headings only.
 * Does not change the internal resource sort order
 */
public class AlbumSortingResourceComparator implements Comparator<GalleryItem>, Serializable {

    private static final long serialVersionUID = -263036600717170733L;
    private boolean flipOrder;
    private String sortOrder;

    @Override
    public int compare(GalleryItem o1, GalleryItem o2) {
        return compareResources(o1,o2);
    }

    public int compareResources(GalleryItem o1, GalleryItem o2) {
        boolean firstIsCategory = o1 instanceof CategoryItem;
        boolean secondIsCategory = o2 instanceof CategoryItem;
        if (!firstIsCategory) {
            // first is not a category
            if (secondIsCategory) {
                // second is a category (move it ahead)
                return 1; // push categories to the start
            } else {
                // Neither are categories
                if (o1 == GalleryItem.PICTURE_HEADING) {
                    // the first should be moved ahead
                    return -1; // push pictures heading to the start of pictures
                } else if (o2 == GalleryItem.PICTURE_HEADING) {
                    // the second should be moved ahead
                    return 1; // push pictures heading to the start of pictures
                } else {
                    return 0; // don't reorder pictures
                }
            }
        } else if (!secondIsCategory) {
            // the first is a category but not the second
            return -1; // push categories to the start
        }
        return 0; // don't reorder if both are categories
    }

    public boolean setFlipResourceOrder(boolean flipOrder) {
        if(this.flipOrder != flipOrder) {
            this.flipOrder = flipOrder;
            return !flipOrder; // return the old value
        }
        return flipOrder; // return the old value
    }

    /**
     * @param sortOrder
     * @return true if changed
     */
    public boolean setSortOrder(String sortOrder) {
        if(this.sortOrder != sortOrder) {
            this.sortOrder = sortOrder;
            return true;
        }
        return false;
    }

    public String getSortOrder() {
        return sortOrder;
    }
}
