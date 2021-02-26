package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class AlbumCreateResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CreateGalleryRspHdlr";
    private final PiwigoGalleryDetails newAlbum;
    private final boolean isVisibleOnWebsite;

    protected AlbumCreateResponseHandler(PiwigoGalleryDetails newAlbum, boolean isVisibleOnWebsite, String tag) {
        super("pwg.categories.add", tag);
        this.newAlbum = newAlbum;
        this.isVisibleOnWebsite = isVisibleOnWebsite;
    }

    public AlbumCreateResponseHandler(PiwigoGalleryDetails newAlbum, boolean isVisibleOnWebsite) {
        this(newAlbum, isVisibleOnWebsite, TAG);
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("parent", String.valueOf(newAlbum.getParentGalleryId()));
        params.put("name", newAlbum.getGalleryName());
        params.put("comment", newAlbum.getGalleryDescription());
        params.put("visible", String.valueOf(isVisibleOnWebsite));
        params.put("status", newAlbum.isPrivate() ? "private" : "public");
        params.put("commentable", String.valueOf(newAlbum.isUserCommentsAllowed()));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        long newAlbumnId = result.get("id").getAsLong();
        newAlbum.setGalleryId(newAlbumnId);

        PiwigoAlbumCreatedResponse r = new PiwigoAlbumCreatedResponse(getMessageId(), getPiwigoMethod(), newAlbum, isCached);
        storeResponse(r);
    }

    public static class PiwigoAlbumCreatedResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        private final PiwigoGalleryDetails albumDetails;
        private final long newAlbumId;

        public PiwigoAlbumCreatedResponse(long messageId, String piwigoMethod, long albumId, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.albumDetails = null;
            this.newAlbumId = albumId;
        }

        public PiwigoAlbumCreatedResponse(long messageId, String piwigoMethod, PiwigoGalleryDetails albumDetails, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
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