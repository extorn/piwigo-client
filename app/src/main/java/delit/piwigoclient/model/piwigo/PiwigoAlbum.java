package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.BuildConfig;

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 * <p>
 */
public class PiwigoAlbum<S extends CategoryItem, T extends GalleryItem> extends ResourceContainer<S, T> implements Parcelable {

    public static final int ALBUM_SORT_ORDER_DEFAULT = 0;
    public static final int ALBUM_SORT_ORDER_NAME = 1;
    public static final int ALBUM_SORT_ORDER_DATE = 2;
    public static final Creator<PiwigoAlbum<?,?>> CREATOR
            = new Creator<PiwigoAlbum<?,?>>() {
        public PiwigoAlbum<?,?> createFromParcel(Parcel in) {
            return new PiwigoAlbum<>(in);
        }

        public PiwigoAlbum<?,?>[] newArray(int size) {
            return new PiwigoAlbum<?,?>[size];
        }
    };
    private static final String TAG = "PwgAlb";
    private final AlbumSortingComparator comparator;
    private int childAlbumCount;
    private int spacerAlbums;
    private int bannerCount;
    private boolean hideAlbums;
    private boolean retrieveAlbumsInReverseOrder;
    private boolean albumsNeedReversing;

    public PiwigoAlbum(S albumDetails) {
        this(albumDetails, ALBUM_SORT_ORDER_DEFAULT);
    }
    
    public PiwigoAlbum(S albumDetails, int albumSortOrder) {
        super(albumDetails, "GalleryItem", (int) (albumDetails.getPhotoCount() + albumDetails.getSubCategories()));
        comparator = new AlbumSortingComparator(albumSortOrder);
    }
    
    public PiwigoAlbum(Parcel in) {
        super(in);
        childAlbumCount = in.readInt();
        spacerAlbums = in.readInt();
        bannerCount = in.readInt();
        comparator = new AlbumSortingComparator(in.readInt());
        hideAlbums = ParcelUtils.readBool(in);
        retrieveAlbumsInReverseOrder = ParcelUtils.readBool(in);
        comparator.setSortCategoriesInReverseOrder(retrieveAlbumsInReverseOrder);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(childAlbumCount);
        dest.writeInt(spacerAlbums);
        dest.writeInt(bannerCount);
        dest.writeInt(comparator.getAlbumSortOrder());
        ParcelUtils.writeBool(dest, hideAlbums);
        ParcelUtils.writeBool(dest, retrieveAlbumsInReverseOrder);
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
        try {
            if (item instanceof CategoryItem) {
                addCategoryItem((CategoryItem) item);
                return;
            }
            addNonCategoryItem(item);
        } catch(RuntimeException e) {
            // the purpose of this is to ensure we can debug any issue.
            Logging.log(Log.ERROR, TAG, toString()+" - itemToAdd : " + item);
            Logging.recordException(e);
            throw e;
        }
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
        if(item instanceof CategoryItem) {
            if (item != StaticCategoryItem.ADVERT && item != StaticCategoryItem.ALBUM_HEADING) {
                if(item.equals(StaticCategoryItem.BLANK)) {
                    spacerAlbums++;
                } else {
                    childAlbumCount++;
                }
            } else {
                bannerCount++;
            }
        }
    }

    @Override
    protected void sortItems(List<T> items) {
        Collections.sort(items, comparator);
        if(albumsNeedReversing) {
            albumsNeedReversing = false;
            int fromIdx = Math.max(0,getItemIdx((T)StaticCategoryItem.ALBUM_HEADING) + 1);
            int toIdx = Math.min(Math.max(0,getFirstResourceIdx() - 1), getItems().size());
            Collections.reverse(getItems().subList(fromIdx, toIdx));
        }
    }

    @Override
    protected int getPageIndexContaining(int resourceIdx) {
        if(resourceIdx < childAlbumCount + spacerAlbums + bannerCount) {
            return -1; // only the resource items are paged
        }
        return super.getPageIndexContaining(resourceIdx);
    }

    private void addCategoryItem(CategoryItem item) {
        remove((T) item);
        super.addItem((T) item);
    }

    @Override
    public int addItemPage(int page, int pageSize, List<T> newItems) {
        int firstInsertPosition = super.addItemPage(page, pageSize, newItems);
        for (GalleryItem item : newItems) {
            if (item == StaticCategoryItem.ALBUM_HEADING || item == GalleryItem.PICTURE_HEADING) {
                bannerCount++;
            }
        }
        return firstInsertPosition;
    }

