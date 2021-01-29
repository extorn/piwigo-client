package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.util.ObjectUtils;

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 * <p>
 */
public class PiwigoAlbum<S extends CategoryItem, T extends GalleryItem> extends ResourceContainer<S, T> implements Parcelable {

    private static final String TAG = "PwgAlb";
    public static final int ALBUM_SORT_ORDER_DEFAULT = 0;
    public static final int ALBUM_SORT_ORDER_NAME = 1;
    public static final int ALBUM_SORT_ORDER_DATE = 2;
    private final FullComparator comparator;
    private int subAlbumCount;
    private int spacerAlbums;
    private int bannerCount;
    private boolean hideAlbums;
    private boolean retrieveAlbumsInReverseOrder;

    public PiwigoAlbum(S albumDetails) {
        this(albumDetails, ALBUM_SORT_ORDER_DEFAULT);
    }

    public PiwigoAlbum(S albumDetails, int albumSortOrder) {
        super(albumDetails, "GalleryItem", (int) (albumDetails.getPhotoCount() + albumDetails.getSubCategories()));
        comparator = new FullComparator(albumSortOrder);
    }
    
    public PiwigoAlbum(Parcel in) {
        super(in);
        subAlbumCount = in.readInt();
        spacerAlbums = in.readInt();
        bannerCount = in.readInt();
        comparator = new FullComparator(in.readInt());
        comparator.setSortInReverseOrder(isRetrieveAlbumsInReverseOrder());
        hideAlbums = ParcelUtils.readBool(in);
        retrieveAlbumsInReverseOrder = ParcelUtils.readBool(in);
    }
    
    @Override
    public int describeContents() {
        return 0;
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(subAlbumCount);
        dest.writeInt(spacerAlbums);
        dest.writeInt(bannerCount);
        dest.writeInt(comparator.getAlbumSortOrder());
        ParcelUtils.writeBool(dest, hideAlbums);
        ParcelUtils.writeBool(dest, retrieveAlbumsInReverseOrder);
    }

    @Override
    public int getItemCount() {
        int itemCount = super.getItemCount();
        if (hideAlbums) {
            itemCount -= subAlbumCount + spacerAlbums;
        }
        return itemCount;
    }

    public boolean setAlbumSortOrder(int albumSortOrder) {
        if(comparator.getAlbumSortOrder() != albumSortOrder) {
            if(getItems().size() > 0) {
                throw new IllegalStateException("Unable to change sort order once items are present");
                //TODO perhaps replicate server sorting unless swapping to default.
            }
            comparator.setAlbumSortOrder(albumSortOrder);
            return true;
        }
        return false;
    }

    public boolean isHideAlbums() {
        return hideAlbums;
    }

    public void setHideAlbums(boolean hideAlbums) {
        this.hideAlbums = hideAlbums;
    }

    @Override
    public void addItem(T item) {
        if(item instanceof CategoryItem) {
            addCategoryItem((CategoryItem) item);
            return;
        }
        addNonCategoryItem(item);
    }

    private void addNonCategoryItem(T item) {
        boolean replaced = false;
        if (containsItem(item)) {
            remove(item);
            replaced = true;
        }
        super.addItem(item);
        if (item == GalleryItem.PICTURE_HEADING && !replaced) {
            bannerCount++;
        }
    }

    @Override
    protected void postItemInsert(T item) {
        if(comparator.getAlbumSortOrder() != ALBUM_SORT_ORDER_DEFAULT) {
            super.postItemInsert(item);
        }
    }

    @Override
    protected void sortItems(List<T> items) {
        Collections.sort(items, comparator);
    }

    @Override
    protected int getPageIndexContaining(int resourceIdx) {
        if(resourceIdx < getSubAlbumCount() + bannerCount) {
            return -1; // only the resource items are paged
        }
        return super.getPageIndexContaining(resourceIdx);
    }

