package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.ui.common.util.ParcelUtils;

/**
 * An item representing a piece of content.
 */
public class CategoryItem extends GalleryItem {
    public static final CategoryItem ROOT_ALBUM = new CategoryItem(0, "--------", null, false, null, 0, 0, 0, null);
    public static final CategoryItem BLANK = new CategoryItem(Long.MIN_VALUE, null, null, true, null, 0, 0, 0, null);
    public static final CategoryItem ALBUM_HEADING = new CategoryItem(Long.MIN_VALUE + 1, "AlbumsHeading", null, true, null, 0, 0, 0, null) {
        @Override
        public int getType() {
            return GalleryItem.ALBUM_HEADING_TYPE;
        }
    };
    private List<CategoryItem> childAlbums;
    private int photoCount;
    private long totalPhotoCount;
    private long subCategories;
    private boolean isPrivate;
    private Long representativePictureId;
    private long[] users;
    private long[] groups;
    private long permissionLoadedAt;

    public CategoryItem(long id) {
        super(id, null, null, null, null);
    }

    public CategoryItem(long id, String name, String description, boolean isPrivate, Date lastAltered, int photoCount, long totalPhotoCount, long subCategories, String thumbnailUrl) {
        super(id, name, description, lastAltered, thumbnailUrl);
        this.photoCount = photoCount;
        this.isPrivate = isPrivate;
        this.totalPhotoCount = totalPhotoCount;
        this.subCategories = subCategories;
    }

    public CategoryItem(Parcel in) {
        super(in);
        childAlbums = ParcelUtils.readTypedList(in, CategoryItem.CREATOR);
        photoCount = in.readInt();
        totalPhotoCount = in.readLong();
        subCategories = in.readLong();
        isPrivate = (boolean) in.readValue(null);
        representativePictureId = (Long) in.readValue(null);
        users = (long[]) in.readValue(null);
        groups = (long[]) in.readValue(null);
        permissionLoadedAt = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        out.writeTypedList(childAlbums);
        out.writeInt(photoCount);
        out.writeLong(totalPhotoCount);
        out.writeLong(subCategories);
        out.writeValue(isPrivate);
        out.writeValue(representativePictureId);
        out.writeLongArray(users);
        out.writeLongArray(groups);
        out.writeLong(permissionLoadedAt);
    }

    /**
     * Used for the admin list of albums
     *
     * @param childAlbum
     */
    public void addChildAlbum(CategoryItem childAlbum) {
        if (childAlbums == null) {
            childAlbums = new ArrayList<>();
        }
        childAlbums.add(childAlbum);
    }

    /**
     * Used for the admin list of albums
     *
     * @return
     */
    public List<CategoryItem> getChildAlbums() {
        return childAlbums;
    }

    public Long getRepresentativePictureId() {
        return representativePictureId;
    }

    public void setRepresentativePictureId(Long representativePictureId) {
        this.representativePictureId = representativePictureId;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GalleryItem && ((GalleryItem) other).getId() == this.getId();
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
        permissionLoadedAt = System.currentTimeMillis();
    }

    public long[] getGroups() {
        return groups;
    }

    public void setGroups(long[] groups) {
        this.groups = groups;
        permissionLoadedAt = System.currentTimeMillis();
    }

    public long getTotalPhotos() {
        return totalPhotoCount;
    }

    public void reducePhotoCount() {
        photoCount--;
        totalPhotoCount--;
    }

    public int getPhotoCount() {
        return photoCount;
    }

    public long getSubCategories() {
        return subCategories;
    }

    public void setSubCategories(int subCategories) {
        this.subCategories = subCategories;
    }

    public CategoryItem locateChildAlbum(List<Long> parentageChain) {
        return locateChildAlbum(parentageChain, 1);
    }

    private CategoryItem locateChildAlbum(List<Long> parentageChain, int idx) {
        if (parentageChain.size() <= idx) {
            if (BuildConfig.DEBUG) {
                Log.e("catItem", "Idx out of bounds for parentage chain : " + parentageChain.toArray() + " idx : " + idx);
            }
            return null;
        }
        if (getId() != parentageChain.get(idx)) {
            return null;
        }
        if (parentageChain.size() == getParentageChain().size() + 1) {
            return this;
        }
        if (childAlbums != null) {
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
        if (childAlbums == null) {
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

    public void removeChildAlbum(CategoryItem item) {
        childAlbums.remove(item);
        updateTotalPhotoAndSubAlbumCount();
    }

    public boolean removeChildAlbum(long albumId) {
        CategoryItem item;
        boolean removed = false;
        if (childAlbums != null) {
            for (Iterator<CategoryItem> iter = childAlbums.iterator(); iter.hasNext(); ) {
                item = iter.next();
                if (item.getId() == albumId) {
                    iter.remove();
                    removed = true;
                    break;
                }
            }
        }
        if (removed) {
            updateTotalPhotoAndSubAlbumCount();
        }
        return removed;
    }

    public static final Parcelable.Creator<CategoryItem> CREATOR
            = new Parcelable.Creator<CategoryItem>() {
        public CategoryItem createFromParcel(Parcel in) {
            return new CategoryItem(in);
        }

        public CategoryItem[] newArray(int size) {
            return new CategoryItem[size];
        }
    };

    public void forcePermissionsReload() {
        permissionLoadedAt = 0;
    }

    public boolean isPermissionsLoaded() {
        return !isLikelyOutdated(permissionLoadedAt) && groups != null && users != null;
    }

}
