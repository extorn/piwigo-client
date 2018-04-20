package delit.piwigoclient.piwigoApi.handlers;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 4/19/18.
 */

public class ImageChangeParentAlbumHandler<T extends ResourceItem> extends ImageSetLinkedAlbumsResponseHandler<T> {
    public ImageChangeParentAlbumHandler(T piwigoResource, CategoryItem targetAlbum) {
        super(piwigoResource, getUpdatedLinkedAlbums(piwigoResource, targetAlbum));
    }

    private static <T extends ResourceItem> HashSet<Long> getUpdatedLinkedAlbums(T piwigoResource, CategoryItem targetAlbum) {
        long moveFromAlbum = piwigoResource.getParentId();
        HashSet<Long> linkedAlbums = piwigoResource.getLinkedAlbums();
        linkedAlbums.remove(moveFromAlbum);
        linkedAlbums.add(targetAlbum.getId());
        return linkedAlbums;
    }
}