    private void addCategoryItem(CategoryItem item) {
        boolean updateCounts = false;
        if (containsItem((T) item)) {
            remove((T) item);
        } else {
            updateCounts = true;
        }
        super.addItem((T) item);
        if(updateCounts) {
            if (item != CategoryItem.ADVERT && item != CategoryItem.ALBUM_HEADING) {
                subAlbumCount++;
            } else {
                bannerCount++;
            }
        }
    }

    @Override
    public int addItemPage(int page, int pageSize, List<T> newItems) {
        int firstInsertPosition = super.addItemPage(page, pageSize, newItems);
        for (GalleryItem item : newItems) {
            if (item == CategoryItem.ALBUM_HEADING || item == GalleryItem.PICTURE_HEADING) {
                bannerCount++;
            }
        }
        return firstInsertPosition;
    }

    @Override
    protected int getItemInsertPosition(T item) {
        if(item instanceof CategoryItem) {
            int itemsToPreceedThisOne =  getSubAlbumCount();
            if(itemsToPreceedThisOne > 0) {
                if(isRetrieveItemsInReverseOrder()) {
                    itemsToPreceedThisOne = 0;
                }
                itemsToPreceedThisOne += this.bannerCount > 0 ? 1 : 0;
                return itemsToPreceedThisOne;
            }
            int offset = getResourcesCount() > 0 ? -1 : 0;
            return (this.bannerCount + offset) > 0 ? 1 : 0; // insert at the start if no albums.
        }
        // put it at the end of the resources.
        return super.getItemInsertPosition(item);
    }

