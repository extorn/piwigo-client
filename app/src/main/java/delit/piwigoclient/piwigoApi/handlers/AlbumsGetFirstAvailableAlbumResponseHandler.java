package delit.piwigoclient.piwigoApi.handlers;

import com.loopj.android.http.AsyncHttpResponseHandler;

import java.util.List;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;

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

        CategoryItem albumTreeRoot = CategoryItem.ROOT_ALBUM.clone();
        CategoryItem currentAlbumItem = albumTreeRoot;
        long desiredAlbumId = albumPath.get(albumPath.size() - 1);
        boolean isAnElementCached = false;
        for (Long albumId : albumPath) {
            if (albumId.equals(CategoryItem.ROOT_ALBUM.getId())) {
                continue;
            }
            AlbumGetSubAlbumsResponseHandler albumListLoadHandler = new AlbumGetSubAlbumsResponseHandler(currentAlbumItem, preferredThumbnailSize, false);
            albumListLoadHandler.invokeAndWait(getContext(), getConnectionPrefs());
            if (!albumListLoadHandler.isSuccess()) {
                // presume the desired child album no longer exists.
                break;
            } else {
                AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse response = (AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) albumListLoadHandler.getResponse();
                currentAlbumItem.setChildAlbums(response.getAlbums());
                boolean found = false;
                for (CategoryItem catItem : response.getAlbums()) {
                    if (catItem.getId() == albumId) {
                        currentAlbumItem = catItem;
                        found = true;
                        break;
                    }
                }
                isAnElementCached |= response.isCached();
                if (!found) {
                    // the desired child album no longer exists.
                    break;
                }
            }
        }


        PiwigoGetAlbumTreeResponse response = new PiwigoGetAlbumTreeResponse(getMessageId(), "pwgcli.int.getAlbumTree", albumPath, albumTreeRoot, currentAlbumItem, isAnElementCached);

        setError(getNestedFailure());
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
