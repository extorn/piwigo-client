package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.util.CollectionUtils;

/**
 * An item representing a piece of content.
 */
public class CategoryItem extends GalleryItem implements PhotoContainer, Parcelable {
    private static final String TAG = "CategoryItem";

    private List<CategoryItem> childAlbums;
    private int photoCount;
    private long totalPhotoCount;
    private long subCategories;
    private boolean isPrivate;
    private Long representativePictureId;
    private long[] users;
    private long[] groups;
    private long permissionLoadedAt;
    private String thumbnailUrl;
    private boolean isUserCommentsAllowed;
    private boolean isAdminCopy;

    public CategoryItem(CategoryItemStub stub) {
        super(stub.getId(), stub.getName(), null, null, null);
    }

    public CategoryItem(long id) {
        super(id, null, null, null, null);
    }

    public CategoryItem(long id, String name, String description, boolean isPrivate, Date lastAltered, int photoCount, long totalPhotoCount, long subCategories, String thumbnailUrl) {
        super(id, name, description, lastAltered, null);
        this.thumbnailUrl = getRelativePath(thumbnailUrl);
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
        isPrivate = ParcelUtils.readBool(in);
        representativePictureId = ParcelUtils.readLong(in);
        users = in.createLongArray();
        groups = in.createLongArray();
        permissionLoadedAt = in.readLong();
        thumbnailUrl = in.readString();
        isAdminCopy = ParcelUtils.readBool(in);
    }

    public void markAsAdminCopy() {
        isAdminCopy = true;
    }

    public static ArrayList<CategoryItem> newListFromStubs(ArrayList<CategoryItemStub> albumNames) {
        ArrayList<CategoryItem> albums = new ArrayList<>(albumNames.size());
        for(CategoryItemStub c : albumNames) {
            albums.add(new CategoryItem(c));
        }
        return albums;
    }

    public static boolean isRoot(long albumId) {
        return StaticCategoryItem.ROOT_ALBUM.getId() == albumId;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        out.writeTypedList(childAlbums);
        out.writeInt(photoCount);
        out.writeLong(totalPhotoCount);
        out.writeLong(subCategories);
        ParcelUtils.writeBool(out, isPrivate);
        out.writeValue(representativePictureId);
        out.writeLongArray(users);
        out.writeLongArray(groups);
        out.writeLong(permissionLoadedAt);
        out.writeString(thumbnailUrl);
        ParcelUtils.writeBool(out, isAdminCopy);
    }

    @Override
    public String getThumbnailUrl() {
        return getFullPath(thumbnailUrl);
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = getRelativePath(thumbnailUrl);
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

    public boolean isAdminCopy() {
        return isAdminCopy;
    }

    @Override
    public boolean equals(Object other) {
        if(other instanceof CategoryItem) {
            CategoryItem otherItem = (CategoryItem) other;
            return (otherItem.getId() == this.getId() && otherItem.isAdminCopy() == this.isAdminCopy) || (StaticCategoryItem.BLANK_TAG.equals(getName()) && StaticCategoryItem.BLANK_TAG.equals(otherItem.getName()));
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return getName();
    }

    public int getType() {
        return CATEGORY_TYPE;
    }

    public boolean isRoot() {
        return this.getId() == StaticCategoryItem.ROOT_ALBUM.getId() && getParentId() == null;
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
            Logging.log(Log.ERROR, "catItem", "Idx out of bounds for parentage chain : " + Arrays.toString(parentageChain.toArray()) + " idx : " + idx);
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
        Logging.log(Log.WARN, TAG, "Failed to locate child album %1$d with parentage %2$s but it could not be found in any of %3$d children (%5$s) within album %4$d", parentageChain.get(idx + 1), parents, getChildAlbumCount(), parentageChain.get(idx), CollectionUtils.toCsvList(PiwigoUtils.toSetOfIds(getChildAlbums())));
        return null;
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
                subCategoryCount += 1; //childAlbum.getSubCategories() + 1;
                subCategoryPhotoCount += childAlbum.getTotalPhotos();
            }
            totalPhotoCount = photoCount + subCategoryPhotoCount;
            subCategories = subCategoryCount;
        }
    }

    public void removeChildAlbum(@NonNull CategoryItem item) {
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
                Logging.log(Log.ERROR, TAG, "Unable to create category item from parcel: " + in.toString());
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
        List<Long> parentageIds = new ArrayList<>();
        if (child == null) {
            Logging.log(Log.ERROR, TAG, "GetFullPath called for null CategoryItem");
            return new ArrayList<>(0);
        }
        if(child.getParentageChain() != null) {
            parentageIds.addAll(child.getParentageChain());
        }
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
                    Logging.log(Log.ERROR, "CatItem", "Unable to find parent album with id : " + id);
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
        Map<Long,Integer> idMap = new HashMap<>(childAlbums.size());
        for (int i = 0; i < childAlbums.size(); i++) {
            CategoryItem existingChildAlbum = childAlbums.get(i);
            idMap.put(existingChildAlbum.getId(), i);
        }
        for(CategoryItem newAlbum : newAlbums) {
            int idxExisting = idMap.get(newAlbum.getId());
            if (idxExisting < 0) {
                // add to the end (won't affect the index we've just built).
                childAlbums.add(newAlbum);
            } else {
                CategoryItem existingMatchingItem = childAlbums.get(idxExisting);
                if (preferExisting) {
                    // merge any missing children from the new one in
                    existingMatchingItem.mergeChildrenWith(newAlbum.getChildAlbums(), false);
                } else {
                    // replace the existing copy
                    childAlbums.set(idxExisting, newAlbum);
                    // now merge any missing children from the old one into the replacement
                    newAlbum.mergeChildrenWith(existingMatchingItem.getChildAlbums(), false);
                }
            }
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

    @Override
    public int getPagesOfPhotos(int pageSize) {
        int pages = ((getPhotoCount() / pageSize) + (getPhotoCount() % pageSize > 0 ? 0 : -1));
        return Math.max(pages, 0);
    }

    public @Nullable
    CategoryItem getChild(long albumId) {
        if (childAlbums == null) {
            throw new IllegalStateException("Unable to retrieve child (no children set) on album " + getId() + " and desired child id : " + albumId);
        }
        for (CategoryItem childAlbum : childAlbums) {
            if (childAlbum.getId() == albumId) {
                return childAlbum;
            }
        }
        return null;
    }

    public boolean isParentRoot() {
        return getParentId() != null && getParentId() == StaticCategoryItem.ROOT_ALBUM.getId();
    }

    public boolean isUserCommentsAllowed() {
        return isUserCommentsAllowed;
    }

    public void setUserCommentsAllowed(boolean isUserCommentsAllowed) {
        this.isUserCommentsAllowed = isUserCommentsAllowed;
    }

    public void setPhotoCount(int thisAlbumPhotoCount) {
        if(thisAlbumPhotoCount != photoCount) {
            totalPhotoCount += (thisAlbumPhotoCount - photoCount);
        }
        photoCount = thisAlbumPhotoCount;
    }
}
