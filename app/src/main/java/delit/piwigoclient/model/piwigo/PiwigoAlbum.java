package delit.piwigoclient.model.piwigo;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 * <p>
 */
public class PiwigoAlbum extends ResourceContainer<CategoryItem, GalleryItem> implements Serializable {

    private transient Comparator<GalleryItem> itemComparator = new AlbumComparator();
    private int subAlbumCount;
    private int spacerAlbums;
    private int advertCount;

    private static class AlbumComparator implements Comparator<GalleryItem> {
        @Override
        public int compare(GalleryItem o1, GalleryItem o2) {
            boolean firstIsCategory = o1 instanceof CategoryItem;
            boolean secondIsCategory = o2 instanceof CategoryItem;
            if (firstIsCategory && secondIsCategory) {
                if (o1 == CategoryItem.ADVERT) {
                    return -1;
                } else if (o2 == CategoryItem.ADVERT) {
                    return 1;
                } else {
                    return 0;
                }
            } else if (!firstIsCategory && !secondIsCategory) {
                if (o1 == GalleryItem.ADVERT) {
                    return -1;
                } else if (o2 == GalleryItem.ADVERT) {
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

    public void addItem(CategoryItem item) {
        if (item != CategoryItem.ADVERT) {
            subAlbumCount++;
        } else {
            advertCount++;
        }
        super.addItem(item);
        // ensure these are always placed first.
        Collections.sort(getItems(), itemComparator);
    }

    protected int getPageInsertPosition(int page, int pageSize) {
        int insertPosition = super.getPageInsertPosition(page, pageSize);
        insertPosition += subAlbumCount;
        insertPosition += spacerAlbums;
        insertPosition += advertCount;
        return insertPosition;
    }

    public void addItemPage(int page, int pageSize, List<GalleryItem> newItems) {
        super.addItemPage(page, pageSize, newItems);
        for (GalleryItem item : newItems) {
            if (item == GalleryItem.ADVERT) {
                advertCount++;
            }
        }
    }

    public void clear() {
        super.clear();
        subAlbumCount = 0;
        spacerAlbums = 0;
        advertCount = 0;
    }

    @Override
    public int getResourcesCount() {
        return super.getItemCount() - subAlbumCount - spacerAlbums - advertCount;
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
            if (removedItem == CategoryItem.ADVERT) {
                advertCount--;
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
