package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class ImageCopyToAlbumResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CopyResourceToAlbumRspHdlr";
    private final T piwigoResource;
    private final CategoryItem targetAlbum;

    public ImageCopyToAlbumResponseHandler(T piwigoResource, CategoryItem targetAlbum) {
        super("pwg.images.setInfo", TAG);
        this.piwigoResource = piwigoResource;
        this.targetAlbum = targetAlbum;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("image_id", String.valueOf(piwigoResource.getId()));
        params.put("categories", String.valueOf(targetAlbum.getId()));
        params.put("multiple_value_mode", "append");
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        PiwigoUpdateAlbumContentResponse r = new PiwigoUpdateAlbumContentResponse<>(getMessageId(), getPiwigoMethod(), targetAlbum, piwigoResource, isCached);
        storeResponse(r);
    }

    public static class PiwigoUpdateAlbumContentResponse<T extends ResourceItem> extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final CategoryItem album;
        private final T piwigoResource;

        public PiwigoUpdateAlbumContentResponse(long messageId, String piwigoMethod, CategoryItem album, T piwigoResource, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.album = album;
            this.piwigoResource = piwigoResource;
        }

        public T getPiwigoResource() {
            return piwigoResource;
        }

        public CategoryItem getAlbum() {
            return album;
        }
    }
}