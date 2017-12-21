package delit.piwigoclient.model.piwigo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * An item representing a piece of content.
 */
public class CategoryItem extends GalleryItem {
    public static final CategoryItem ROOT_ALBUM = new CategoryItem(0, "--------", null, false, null, 0, 0, 0, null);
    private List<CategoryItem> childAlbums;
    private long photoCount;
    private long totalPhotoCount;
    private long subCategories;
    private boolean isPrivate;
    private Long representativePictureId;
    private long[] users;
    private long[] groups;

    public static final CategoryItem ADVERT = new CategoryItem(Long.MIN_VALUE + 1, null, null, true, null, 0, 0, 0, null) {
        @Override
        public int getType() {
            return GalleryItem.CATEGORY_ADVERT_TYPE;
        }
    };
    public static final CategoryItem BLANK = new CategoryItem(Long.MIN_VALUE, null, null, true, null, 0, 0, 0, null);

    public CategoryItem(long id, String name, String description, boolean isPrivate, Date lastAltered, long photoCount, long totalPhotoCount, long subCategories, String thumbnailUrl) {
        super(id, name, description, lastAltered, thumbnailUrl);
        this.photoCount = photoCount;
        this.isPrivate = isPrivate;
        this.totalPhotoCount = totalPhotoCount;
        this.subCategories = subCategories;
    }

    /**
     * Used for the admin list of albums
     * @param childAlbum
     */
    public void addChildAlbum(CategoryItem childAlbum) {
        if(childAlbums == null) {
            childAlbums = new ArrayList<>();
        }
        childAlbums.add(childAlbum);
    }

    /**
     * Used for the admin list of albums
     * @return
     */
    public List<CategoryItem> getChildAlbums() {
        return childAlbums;
    }

    public void setRepresentativePictureId(Long representativePictureId) {
        this.representativePictureId = representativePictureId;
    }

    public Long getRepresentativePictureId() {
        return representativePictureId;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GalleryItem)) {
            return false;
        }
        return ((GalleryItem) other).getId() == this.getId();
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return getName();
    }

    public int getType() {
        return CATEGORY_TYPE;
    }

    public boolean isRoot() {
        return this.getId() == ROOT_ALBUM.getId() && getParentId() == null;
    }

    public void setSubCategories(int subCategories) {
        this.subCategories = subCategories;
    }

    public CategoryItemStub toStub() {
        CategoryItemStub stub = new CategoryItemStub(getName(), getId());
        stub.setParentageChain(getParentageChain());
        return stub;
    }

    public long[] getUsers() {
        return users;
    }

    public void setUsers(long[] users) {
        this.users = users;
    }

    public long[] getGroups() {
        return groups;
    }

    public void setGroups(long[] groups) {
        this.groups = groups;
    }

    public long getTotalPhotos() {
        return totalPhotoCount;
    }

    public void reducePhotoCount() {
        photoCount--;
        totalPhotoCount--;
    }

    public long getPhotoCount() {
        return photoCount;
    }

    public long getSubCategories() {
        return subCategories;
    }

    public CategoryItem locateChildAlbum(List<Long> parentageChain) {
        return locateChildAlbum(parentageChain,1);
    }

    private CategoryItem locateChildAlbum(List<Long> parentageChain, int idx) {
        if(getId() != parentageChain.get(idx).longValue()) {
            return null;
        }
        if(parentageChain.size() == getParentageChain().size() + 1) {
            return this;
        }
        if(childAlbums != null) {
            for (CategoryItem c : childAlbums) {
                CategoryItem item = c.locateChildAlbum(parentageChain, idx + 1);
                if (item != null) {
                    return item;
                }
            }
        }
        throw new IllegalStateException("Trying to locate an album, but either it is missing, or part of its ancestory is missing");
    }

    public void updateTotalPhotoAndSubAlbumCount() {
        if(childAlbums == null) {
            subCategories = 0;
            totalPhotoCount = photoCount;
        } else {
            int subCategoryCount = 0;
            int subCategoryPhotoCount = 0;
            for (CategoryItem childAlbum : childAlbums) {
                childAlbum.updateTotalPhotoAndSubAlbumCount();
                subCategoryCount += childAlbum.getSubCategories() + 1;
                subCategoryPhotoCount += childAlbum.getTotalPhotos();
            }
            totalPhotoCount = photoCount + subCategoryPhotoCount;
            subCategories = subCategoryCount;
        }
    }
}
