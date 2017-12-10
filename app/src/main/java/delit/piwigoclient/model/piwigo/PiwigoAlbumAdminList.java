package delit.piwigoclient.model.piwigo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static delit.piwigoclient.model.piwigo.CategoryItem.ROOT_ALBUM;

/**
 * Created by gareth on 10/12/17.
 */

public class PiwigoAlbumAdminList implements Serializable {

    ArrayList<CategoryItem> rootAlbums = new ArrayList<>();


    public List<CategoryItem> getDirectChildrenOfAlbum(List<Long> parentageChain, long albumId) {
        if(ROOT_ALBUM.getParentageChain().equals(parentageChain)) {
            return rootAlbums;
        }
        List<Long> fullAlbumPath = new ArrayList<>(parentageChain);
        fullAlbumPath.add(albumId);
        for(CategoryItem c : rootAlbums) {
            CategoryItem item = c.locateChildAlbum(fullAlbumPath);
            if(item != null) {
                return item.getChildAlbums();
            }
        }
        throw new IllegalStateException("Trying to locate an album, but either it is missing, or part of its ancestory is missing");
    }

    public void addItem(CategoryItem item) {
        if(item.getParentageChain().size() == 1) {
            rootAlbums.add(item);
        } else {
            int parentIdx = 0;
            for(CategoryItem c : rootAlbums) {
                CategoryItem parent = c.locateChildAlbum(item.getParentageChain());
                if(parent != null) {
                    parent.addChildAlbum(item);
                    // no need to check the rest
                    break;
                }
            }
        }
    }

    public void updateTotalPhotosAndSubAlbumCount() {
        for(CategoryItem c : rootAlbums) {
            c.updateTotalPhotoAndSubAlbumCount();
        }
    }

    public ArrayList<CategoryItemStub> flattenTree() {
        ArrayList<CategoryItemStub> flatTree = new ArrayList<>();
        for(CategoryItem c : rootAlbums) {
            flattenTree(flatTree, c);
        }
        return flatTree;
    }

    private void flattenTree(ArrayList<CategoryItemStub> flatTree, CategoryItem node) {
        flatTree.add(node.toStub());
        List<CategoryItem> children = node.getChildAlbums();
        if(children != null) {
            for(CategoryItem child : children) {
                flattenTree(flatTree, child);
            }
        }
    }
}
