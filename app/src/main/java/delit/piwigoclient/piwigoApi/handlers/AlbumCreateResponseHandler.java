package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class AlbumCreateResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CreateGalleryRspHdlr";
    private final PiwigoGalleryDetails newAlbum;

    public AlbumCreateResponseHandler(PiwigoGalleryDetails newAlbum) {
        super("pwg.categories.add", TAG);
        this.newAlbum = newAlbum;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("parent", String.valueOf(newAlbum.getParentGalleryId()));
        params.put("name", newAlbum.getGalleryName());
        params.put("comment", newAlbum.getGalleryDescription());
        params.put("visible", String.valueOf(true));
        params.put("status", newAlbum.isPrivate() ? "private" : "public");
        params.put("commentable", String.valueOf(newAlbum.isUserCommentsAllowed()));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        long newAlbumnId = result.get("id").getAsLong();
        newAlbum.setGalleryId(newAlbumnId);

        PiwigoAlbumCreatedResponse r = new PiwigoAlbumCreatedResponse(getMessageId(), getPiwigoMethod(), newAlbum);
        storeResponse(r);
    }

    public static class PiwigoAlbumCreatedResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        private final PiwigoGalleryDetails albumDetails;
        private final long newAlbumId;

        public PiwigoAlbumCreatedResponse(long messageId, String piwigoMethod, long albumId) {
            super(messageId, piwigoMethod, true);
            this.albumDetails = null;
            this.newAlbumId = albumId;
        }

        public PiwigoAlbumCreatedResponse(long messageId, String piwigoMethod, PiwigoGalleryDetails albumDetails) {
            super(messageId, piwigoMethod, true);
            this.albumDetails = albumDetails;
            this.newAlbumId = albumDetails.getGalleryId();
        }

        public PiwigoGalleryDetails getAlbumDetails() {
            return albumDetails;
        }

        public long getNewAlbumId() {
            return newAlbumId;
        }
    }
}