    @Override
    public int getFirstResourceIdx() {
        if(hideAlbums) {
            return spacerAlbums + childAlbumCount + (bannerCount > 0 ? bannerCount - 1 : 0);
        }
        if(getItemCount() == 0) {
            return -1;
        }
        int resourceIdx = spacerAlbums + childAlbumCount + bannerCount;
        if(resourceIdx >= getItemCount()) {
            // this will occur in case of error, but might be just because there are other items in the list such as categories etc.
            Logging.log(Log.DEBUG, TAG, "First Resource idx %1$d beyond end of list %2$d. Resetting to -1", resourceIdx, getItemCount());
            resourceIdx = -1;
        }
        return resourceIdx;
    }

    @Override
    protected int getItemInsertPosition(T item) {
        if(item instanceof CategoryItem) {
            int itemsToPrecedeThisOne = childAlbumCount;
            if(itemsToPrecedeThisOne > 0) {
                if(comparator.isSortCategoriesInReverseOrder()) {
                    itemsToPrecedeThisOne = 0;
                }
                itemsToPrecedeThisOne += this.bannerCount > 0 ? 1 : 0;
                return itemsToPrecedeThisOne;
            }
            int offset = getResourcesCount() > 0 ? -1 : 0;
            return (this.bannerCount + offset) > 0 ? 1 : 0; // insert at the start if no albums.
        }
        // put it at the start of the resources (regardless of ordering).
        return getItems().size() - getResourcesCount();
    }

    @Override
    public T getItemByIdx(int idx) {
        try {
            if (hideAlbums) {
                if (idx == 0) {
                    super.getItemByIdx(0);
                } else {
                    return super.getItemByIdx(idx + spacerAlbums + childAlbumCount);
                }
            }
            return super.getItemByIdx(idx);
        } catch(IndexOutOfBoundsException e) {
            Logging.log(Log.ERROR, TAG, toString()+" - idx requested : "+ idx);
            throw e;
        }
    }

    @Override
    public int getItemIdx(T item) {
        int itemIdx = super.getItemIdx(item);
        if(hideAlbums && itemIdx >= (spacerAlbums + childAlbumCount)) {
            itemIdx -= (spacerAlbums + childAlbumCount);
        }
        return itemIdx;
    }

