package delit.piwigoclient.model.piwigo;

import android.util.Log;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 * <p>
 */
public class PiwigoAlbum extends ResourceContainer<CategoryItem, GalleryItem> implements Serializable {

    private static final long serialVersionUID = 919642836918692590L;
    private transient Comparator<GalleryItem> itemComparator = new AlbumComparator();
    private int subAlbumCount;
    private int spacerAlbums;
    private int bannerCount;

    private static class AlbumComparator implements Comparator<GalleryItem> {
        @Override
        public int compare(GalleryItem o1, GalleryItem o2) {
            boolean firstIsCategory = o1 instanceof CategoryItem;
            boolean secondIsCategory = o2 instanceof CategoryItem;
            if (firstIsCategory && secondIsCategory) {
                if (o1 == CategoryItem.ALBUM_HEADING) {
                    return -1;
                } else if (o2 == CategoryItem.ALBUM_HEADING) {
                    return 1;
                } else {
                    return 0;
                }
            } else if (!firstIsCategory && !secondIsCategory) {
                if (o1 == GalleryItem.PICTURE_HEADING) {
                    return -1;
                } else if (o2 == GalleryItem.PICTURE_HEADING) {
                    return 1;
                } else {
                    return 0;
                }
            } else if (firstIsCategory) {
                return -1;
            } else {
                return 1;
            }
        }
    };

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        itemComparator = new AlbumComparator();
    }

    public PiwigoAlbum(CategoryItem albumDetails) {
        super(albumDetails, "GalleryItem", (int) (albumDetails.getPhotoCount() + albumDetails.getSubCategories()));
    }

    @Override
    public void addItem(GalleryItem item) {
        super.addItem(item);
        if(item == GalleryItem.PICTURE_HEADING) {
            bannerCount++;
        }
        // ensure these are always placed above other resources.
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
        Collections.sort(getItems(), itemComparator);
//        Log.d("Order", getItems().toString());
    }

    protected int getPageInsertPosition(int page, int pageSize) {
        int insertPosition = super.getPageInsertPosition(page, pageSize);
        insertPosition += subAlbumCount;
        insertPosition += spacerAlbums;
        insertPosition += bannerCount;
        return insertPosition;
    }

    public void addItemPage(int page, int pageSize, List<GalleryItem> newItems) {
        super.addItemPage(page, pageSize, newItems);
        for (GalleryItem item : newItems) {
            if (item == CategoryItem.ALBUM_HEADING || item == GalleryItem.PICTURE_HEADING) {
                bannerCount++;
            }
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

    public void setSpacerAlbumCount(int spacerAlbumsNeeded) {
        // remove all spacers
        ArrayList<GalleryItem> items = getItems();
        while (items.remove(CategoryItem.BLANK)) {
        }
        spacerAlbums = spacerAlbumsNeeded;
        if (spacerAlbumsNeeded > 0) {
            // add correct number of spacers
            for (int i = 0; i < spacerAlbumsNeeded; i++) {
                items.add(CategoryItem.BLANK);
            }
            // ensure spacers are always placed before images etc.
            Collections.sort(items, itemComparator);
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
            } else if (removedItem == CategoryItem.BLANK) {
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
}
