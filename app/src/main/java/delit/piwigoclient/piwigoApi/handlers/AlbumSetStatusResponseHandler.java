package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class AlbumSetStatusResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "SetAlbmStatusRspHdlr";
    private final PiwigoGalleryDetails album;

    public AlbumSetStatusResponseHandler(PiwigoGalleryDetails album) {
        super("pwg.categories.setInfo", TAG);
        this.album = album;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("category_id", album.getGalleryId().toString());
//        params.put("name", album.getGalleryName());
//        params.put("comment", album.getGalleryDescription());
        params.put("status", album.isPrivate() ? "private" : "public");
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        PiwigoSetAlbumStatusResponse r = new PiwigoSetAlbumStatusResponse(getMessageId(), getPiwigoMethod(), isCached);
        storeResponse(r);
    }


    public static class PiwigoSetAlbumStatusResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        public PiwigoSetAlbumStatusResponse(long messageId, String piwigoMethod, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
        }
    }
}