package delit.piwigoclient.piwigoApi.handlers;

import com.loopj.android.http.AsyncHttpResponseHandler;

import java.util.Iterator;
import java.util.List;

import delit.libs.http.RequestParams;
import delit.libs.http.cache.CachingAsyncHttpClient;
import delit.libs.http.cache.RequestHandle;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class AlbumsGetFirstAvailableAlbumResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "AlbumGet1stRspHdlr";
    private final List<Long> albumPath;
    private final String preferredThumbnailSize;

    public AlbumsGetFirstAvailableAlbumResponseHandler(List<Long> albumPath, String preferredThumbnailSize) {
        super(null, TAG);
        this.albumPath = albumPath;
        this.preferredThumbnailSize = preferredThumbnailSize;
    }

    @Override
    public RequestParams buildRequestParameters() {
        return null;
    }

    @Override
    public String getPiwigoMethod() {
        return getNestedFailureMethod();
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler, boolean forceResponseRevalidation) {

        CategoryItem albumTreeRoot = StaticCategoryItem.ROOT_ALBUM.toInstance();
        CategoryItem currentAlbumItem = albumTreeRoot;
        boolean isAnElementCached = false;

        if (!albumPath.isEmpty()) {
//            long desiredAlbumId = albumPath.get(albumPath.size() - 1);
            for (Long albumId : albumPath) {
                if (albumId.equals(StaticCategoryItem.ROOT_ALBUM.getId())) {
                    continue;
                }
                AlbumGetChildAlbumsResponseHandler albumListLoadHandler = new AlbumGetChildAlbumsResponseHandler(currentAlbumItem, preferredThumbnailSize, false);
                albumListLoadHandler.invokeAndWait(getContext(), getConnectionPrefs());
                if (!albumListLoadHandler.isSuccess()) {
                    // presume the desired child album no longer exists.
                    reportNestedFailure(albumListLoadHandler);
                    break;
                } else {
                    AlbumGetChildAlbumsResponseHandler.PiwigoGetSubAlbumsResponse response = (AlbumGetChildAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) albumListLoadHandler.getResponse();
                    currentAlbumItem.setChildAlbums(response.getAlbums());
                    boolean found = false;
                    Iterator<CategoryItem> iter = response.getAlbums().iterator();
                    while (iter.hasNext()) {
                        CategoryItem catItem = iter.next();
                        if (catItem.getId() == albumId) {
                            currentAlbumItem = catItem;
                            found = true;
                        } else {
                            iter.remove();
                        }
                    }
                    isAnElementCached |= response.isCached();
                    if (!found) {
                        // the desired child album no longer exists.
                        break;
                    }
                }
            }
        }


        PiwigoGetAlbumTreeResponse response = new PiwigoGetAlbumTreeResponse(getMessageId(), "pwgcli.int.getAlbumTree", albumPath, albumTreeRoot, currentAlbumItem, isAnElementCached);
        storeResponse(response);

        return null;
    }

    public static class PiwigoGetAlbumTreeResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final CategoryItem albumTreeRoot;
        private final CategoryItem deepestAlbumOnDesiredPath;
        private final List<Long> albumPath;

        public PiwigoGetAlbumTreeResponse(long messageId, String piwigoMethod, List<Long> albumPath, CategoryItem albumTreeRoot, CategoryItem deepestAlbumOnDesiredPath, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.albumPath = albumPath;
            this.albumTreeRoot = albumTreeRoot;
            this.deepestAlbumOnDesiredPath = deepestAlbumOnDesiredPath;
        }

        public CategoryItem getAlbumTreeRoot() {
            return albumTreeRoot;
        }

        public CategoryItem getDeepestAlbumOnDesiredPath() {
            return deepestAlbumOnDesiredPath;
        }

        public List<Long> getAlbumPath() {
            return albumPath;
        }
    }

}
