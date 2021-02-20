package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
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
            Logging.waitForExceptionToBeSent();
            throw e;
        }
    }

    private void addNonCategoryItem(T item) {
        if (containsItem(item)) {
            remove(item);
        }
        super.addItem(item);
    }

    @Override
    protected void postItemRemove(T item) {
        onContentsAmendment(item, -1);
    }

    @Override
    protected void postItemInsert(T item) {
        if(comparator.getAlbumSortOrder() != ALBUM_SORT_ORDER_DEFAULT) {
            // this will perform a sort
            super.postItemInsert(item);
        }
        onContentsAmendment(item, +1);
    }

    private void onContentsAmendment(T item, int change) {
        if (item.isFromServer()) {
            if (item instanceof CategoryItem) {
                childAlbumCount += change;
            }
        } else if (StaticCategoryItem.BLANK.equals(item)) {
            spacerAlbums += change;
        } else if (StaticCategoryItem.ALBUM_HEADING.equals(item)) {
            bannerCount += change;
        } else if (GalleryItem.PICTURE_HEADING.equals(item)) {
            bannerCount += change;
        }
    }


    @Override
    protected void sortItems(List<T> items) {
        Collections.sort(items, comparator);
        if(albumsNeedReversing) {
            // reverses just the albums.
            albumsNeedReversing = false;
            reverseTheAlbumOrder();
        }
    }

    private void reverseTheAlbumOrder() {
        int fromIdx = Math.max(0,getItemIdx((T)StaticCategoryItem.ALBUM_HEADING) + 1);
        int toIdx = fromIdx + childAlbumCount;
        Collections.reverse(getItems().subList(fromIdx, toIdx));
    }

    @Override
    protected int getPageIndexContaining(int resourceIdx) {
        int nonPageLoadedItems = nonResourceItemsIncResourcesHeader();
        if(resourceIdx < nonPageLoadedItems) {
            return NOT_TRACKED_PAGE_ID; // only the resource items are paged
        }
        return super.getPageIndexContaining(resourceIdx - nonPageLoadedItems);
    }

    private void addCategoryItem(CategoryItem item) {
        remove((T) item);
        super.addItem((T) item);
    }

    @Override
    protected void postPageInsert(ArrayList<T> sortedItems, List<T> newItems) {
        super.postPageInsert(sortedItems, newItems);
        for (GalleryItem item : newItems) {
            if (item == StaticCategoryItem.ALBUM_HEADING || item == GalleryItem.PICTURE_HEADING) {
                bannerCount++;
            }
        }
    }

    @Override
    public int getFirstResourceIdx() {
        if(getItemCount() == 0) {
            return -1;
        }
        int resourceIdx = nonResourceItemsIncResourcesHeader();
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
        int wantedIdx = idx;
        try {
            if (hideAlbums) {
                if (idx > 0) {
                    wantedIdx += nonResourceItemsExcResourcesHeader();
                    if(idx < getBannerCount()) {
                        wantedIdx -= 1;
                    }
                }
            }
            return super.getItemByIdx(wantedIdx);
        } catch(IndexOutOfBoundsException e) {
            Logging.log(Log.ERROR, TAG, toString()+" - idx requested : "+ idx + " translated to : " + wantedIdx);
            throw e;
        }
    }

    private int nonResourceItemsExcResourcesHeader() {
        int nonResourceItems = nonResourceItemsIncResourcesHeader();
        if(bannerCount > (childAlbumCount == 0 ? 0 : 1)) {
            nonResourceItems-=1; // we want to show a resource banner
        }
        return nonResourceItems;
    }

    private int nonResourceItemsIncResourcesHeader() {
        return spacerAlbums + childAlbumCount + bannerCount;
    }

    @Override
    public int getItemIdx(T item) {
        int itemIdx = super.getItemIdx(item);
        int nonResourceItems = nonResourceItemsExcResourcesHeader();
        if(hideAlbums && itemIdx >= nonResourceItems) {
            itemIdx -= nonResourceItems;
        }
        return itemIdx;
    }

    @Override
    protected int getPageInsertPosition(int page, int pageSize) {
        int insertAtIdx = super.getPageInsertPosition(page, pageSize);
        // ensure the pages of resources are placed after the albums
        int nonServerResourceItems = nonResourceItemsIncResourcesHeader();
        insertAtIdx += nonServerResourceItems;
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
        sb.append(", super=").append(super.toString());
        sb.append('}');
        return sb.toString();
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
        boolean changed = false;
        while(spacerAlbums > spacerAlbumsNeeded) {
            changed = removeByEquality((T)StaticCategoryItem.BLANK);
        }
        if(changed) {
            Logging.log(Log.DEBUG, TAG, "Spacer album count corrected by remove");
            changed = false;
        }
        long blankId = StaticCategoryItem.BLANK.getId() + spacerAlbums; // ensure we don't add a duplicate ID
        int insertAtIdx = 1 + childAlbumCount + spacerAlbums;
        while(spacerAlbums < spacerAlbumsNeeded) {
            changed = true;
            addItem(insertAtIdx, (T)StaticCategoryItem.BLANK.toInstance(blankId++));
        }
        if(changed) {
            Logging.log(Log.DEBUG, TAG, "Spacer album count corrected by add");
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
            itemCount -= nonResourceItemsExcResourcesHeader();
            if(childAlbumCount > 0) {
                itemCount+=1; // we always show the album header even when hiding albums
            }
        }
        return itemCount;
    }

    @Override
    public int getResourcesCount() {
        int serverResourceCount = super.getItemCount() - nonResourceItemsIncResourcesHeader();
        if(serverResourceCount < 0) {
            Logging.log(Log.ERROR, TAG, "PiwigoAlbum Resource count is wrong - %1$d items - %2$d childAlbums - %3$d spacerAlbums - %4$d banners = %5$d", super.getItemCount(), childAlbumCount, spacerAlbums, bannerCount, serverResourceCount);
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
                } else if(GalleryItem.PICTURE_HEADING.equals(item)) {
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

            Logging.log(Log.ERROR, TAG, "Corrected PiwigoAlbum Resource count: - %1$d items - %2$d childAlbums - %3$d spacerAlbums - %4$d banners = %5$d", super.getItemCount(), childAlbumCount, spacerAlbums, bannerCount, serverResourceCount);
        }
        return serverResourceCount;
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
        return super.remove(idx);
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


    protected int getSpacerAlbumCount() {
        return spacerAlbums;
    }
}
