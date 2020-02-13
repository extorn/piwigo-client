package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class AlbumThumbnailUpdatedResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UpdateAlbumThumbRspHdlr";
    private final long albumId;
    private final Long resourceId;
    private final Long albumParentId;

    public AlbumThumbnailUpdatedResponseHandler(long albumId, Long albumParentId, Long resourceId) {
        super(resourceId != null ? "pwg.categories.setRepresentative" : "pwg.categories.refreshRepresentative", TAG);
        this.albumId = albumId;
        this.resourceId = resourceId;
        this.albumParentId = albumParentId;
    }

    @Override
    public RequestParams buildRequestParameters() {

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("category_id", String.valueOf(albumId));
        if (resourceId != null) {
            params.put("image_id", String.valueOf(resourceId));
        }
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        PiwigoAlbumThumbnailUpdatedResponse r = new PiwigoAlbumThumbnailUpdatedResponse(getMessageId(), getPiwigoMethod(), albumParentId, albumId, isCached);
        storeResponse(r);
    }

    public static class PiwigoAlbumThumbnailUpdatedResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final Long albumParentId;
        private long albumIdAltered;

        public PiwigoAlbumThumbnailUpdatedResponse(long messageId, String piwigoMethod, Long albumParentId, long albumIdAltered, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.albumParentId = albumParentId;
            this.albumIdAltered = albumIdAltered;
        }

        public Long getAlbumParentIdAltered() {
            return albumParentId;
        }

        public long getAlbumIdAltered() {
            return albumIdAltered;
        }
    }
}