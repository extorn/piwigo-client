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
    private int subAlbumCount;
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
        subAlbumCount = in.readInt();
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
        dest.writeInt(subAlbumCount);
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
        if(item instanceof CategoryItem) {
            if (item != CategoryItem.ADVERT && item != CategoryItem.ALBUM_HEADING) {
                subAlbumCount++;
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
            int fromIdx = Math.max(0,getItemIdx((T)CategoryItem.ALBUM_HEADING) + 1);
            int toIdx = Math.min(Math.max(0,getItemIdx((T)ResourceItem.PICTURE_HEADING)), getItems().size());
            Collections.reverse(getItems().subList(fromIdx, toIdx));
        }
    }

    @Override
    protected int getPageIndexContaining(int resourceIdx) {
        if(resourceIdx < getSubAlbumCount() + bannerCount) {
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
            if (item == CategoryItem.ALBUM_HEADING || item == GalleryItem.PICTURE_HEADING) {
                bannerCount++;
            }
        }
        return firstInsertPosition;
    }

    @Override
    protected int getItemInsertPosition(T item) {
        if(item instanceof CategoryItem) {
            int itemsToPreceedThisOne = getSubAlbumCount();
            if(itemsToPreceedThisOne > 0) {
                if(comparator.isSortCategoriesInReverseOrder()) {
                    itemsToPreceedThisOne = 0;
                }
                itemsToPreceedThisOne += this.bannerCount > 0 ? 1 : 0;
                return itemsToPreceedThisOne;
            }
            int offset = getResourcesCount() > 0 ? -1 : 0;
            return (this.bannerCount + offset) > 0 ? 1 : 0; // insert at the start if no albums.
        }
        // put it at the start of the resources (regardless of ordering).
        return getItems().size() - getResourcesCount();
    }

    @Override
    public T getItemByIdx(int idx) {
        if(hideAlbums) {
            if(idx == 0) {
                super.getItemByIdx(0);
            } else {
                return super.getItemByIdx(idx + subAlbumCount);
            }
        }
        return super.getItemByIdx(idx);
    }

    @Override
    public int getItemIdx(T item) {
        int itemIdx = super.getItemIdx(item);
        if(hideAlbums && itemIdx >= subAlbumCount) {
            itemIdx -= (subAlbumCount);
        }
        return itemIdx;
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

    @NonNull
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

    private void reduceCountOnRemoval(T item) {
        if (item.isFromServer()) {
            if (item instanceof CategoryItem) {
                subAlbumCount--;
            }
        } else if (CategoryItem.BLANK.equals(item)) {
            spacerAlbums--;
        } else if (CategoryItem.ALBUM_HEADING.equals(item)) {
            bannerCount--;
        } else if (GalleryItem.PICTURE_HEADING.equals(item)) {
            bannerCount--;
        }
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
    public int getItemCount() {
        int itemCount = super.getItemCount();
        if (hideAlbums) {
            itemCount -= subAlbumCount + spacerAlbums + bannerCount;
        }
        return itemCount;
    }

    @Override
    public int getResourcesCount() {
        int resourceCount = super.getItemCount() - subAlbumCount - spacerAlbums - bannerCount;
        if(resourceCount < 0) {
            Logging.log(Log.ERROR, TAG, "PiwigoAlbum Resource count is wrong - %1$d items - %2$d childAlbums - %3$d spacerAlbums - %4$d banners = %5$d", super.getItemCount(), subAlbumCount, spacerAlbums, bannerCount, resourceCount);
            int actualSubAlbumCount = 0;
            int actualSpacerAlbums = 0;
            int actualBannerCount = 0;
            for(T item : getItems()) {
                if(CategoryItem.BLANK.equals(item)) {
                    actualSpacerAlbums++;
                } else if(CategoryItem.ALBUM_HEADING.equals(item)) {
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
                            +"banners (%5$d|%6$d)\n";
                throw new IllegalStateException(String.format(msg, subAlbumCount, actualSubAlbumCount, spacerAlbums, actualSpacerAlbums, bannerCount, actualBannerCount));
            }
            subAlbumCount = actualSubAlbumCount;
            spacerAlbums = actualSpacerAlbums;
            bannerCount = actualBannerCount;

            Logging.log(Log.ERROR, TAG, "Corrected PiwigoAlbum Resource count: - %1$d items - %2$d childAlbums - %3$d spacerAlbums - %4$d banners = %5$d", super.getItemCount(), subAlbumCount, spacerAlbums, bannerCount, resourceCount);
        }
        return resourceCount;
    }

    public int getSubAlbumCount() {
        return subAlbumCount;
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
                        && subAlbumCount > 0) {
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
        int spacerAlbumsNeeded = getSubAlbumCount() % albumsPerRow;
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

}
