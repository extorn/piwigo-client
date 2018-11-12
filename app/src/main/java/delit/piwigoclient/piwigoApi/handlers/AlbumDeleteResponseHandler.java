package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class AlbumDeleteResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "DeleteGalleryRspHdlr";
    private final long galleryId;

    public AlbumDeleteResponseHandler(long galleryId) {
        super("pwg.categories.delete", TAG);
        this.galleryId = galleryId;
    }

    @Override
    public RequestParams buildRequestParameters() {
        String sessionToken = "";
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
            sessionToken = sessionDetails.getSessionToken();
        }
        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("category_id", String.valueOf(galleryId));
        params.put("photo_deletion_mode", "delete_orphans");
        params.put("pwg_token", sessionToken);
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