    @Override
    protected int getPageInsertPosition(int page, int pageSize) {
        int insertAtIdx = super.getPageInsertPosition(page, pageSize);
        // ensure the pages of resources are placed after the albums
        insertAtIdx += childAlbumCount;
        insertAtIdx += spacerAlbums;
        insertAtIdx += bannerCount;
        return insertAtIdx;
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PiwigoAlbum{");
        sb.append("albumDetail=").append(getContainerDetails());
        sb.append("comparator=").append(comparator);
        sb.append(", childAlbumCount=").append(childAlbumCount);
        sb.append(", childSpacerAlbums=").append(spacerAlbums);
        sb.append(", bannerCount=").append(bannerCount);
        sb.append(", hideAlbums=").append(hideAlbums);
        sb.append(", itemType=").append(getItemType());
        sb.append(", reverseOrder=").append(isRetrieveAlbumsInReverseOrder());
        sb.append('}');
        return sb.toString();
    }

    private void reduceCountOnRemoval(T item) {
        if (item.isFromServer()) {
            if (item instanceof CategoryItem) {
                childAlbumCount--;
            }
        } else if (StaticCategoryItem.BLANK.equals(item)) {
            spacerAlbums--;
        } else if (StaticCategoryItem.ALBUM_HEADING.equals(item)) {
            bannerCount--;
        } else if (GalleryItem.PICTURE_HEADING.equals(item)) {
            bannerCount--;
        }
    }

    /**
     *
     * @param spacerAlbumsNeeded
     * @return true if the list contents was changed
     */
    public boolean setSpacerAlbumCount(int spacerAlbumsNeeded) {
        // remove all spacers
        if(spacerAlbums == spacerAlbumsNeeded) {
            return false;
        }
        boolean removed = removeAllByEquality(Collections.singletonList((T)StaticCategoryItem.BLANK));
        if(removed) {
            Logging.log(Log.DEBUG, TAG, "removing spacer album");
        }
        if (spacerAlbumsNeeded > 0) {
            // add correct number of spacers
            long blankId = StaticCategoryItem.BLANK.getId();
            for (int i = 0; i < spacerAlbumsNeeded; i++) {
                addItem((T)StaticCategoryItem.BLANK.toInstance(blankId++));
            }
        }
        return true;
    }

    @Override
    public void clear() {
        if(getItemCount() == 0) {
            Logging.log(Log.WARN, TAG, "Pointlessly clearing album content");
        } else {
            Logging.log(Log.DEBUG, TAG, "Clearing album content");
        }
        super.clear();
        childAlbumCount = 0;
        spacerAlbums = 0;
        bannerCount = 0;
    }

    @Override
    public int getItemCount() {
        int itemCount = super.getItemCount();
        if (hideAlbums) {
            itemCount -= childAlbumCount + spacerAlbums + bannerCount;
        }
        return itemCount;
    }

    @Override
    public int getResourcesCount() {
        int resourceCount = super.getItemCount() - childAlbumCount - spacerAlbums - bannerCount;
        if(resourceCount < 0) {
            Logging.log(Log.ERROR, TAG, "PiwigoAlbum Resource count is wrong - %1$d items - %2$d childAlbums - %3$d spacerAlbums - %4$d banners = %5$d", super.getItemCount(), childAlbumCount, spacerAlbums, bannerCount, resourceCount);
            int actualSubAlbumCount = 0;
            int actualSpacerAlbums = 0;
            int actualBannerCount = 0;
            for(T item : getItems()) {
                if(StaticCategoryItem.BLANK.equals(item)) {
                    actualSpacerAlbums++;
                } else if(StaticCategoryItem.ALBUM_HEADING.equals(item)) {
                    actualBannerCount++;
                } else if(item instanceof CategoryItem) {
                    actualSubAlbumCount++;
                } else if(ResourceItem.PICTURE_HEADING.equals(item)) {
                    actualBannerCount++;
                }
            }
            if(BuildConfig.DEBUG) {
                String msg = "subAlbums (%1$d|%2$d)\n"
                            +"spacers (%3$d|%4$d)\n"
                            +"banners (%5$d|%6$d)\n"
                            +"total (%7$d)";
                throw new IllegalStateException(String.format(msg, childAlbumCount, actualSubAlbumCount, spacerAlbums, actualSpacerAlbums, bannerCount, actualBannerCount, super.getItemCount()));
            }
            childAlbumCount = actualSubAlbumCount;
            spacerAlbums = actualSpacerAlbums;
            bannerCount = actualBannerCount;

            Logging.log(Log.ERROR, TAG, "Corrected PiwigoAlbum Resource count: - %1$d items - %2$d childAlbums - %3$d spacerAlbums - %4$d banners = %5$d", super.getItemCount(), childAlbumCount, spacerAlbums, bannerCount, resourceCount);
        }
        return resourceCount;
    }

    public int getChildAlbumCount() {
        return childAlbumCount;
    }

    public boolean isRetrieveAlbumsInReverseOrder() {
        return retrieveAlbumsInReverseOrder;
    }

    public boolean setRetrieveChildAlbumsInReverseOrder(boolean retrieveAlbumsInReverseOrder) {
        if(this.retrieveAlbumsInReverseOrder != retrieveAlbumsInReverseOrder) {
            comparator.setSortCategoriesInReverseOrder(retrieveAlbumsInReverseOrder);
            this.retrieveAlbumsInReverseOrder = retrieveAlbumsInReverseOrder;
            if (getItems().size() > 0) {
                if(comparator.getAlbumSortOrder() == PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT
                        && childAlbumCount > 0) {
                    albumsNeedReversing = true;
                }
                sortItems();
                return true;
            }
        }
        return false;
    }

    public boolean addMissingAlbums(List<CategoryItem> adminCategories) {
        boolean changed =  super.addMissingItems((List<? extends T>) adminCategories);
        return changed;
    }

    public void updateSpacerAlbumCount(int albumsPerRow) {
        int spacerAlbumsNeeded = childAlbumCount % albumsPerRow;
        if (spacerAlbumsNeeded > 0) {
            spacerAlbumsNeeded = albumsPerRow - spacerAlbumsNeeded;
        }
        setSpacerAlbumCount(spacerAlbumsNeeded);
    }

    @Override
    public T remove(int idx) {
        T removedItem = super.remove(idx);
        if (removedItem != null) {
            reduceCountOnRemoval(removedItem);
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

    public int getBannerCount() {
        return bannerCount;
    }


}