    @Override
    protected int getPageInsertPosition(int page, int pageSize) {
        int insertAtIdx = super.getPageInsertPosition(page, pageSize);
        // ensure the pages of resources are placed after the albums
        insertAtIdx += subAlbumCount;
        insertAtIdx += spacerAlbums;
        insertAtIdx += bannerCount;
        return insertAtIdx;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PiwigoAlbum{");
        sb.append("comparator=").append(comparator);
        sb.append(", subAlbumCount=").append(subAlbumCount);
        sb.append(", spacerAlbums=").append(spacerAlbums);
        sb.append(", bannerCount=").append(bannerCount);
        sb.append(", hideAlbums=").append(hideAlbums);
        sb.append('}');
        return sb.toString();
    }

    public void setSpacerAlbumCount(int spacerAlbumsNeeded) {
        // remove all spacers
        boolean removed = removeAll(Collections.singletonList((T)CategoryItem.BLANK));
        if(removed) {
            Logging.log(Log.DEBUG, TAG, "removing spacer album");
        }
        spacerAlbums = spacerAlbumsNeeded;
        if (spacerAlbumsNeeded > 0) {
            // add correct number of spacers
            long blankId = CategoryItem.BLANK.getId();
            for (int i = 0; i < spacerAlbumsNeeded; i++) {
                addItem((T)CategoryItem.BLANK.clone().withId(blankId++));
            }
        }
    }

    @Override
    public void clear() {
        super.clear();
        subAlbumCount = 0;
        spacerAlbums = 0;
        bannerCount = 0;
    }

    @Override
    public int getResourcesCount() {
        return super.getItemCount() - subAlbumCount - spacerAlbums - bannerCount;
    }

    public int getSubAlbumCount() {
        return subAlbumCount;
    }

    public boolean isRetrieveAlbumsInReverseOrder() {
        return retrieveAlbumsInReverseOrder;
    }

    public boolean setRetrieveChildAlbumsInReverseOrder(boolean retrieveAlbumsInReverseOrder) {
        if(this.retrieveAlbumsInReverseOrder != retrieveAlbumsInReverseOrder) {
            comparator.setSortInReverseOrder(retrieveAlbumsInReverseOrder);
            this.retrieveAlbumsInReverseOrder = retrieveAlbumsInReverseOrder;
            return true;
        }
        return false;
    }

    public static class FullComparator implements Comparator<GalleryItem>, Serializable {

        private static final long serialVersionUID = 4330313592069147720L;
        CategoryComparator categoryComparator;
        ResourceComparator resourceComparator;

        public FullComparator(int albumSortOrder) {
            categoryComparator = new CategoryComparator(albumSortOrder);
            resourceComparator = new ResourceComparator();
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

        private void setSortInReverseOrder(boolean reverseOrder) {
            categoryComparator.setSortInReverseOrder(reverseOrder);
        }

        public int getAlbumSortOrder() {
            return categoryComparator.getAlbumSortOrder();
        }

        public void setAlbumSortOrder(int albumSortOrder) {
            categoryComparator.setAlbumSortOrder(albumSortOrder);
        }
    }

    private static class CategoryComparator implements Comparator<GalleryItem>, Serializable {
        private static final long serialVersionUID = -4017716137940864728L;
        private boolean sortInReverseOrder;
        private int albumSortOrder;

        public CategoryComparator(int albumSortOrder) {
            this.albumSortOrder = albumSortOrder;
        }


        @Override
        public Comparator<GalleryItem> reversed() {
            CategoryComparator categoryComparator = new CategoryComparator(albumSortOrder);
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
                if (o1 == CategoryItem.ALBUM_HEADING) {
                    return -1; // push album heading to the start of albums
                } else if (o2 == CategoryItem.ALBUM_HEADING) {
                    return 1; // push album heading to the start of albums
                } else {
                    if (CategoryItem.BLANK.equals(o1)) {
                        if (!CategoryItem.BLANK.equals(o2)) {
                            return -1;
                        } else {
                            return 0; // avoid perpetual sorting of blank items
                        }
                    }
                    switch (albumSortOrder) {
                        case ALBUM_SORT_ORDER_DATE:
                            // internal album ordering is static
                            return autoReverse * ObjectUtils.compare(o1.getLastAltered(), o2.getLastAltered());
                        case ALBUM_SORT_ORDER_NAME:
                            // internal album ordering is static
                            return autoReverse * o1.getName().compareToIgnoreCase(o2.getName());
                        case ALBUM_SORT_ORDER_DEFAULT:
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
    }

    /**
     * sorts resources relative to the categories and headings only.
     * Does not change the internal resource sort order
     */
    private static class ResourceComparator implements Comparator<GalleryItem>, Serializable {

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

    public boolean addMissingAlbums(List<CategoryItem> adminCategories) {
        boolean changed =  super.addMissingItems((List<? extends T>) adminCategories);
        return changed;
    }

    public void updateSpacerAlbumCount(int albumsPerRow) {
        int spacerAlbumsNeeded = getSubAlbumCount() % albumsPerRow;
        if (spacerAlbumsNeeded > 0) {
            spacerAlbumsNeeded = albumsPerRow - spacerAlbumsNeeded;
        }
        setSpacerAlbumCount(spacerAlbumsNeeded);
    }

    public T remove(int idx) {
        T removedItem = super.remove(idx);
        if (removedItem instanceof CategoryItem) {
            if (removedItem == CategoryItem.ADVERT || removedItem == GalleryItem.PICTURE_HEADING || removedItem == CategoryItem.ALBUM_HEADING) {
                bannerCount--;
            } else if (CategoryItem.BLANK.equals(removedItem)) {
                subAlbumCount--;
            }
        }
        return removedItem;
    }

    @Override
    public int getImgResourceCount() {
        return getContainerDetails().getPhotoCount();
    }

    public CategoryItem getSubAlbumByRepresentativeImageId(long representativePictureId) {
        for (GalleryItem item : this.getItems()) {
            if (item instanceof CategoryItem) {
                Long albumRepresentativePictureId = ((CategoryItem) item).getRepresentativePictureId();
                if (albumRepresentativePictureId != null && representativePictureId == albumRepresentativePictureId) {
                    return (CategoryItem) item;
                }
            }
        }
        return null;
    }

    public static final Creator<PiwigoAlbum<?,?>> CREATOR
            = new Creator<PiwigoAlbum<?,?>>() {
        public PiwigoAlbum<?,?> createFromParcel(Parcel in) {
            return new PiwigoAlbum<>(in);
        }

        public PiwigoAlbum<?,?>[] newArray(int size) {
            return new PiwigoAlbum<?,?>[size];
        }
    };
}
