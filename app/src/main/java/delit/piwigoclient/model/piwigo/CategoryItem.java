package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.ui.common.util.ParcelUtils;
import delit.piwigoclient.util.CollectionUtils;

/**
 * An item representing a piece of content.
 */
public class CategoryItem extends GalleryItem implements Cloneable {
    private static final String TAG = "CategoryItem";
    private static final String BLANK_TAG = "PIWIGO_CLIENT_INTERNAL_BLANK";
    public static final CategoryItem ROOT_ALBUM = new CategoryItem(0, "--------", null, false, null, 0, 0, 0, null);
    public static final CategoryItem BLANK = new CategoryItem(Long.MIN_VALUE, BLANK_TAG, null, true, null, 0, 0, 0, null);
    public static final CategoryItem ALBUM_HEADING = new CategoryItem(Long.MIN_VALUE + 100, "AlbumsHeading", null, true, null, 0, 0, 0, null) {
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

    public CategoryItem(CategoryItemStub stub) {
        super(stub.getId(), stub.getName(), null, null, null);
    }

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
        childAlbums = in.createTypedArrayList(CategoryItem.CREATOR);
        photoCount = in.readInt();
        totalPhotoCount = in.readLong();
        subCategories = in.readLong();
        isPrivate = ParcelUtils.readValue(in,null, boolean.class);
        representativePictureId = ParcelUtils.readValue(in,null, Long.class);
        users = in.createLongArray();
        groups = in.createLongArray();
        permissionLoadedAt = in.readLong();
    }

    public static ArrayList<CategoryItem> newListFromStubs(ArrayList<CategoryItemStub> albumNames) {
        ArrayList<CategoryItem> albums = new ArrayList<>(albumNames.size());
        for(CategoryItemStub c : albumNames) {
            albums.add(new CategoryItem(c));
        }
        return albums;
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

    public void setChildAlbums(ArrayList<CategoryItem> childAlbums) {
        this.childAlbums = childAlbums;
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
        if(other instanceof GalleryItem) {
            GalleryItem otherItem = (GalleryItem) other;
            return otherItem.getId() == this.getId() || BLANK_TAG.equals(getName()) && BLANK_TAG.equals(otherItem.getName());
        }
        return false;
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
                Log.e("catItem", "Idx out of bounds for parentage chain : " + Arrays.toString(parentageChain.toArray()) + " idx : " + idx);
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
        String parents = CollectionUtils.toCsvList(parentageChain);
        throw new IllegalStateException(String.format("Failed to locate child album %1$d with parentage %2$s but it could not be found in any of %3$d children within album %4$d", parentageChain.get(idx+1), parents, getChildAlbumCount(), parentageChain.get(idx)));
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
            try {
                return new CategoryItem(in);
            } catch(RuntimeException e) {
                Crashlytics.log(Log.ERROR, TAG, "Unable to create category item from parcel: " + in.toString());
                throw e;
            }
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

    public int getChildAlbumCount() {
        if(childAlbums == null) {
            return 0;
        }
        return childAlbums.size();
    }

    @Override
    public CategoryItem clone() {
        Parcel p = Parcel.obtain();
        byte[] dataBytes;
        try {
            writeToParcel(p, 0);
            dataBytes = p.marshall();
        } finally {
            p.recycle();
        }

        // get a fresh parcel.
        p = Parcel.obtain();

        CategoryItem clone = null;
        try {
            p.unmarshall(dataBytes, 0, dataBytes.length);
            p.setDataPosition(0);
            clone = new CategoryItem(p);
        } finally {
            p.recycle();
        }
        return clone;
    }

    public CategoryItem findChild(long id) {
        CategoryItem child = null;
        if(this.getId() == id) {
            return this;
        }
        if(childAlbums != null) {
            for(CategoryItem i : childAlbums) {
                if(i.getId() == id) {
                    return i;
                }
            }
            for(CategoryItem i : childAlbums) {
                if(i.getChildAlbumCount() > 0) {
                    child = i.findChild(id);
                    if(child != null) {
                        break;
                    }
                }
            }
        }
        return child;
    }

    public CategoryItem findImmediateChild(long id) {
        if(childAlbums != null) {
            for (CategoryItem i : childAlbums) {
                if (i.getId() == id) {
                    return i;
                }
            }
        }
        return null;
    }

    /**
     * root, child, ..., child, this
     * @return
     */
    public List<CategoryItem> getFullPath(CategoryItem child) {
        List<Long> parentageIds = new ArrayList<>(child.getParentageChain());
        CategoryItem root = this;
        List<CategoryItem> parentage = new ArrayList<>(parentageIds.size() + 1);
        parentage.add(root);
        // remove the root item
        if(parentageIds.size() > 0) {
            parentageIds.remove(0);
            while (parentageIds.size() > 0 && root != null) {
                long id = parentageIds.remove(0);
                root = root.findImmediateChild(id);
                if (root != null) {
                    parentage.add(root);
                } else {
                    Crashlytics.log(Log.ERROR, "CatItem", "Unable to find parent album with id : " + id);
                }
            }
        }
        if(!child.isRoot()) {
            parentage.add(child);
        }
        return parentage;
    }

    /**
     * Warning. This will be destructive to the newAlbums object passed in.
     * @param newAlbums
     * @param preferExisting
     */
    public void mergeChildrenWith(List<CategoryItem> newAlbums, boolean preferExisting) {
        if(newAlbums == null) {
            return;
        }
        for(int i = 0; i < getChildAlbumCount(); i++) {
            CategoryItem existingMatchingItem = childAlbums.get(i);
            int matchItemIdx = newAlbums.indexOf(existingMatchingItem);
            if(matchItemIdx < 0) {
                continue;
            }
            CategoryItem newMatchingItem = newAlbums.get(matchItemIdx);
            newAlbums.remove(matchItemIdx);
            if(newMatchingItem != null) {
                if(!preferExisting) {
                    // swap to the new copy. Note still need to merge old child albums across.
                    childAlbums.remove(i);
                    childAlbums.add(i, newMatchingItem);
                    mergeChildrenWith(i, newMatchingItem, existingMatchingItem, !preferExisting);
                } else {
                    mergeChildrenWith(i, existingMatchingItem, newMatchingItem, preferExisting);
                }
            }
        }
        if(childAlbums == null) {
            childAlbums = new ArrayList<>();
        }
        childAlbums.addAll(newAlbums);
    }

    private void mergeChildrenWith(int thisItemIdx, CategoryItem thisItem, CategoryItem otherItem, boolean preferExisting) {
        if(preferExisting) {
            thisItem.mergeChildrenWith(otherItem.getChildAlbums(), preferExisting);
        } else {
            otherItem.mergeChildrenWith(thisItem.getChildAlbums(), preferExisting);
            childAlbums.remove(thisItemIdx);
            childAlbums.add(thisItemIdx, otherItem);
        }
    }

    public String getAlbumPath(CategoryItem selectedItem) {
        List<CategoryItem> path = getFullPath(selectedItem);
        StringBuilder sb = new StringBuilder();
        for(CategoryItem item : path) {
            if(!item.isRoot()) {
                if (sb.length() > 0) {
                    sb.append(" / ");
                }
                sb.append(item.getName());
            }
        }
        return sb.toString();
    }

    public CategoryItem withId(long newId) {
        this.setId(newId);
        return this;
    }
}
