package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static delit.piwigoclient.model.piwigo.CategoryItem.ROOT_ALBUM;

/**
 * Created by gareth on 10/12/17.
 */

public class PiwigoAlbumAdminList implements Parcelable {

    private final ArrayList<CategoryItem> rootAlbums = new ArrayList<>();

    public PiwigoAlbumAdminList() {}

    public PiwigoAlbumAdminList(Parcel in) {
        in.readList(rootAlbums, null);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeList(rootAlbums);
    }

    public List<CategoryItem> getDirectChildrenOfAlbum(List<Long> parentageChain, long albumId) {
        if (ROOT_ALBUM.getParentageChain().equals(parentageChain)) {
            return rootAlbums;
        }
        List<Long> fullAlbumPath = new ArrayList<>(parentageChain);
        fullAlbumPath.add(albumId);
        for (CategoryItem c : rootAlbums) {
            CategoryItem item = c.locateChildAlbum(fullAlbumPath);
            if (item != null) {
                return item.getChildAlbums();
            }
        }
        throw new IllegalStateException("Trying to locate an album, but either it is missing, or part of its ancestory is missing");
    }

    public CategoryItem getAlbum(CategoryItem album) {
        List<Long> fullAlbumPath = new ArrayList<>(album.getParentageChain());
        fullAlbumPath.add(album.getId());
        for (CategoryItem c : rootAlbums) {
            CategoryItem item = c.locateChildAlbum(fullAlbumPath);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    public void addItem(CategoryItem item) {
        if (item.getParentageChain().size() == 1) {
            rootAlbums.add(item);
        } else {
            int parentIdx = 0;
            for (CategoryItem c : rootAlbums) {
                CategoryItem parent = c.locateChildAlbum(item.getParentageChain());
                if (parent != null) {
                    parent.addChildAlbum(item);
                    // no need to check the rest
                    break;
                }
            }
        }
    }

    public void updateTotalPhotosAndSubAlbumCount() {
        for (CategoryItem c : rootAlbums) {
            c.updateTotalPhotoAndSubAlbumCount();
        }
    }

    public ArrayList<CategoryItemStub> flattenTree() {
        ArrayList<CategoryItemStub> flatTree = new ArrayList<>();
        for (CategoryItem c : rootAlbums) {
            flattenTree(flatTree, c);
        }
        return flatTree;
    }

    private void flattenTree(ArrayList<CategoryItemStub> flatTree, CategoryItem node) {
        flatTree.add(node.toStub());
        List<CategoryItem> children = node.getChildAlbums();
        if (children != null) {
            for (CategoryItem child : children) {
                flattenTree(flatTree, child);
            }
        }
    }

    protected CategoryItem findAlbumInSubTree(long albumId, CategoryItem subTree) {
        CategoryItem item;
        if(subTree.getChildAlbums() != null) {
            for (Iterator<CategoryItem> childrenIter = subTree.getChildAlbums().iterator(); childrenIter.hasNext(); ) {
                item = childrenIter.next();
                if (item.getId() == albumId) {
                    return item;
                } else if (item.getChildAlbums().size() > 0) {
                    item = findAlbumInSubTree(albumId, item);
                    if (item != null) {
                        return item;
                    }
                }
            }
        }
        return null;
    }

    protected CategoryItem findAlbum(long albumId) {
        CategoryItem item;
        for (Iterator<CategoryItem> iter = rootAlbums.iterator(); iter.hasNext(); ) {
            item = iter.next();
            if(item.getId() != albumId) {
                item = findAlbumInSubTree(albumId, item);
            }
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    public boolean removeAlbumById(CategoryItem parent, long albumId) {
        if(ROOT_ALBUM.equals(parent)) {
            for (Iterator<CategoryItem> rootAlbumIter = rootAlbums.iterator(); rootAlbumIter.hasNext(); ) {
                if(rootAlbumIter.next().getId() == albumId) {
                    rootAlbumIter.remove();
                    return true;
                }
            }
        } else {
            CategoryItem adminCopyOfParent = findAlbum(parent.getId());
            if (adminCopyOfParent != null) {
                adminCopyOfParent.removeChildAlbum(albumId);
                return true;
            }
        }
        return false;
    }

    public List<CategoryItem> getAlbums() {
        return rootAlbums;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<PiwigoAlbumAdminList> CREATOR
            = new Parcelable.Creator<PiwigoAlbumAdminList>() {
        public PiwigoAlbumAdminList createFromParcel(Parcel in) {
            return new PiwigoAlbumAdminList(in);
        }

        public PiwigoAlbumAdminList[] newArray(int size) {
            return new PiwigoAlbumAdminList[size];
        }
    };

    public List<CategoryItem> getDirectChildrenOfAlbum(CategoryItem album) {
        if(ROOT_ALBUM.equals(album)) {
            return rootAlbums;
        } else {
            return getDirectChildrenOfAlbum(album.getParentageChain(), album.getId());
        }
    }
}
