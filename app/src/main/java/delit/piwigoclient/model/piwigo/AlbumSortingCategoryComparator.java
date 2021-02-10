package delit.piwigoclient.model.piwigo;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.Comparator;

import delit.libs.util.ObjectUtils;

public class AlbumSortingCategoryComparator implements Comparator<GalleryItem>, Serializable {
    private static final long serialVersionUID = -4017716137940864728L;
    private boolean sortInReverseOrder;
    private int albumSortOrder;

    public AlbumSortingCategoryComparator(int albumSortOrder) {
        this.albumSortOrder = albumSortOrder;
    }


    @Override
    public Comparator<GalleryItem> reversed() {
        AlbumSortingCategoryComparator categoryComparator = new AlbumSortingCategoryComparator(albumSortOrder);
        categoryComparator.setSortInReverseOrder(true);
        return categoryComparator;
    }

    public void setSortInReverseOrder(boolean sortInReverseOrder) {
        this.sortInReverseOrder = sortInReverseOrder;
    }

    public int getAlbumSortOrder() {
        return albumSortOrder;
    }

    public void setAlbumSortOrder(int albumSortOrder) {
        this.albumSortOrder = albumSortOrder;
    }

    @Override
    public int compare(GalleryItem o1, GalleryItem o2) {
        return compareFixingCategoryOrder(o1,o2);
    }

    protected int compareFixingCategoryOrder(GalleryItem o1, GalleryItem o2) {
        boolean firstIsCategory = o1 instanceof CategoryItem;
        boolean secondIsCategory = o2 instanceof CategoryItem;
        int autoReverse = sortInReverseOrder ? -1 : 1;
        if (firstIsCategory && secondIsCategory) {
            if (o1 == StaticCategoryItem.ALBUM_HEADING) {
                return -1; // push album heading to the start of albums
            } else if (o2 == StaticCategoryItem.ALBUM_HEADING) {
                return 1; // push album heading to the start of albums
            } else {
                // avoid perpetual sorting of blank items
                boolean sortInList = albumSortOrder == PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT;
                if (StaticCategoryItem.BLANK.equals(o1)) {
                    return StaticCategoryItem.BLANK.equals(o2) ? 0 : sortInList ? -autoReverse : autoReverse; // push the spacers to the end
                } else if (StaticCategoryItem.BLANK.equals(o2)) {
                    return StaticCategoryItem.BLANK.equals(o1) ? 0 : sortInList ? autoReverse : -autoReverse;  // push the spacers to the end
                }
                switch (albumSortOrder) {
                    case PiwigoAlbum.ALBUM_SORT_ORDER_DATE:
                        // internal album ordering is static
                        return autoReverse * ObjectUtils.compare(o1.getLastAltered(), o2.getLastAltered());
                    case PiwigoAlbum.ALBUM_SORT_ORDER_NAME:
                        // internal album ordering is static
                        return autoReverse * o1.getName().compareToIgnoreCase(o2.getName());
                    case PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT:
                        return 0; // do nothing. we have to deal with this separately using the list.
                    default:
                        return autoReverse;
                }
            }
        } else if (firstIsCategory) {
            return -1; // push categories to the start
        } else if (secondIsCategory) {
            return 1; // push categories to the start
        }
        return 0; // don't affect the resource ordering
    }

    public boolean isSortInReverseOrder() {
        return sortInReverseOrder;
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AlbumSortingCategoryComparator{");
        sb.append("sortInReverseOrder=").append(sortInReverseOrder);
        sb.append(", albumSortOrder=").append(albumSortOrder);
        sb.append('}');
        return sb.toString();
    }
}
