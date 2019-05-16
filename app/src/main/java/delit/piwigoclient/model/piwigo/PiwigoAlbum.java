package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import delit.piwigoclient.ui.common.util.ParcelUtils;

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 * <p>
 */
public class PiwigoAlbum extends ResourceContainer<CategoryItem, GalleryItem> implements Parcelable {

    private transient AlbumComparator itemComparator = new AlbumComparator();
    private int subAlbumCount;
    private int spacerAlbums;
    private int bannerCount;
    private boolean hideAlbums;

    public PiwigoAlbum(CategoryItem albumDetails) {
        super(albumDetails, "GalleryItem", (int) (albumDetails.getPhotoCount() + albumDetails.getSubCategories()));
    }
    
    public PiwigoAlbum(Parcel in) {
        super(in);
        subAlbumCount = in.readInt();
        spacerAlbums = in.readInt();
        bannerCount = in.readInt();
        if(itemComparator == null) {
            itemComparator = new AlbumComparator();
        }
        hideAlbums = ParcelUtils.readBool(in);
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
        ParcelUtils.writeBool(dest, hideAlbums);
    }

    @Override
    public int getItemCount() {
        int itemCount = super.getItemCount();
        if (hideAlbums) {
            itemCount -= subAlbumCount + spacerAlbums;
        }
        return itemCount;
    }

    @Override
    public int getDisplayIdx(GalleryItem item) {
        //TODO add offsets for the headers etc?
        return super.getDisplayIdx(item);
    }

    @Override
    public GalleryItem getItemByIdx(int idx) {
        if (hideAlbums) {
            int bannerOffset = (subAlbumCount > 0 ? 1 : 0);
            if (idx > 0) {
                idx += subAlbumCount + spacerAlbums;
            }
        }
        if (isRetrieveItemsInReverseOrder()) {
            // albums should not be reversed as their ordering is static
            int bannerOffset = (subAlbumCount > 0 ? 1 : 0);
            if (idx >= bannerOffset && idx < bannerOffset + subAlbumCount + spacerAlbums) {
                // retrieving a category item - sub album or banner (heading / advert)
                int newIdx = subAlbumCount + spacerAlbums + bannerOffset - idx;
                idx = newIdx;
            }
        }
        return super.getItemByIdx(idx);
    }

    public boolean isHideAlbums() {
        return hideAlbums;
    }

    public void setHideAlbums(boolean hideAlbums) {
        this.hideAlbums = hideAlbums;
    }

    @Override
    public void addItem(GalleryItem item) {
        super.addItem(item);
        if(item == GalleryItem.PICTURE_HEADING) {
            bannerCount++;
        }
        // ensure these are always placed above other resources.
        itemComparator.setSortInReverseOrder(isRetrieveItemsInReverseOrder());
        Collections.sort(getItems(), itemComparator);
//        Log.d("Order", getItems().toString());
    }

    public void addItem(CategoryItem item) {
        if (item != CategoryItem.ADVERT && item != CategoryItem.ALBUM_HEADING) {
            subAlbumCount++;
        } else {
            bannerCount++;
        }
        super.addItem(item);
        // ensure these are always placed first.
        itemComparator.setSortInReverseOrder(isRetrieveItemsInReverseOrder());
        Collections.sort(getItems(), itemComparator);
//        Log.d("Order", getItems().toString());
    }

    public void addItemPage(int page, int pageSize, List<GalleryItem> newItems) {
        super.addItemPage(page, pageSize, newItems);
        for (GalleryItem item : newItems) {
            if (item == CategoryItem.ALBUM_HEADING || item == GalleryItem.PICTURE_HEADING) {
                bannerCount++;
            }
        }
        if (isRetrieveItemsInReverseOrder()) {
            // need to resort the list to bubble the albums back to the end!
            itemComparator.setSortInReverseOrder(isRetrieveItemsInReverseOrder());
            Collections.sort(getItems(), itemComparator);
        }
    }

