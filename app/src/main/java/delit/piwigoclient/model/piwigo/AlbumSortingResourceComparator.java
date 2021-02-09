package delit.piwigoclient.model.piwigo;

import java.io.Serializable;
import java.util.Comparator;

/**
 * sorts resources relative to the categories and headings only.
 * Does not change the internal resource sort order
 */
public class AlbumSortingResourceComparator implements Comparator<GalleryItem>, Serializable {

    private static final long serialVersionUID = -263036600717170733L;

    @Override
    public int compare(GalleryItem o1, GalleryItem o2) {
        return compareResources(o1,o2);
    }

    public int compareResources(GalleryItem o1, GalleryItem o2) {
        boolean firstIsCategory = o1 instanceof CategoryItem;
        boolean secondIsCategory = o2 instanceof CategoryItem;
        if (!firstIsCategory && !secondIsCategory) {
            if (o1 == GalleryItem.PICTURE_HEADING) {
                return -1; // push pictures heading to the start of pictures
            } else if (o2 == GalleryItem.PICTURE_HEADING) {
                return 1; // push pictures heading to the start of pictures
            } else {
                return 0; // don't reorder pictures
            }
        } else if (firstIsCategory && !secondIsCategory) {
            return -1; // push categories to the start
        } else if (!firstIsCategory && secondIsCategory) {
            return 1; // push categories to the start
        }
        return 0; // don't reorder resources
    }
}
