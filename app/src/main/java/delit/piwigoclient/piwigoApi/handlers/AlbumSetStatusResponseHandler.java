package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

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
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        PiwigoSetAlbumStatusResponse r = new PiwigoSetAlbumStatusResponse(getMessageId(), getPiwigoMethod());
        storeResponse(r);
    }


    public static class PiwigoSetAlbumStatusResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        public PiwigoSetAlbumStatusResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod, true);
        }
    }
}