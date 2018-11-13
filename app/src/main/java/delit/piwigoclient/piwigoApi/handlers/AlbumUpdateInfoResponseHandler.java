package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class AlbumUpdateInfoResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UpdateGalleryInfoRspHdlr";
    private final CategoryItem album;

    public AlbumUpdateInfoResponseHandler(CategoryItem album) {
        super("pwg.categories.setInfo", TAG);
        this.album = album;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        PiwigoUpdateAlbumInfoResponse r = new PiwigoUpdateAlbumInfoResponse(getMessageId(), getPiwigoMethod(), album, isCached);
        storeResponse(r);
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("category_id", String.valueOf(album.getId()));
        params.put("name", album.getName());
        params.put("comment", album.getDescription());
        params.put("status", album.isPrivate() ? "private" : "public");
        return params;
    }

    public static class PiwigoUpdateAlbumInfoResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final CategoryItem album;

        public PiwigoUpdateAlbumInfoResponse(long messageId, String piwigoMethod, CategoryItem album, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.album = album;
        }

        public CategoryItem getAlbum() {
            return album;
        }
    }
}