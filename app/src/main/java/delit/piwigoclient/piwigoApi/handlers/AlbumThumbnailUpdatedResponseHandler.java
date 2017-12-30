package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

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
        if(resourceId != null) {
            params.put("image_id", String.valueOf(resourceId));
        }
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoAlbumThumbnailUpdatedResponse r = new PiwigoResponseBufferingHandler.PiwigoAlbumThumbnailUpdatedResponse(getMessageId(), getPiwigoMethod(), albumParentId);
        storeResponse(r);
    }

}