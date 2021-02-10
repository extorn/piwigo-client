package delit.piwigoclient.model.piwigo;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.Comparator;

public class AlbumSortingComparator implements Comparator<GalleryItem>, Serializable {

    private static final long serialVersionUID = 4330313592069147720L;
    AlbumSortingCategoryComparator categoryComparator;
    AlbumSortingResourceComparator resourceComparator;

    public AlbumSortingComparator(int albumSortOrder) {
        categoryComparator = new AlbumSortingCategoryComparator(albumSortOrder);
        resourceComparator = new AlbumSortingResourceComparator();
    }

    @Override
    public int compare(GalleryItem o1, GalleryItem o2) {
        int result = categoryComparator.compare(o1,o2);
        if(result == 0 && o1 instanceof ResourceItem) {
            // only compare if one is a resource item to avoid double testing of two categories
            result = resourceComparator.compare(o1,o2);
        }
        return result;
    }

    public void setSortCategoriesInReverseOrder(boolean reverseOrder) {
        categoryComparator.setSortInReverseOrder(reverseOrder);
    }

    public int getAlbumSortOrder() {
        return categoryComparator.getAlbumSortOrder();
    }

    public void setAlbumSortOrder(int albumSortOrder) {
        categoryComparator.setAlbumSortOrder(albumSortOrder);
    }

    public boolean isSortCategoriesInReverseOrder() {
        return categoryComparator.isSortInReverseOrder();
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AlbumSortingComparator{");
        sb.append("categoryComparator=").append(categoryComparator);
        sb.append(", resourceComparator=").append(resourceComparator);
        sb.append('}');
        return sb.toString();
    }
}