    protected int getPageInsertPosition(int page, int pageSize) {
        int insertPosition = super.getPageInsertPosition(page, pageSize);

        insertPosition += subAlbumCount;
        insertPosition += spacerAlbums;

        if (!isRetrieveItemsInReverseOrder()) {
            insertPosition += bannerCount;
        } else {
            int bannerOffset = (subAlbumCount > 0 ? 1 : 0);
            insertPosition += bannerOffset; // there will be one or two banners - one for pictures one for albums (ignoring adverts!)
        }
        return insertPosition;
    }

    public void setSpacerAlbumCount(int spacerAlbumsNeeded) {
        // remove all spacers
        ArrayList<GalleryItem> items = getItems();
        while (items.remove(CategoryItem.BLANK)) {
        }
        spacerAlbums = spacerAlbumsNeeded;
        if (spacerAlbumsNeeded > 0) {
            // add correct number of spacers
            long blankId = CategoryItem.BLANK.getId();
            for (int i = 0; i < spacerAlbumsNeeded; i++) {
                items.add(CategoryItem.BLANK.clone().withId(blankId++));
            }
            // ensure spacers are always placed before images etc.
            itemComparator.setSortInReverseOrder(isRetrieveItemsInReverseOrder());
            Collections.sort(items, itemComparator);
        }
    }

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

    private static class AlbumComparator implements Comparator<GalleryItem> {

        private boolean sortInReverseOrder;

        public void setSortInReverseOrder(boolean sortInReverseOrder) {
            this.sortInReverseOrder = sortInReverseOrder;
        }

        @Override
        public int compare(GalleryItem o1, GalleryItem o2) {
            if (sortInReverseOrder) {
                return compareReverseOrdering(o1, o2);
            } else {
                return -compareReverseOrdering(o1, o2);
            }
        }

        private int compareReverseOrdering(GalleryItem o1, GalleryItem o2) {
            boolean firstIsCategory = o1 instanceof CategoryItem;
            boolean secondIsCategory = o2 instanceof CategoryItem;

            if (firstIsCategory && secondIsCategory) {
                if (o1 == CategoryItem.ALBUM_HEADING) {
                    return 1; // push album heading to the end of albums
                } else if (o2 == CategoryItem.ALBUM_HEADING) {
                    return -1; // push album heading to the end of albums
                } else {
                    return 0; // don't change the individual album order (we'll do this once they're all added)
                }
            } else if (!firstIsCategory && !secondIsCategory) {
                if (o1 == GalleryItem.PICTURE_HEADING) {
                    return 1; // push pictures heading to the end of pictures
                } else if (o2 == GalleryItem.PICTURE_HEADING) {
                    return -1; // push pictures heading to the end of pictures
                } else {
                    return 0; // don't change the actual picture ordering
                }
            } else if (firstIsCategory) {
                return 1; // push categories to the very end
            } else {
                return -1; // push categories to the very end
            }
        }

    }

    public boolean addMissingAlbums(List<CategoryItem> adminCategories) {
        return super.addMissingItems(adminCategories);
    }

    public void updateSpacerAlbumCount(int albumsPerRow) {
        int spacerAlbumsNeeded = getSubAlbumCount() % albumsPerRow;
        if (spacerAlbumsNeeded > 0) {
            spacerAlbumsNeeded = albumsPerRow - spacerAlbumsNeeded;
        }
        setSpacerAlbumCount(spacerAlbumsNeeded);
    }

    public GalleryItem remove(int idx) {
        GalleryItem removedItem = super.remove(idx);
        if (removedItem instanceof CategoryItem) {
            if (removedItem == CategoryItem.ADVERT || removedItem == GalleryItem.PICTURE_HEADING || removedItem == CategoryItem.ALBUM_HEADING) {
                bannerCount--;
            } else if (CategoryItem.BLANK.equals(removedItem)) {
                subAlbumCount--;
            }
        } else {
            // It's a resource (no count recorded at the moment).
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
    public static final Parcelable.Creator<PiwigoAlbum> CREATOR
            = new Parcelable.Creator<PiwigoAlbum>() {
        public PiwigoAlbum createFromParcel(Parcel in) {
            return new PiwigoAlbum(in);
        }

        public PiwigoAlbum[] newArray(int size) {
            return new PiwigoAlbum[size];
        }
    };
}
