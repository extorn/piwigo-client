package delit.piwigoclient.model.piwigo;

import java.util.Date;

/**
 * An item representing a piece of content.
 */
public class CategoryItem extends GalleryItem {
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
    private long thumbnailId;


    public CategoryItem(long id, String name, String description, boolean isPrivate, Date lastAltered, long photoCount, long totalPhotoCount, long subCategories, String thumbnailUrl) {
        super(id, name, description, lastAltered, thumbnailUrl);
        this.photoCount = photoCount;
        this.isPrivate = isPrivate;
        this.totalPhotoCount = totalPhotoCount;
        this.subCategories = subCategories;
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
        return this.getId() == PiwigoAlbum.ROOT_ALBUM.getId() && getParentId() == null;
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

    public long getThumbnailId() {
        return thumbnailId;
    }
}
