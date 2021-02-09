package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class AlbumDeleteResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "DeleteGalleryRspHdlr";
    private final long galleryId;
    private final boolean deleteOrphans;

    public AlbumDeleteResponseHandler(long galleryId, boolean deleteOrphans) {
        super("pwg.categories.delete", TAG);
        this.galleryId = galleryId;
        this.deleteOrphans = deleteOrphans;
    }

    @Override
    public RequestParams buildRequestParameters() {

        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("category_id", String.valueOf(galleryId));
        if(deleteOrphans) {
            params.put("photo_deletion_mode", "delete_orphans");
        } else {
            params.put("photo_deletion_mode", "no_delete");
        }
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        PiwigoAlbumDeletedResponse r = new PiwigoAlbumDeletedResponse(getMessageId(), getPiwigoMethod(), galleryId, isCached);
        storeResponse(r);
    }

    public static class PiwigoAlbumDeletedResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final long albumId;

        public PiwigoAlbumDeletedResponse(long messageId, String piwigoMethod, long albumId, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.albumId = albumId;
        }

        public long getAlbumId() {
            return albumId;
        }
    }
}