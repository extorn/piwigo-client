package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

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
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoUpdateAlbumContentResponse r = new PiwigoResponseBufferingHandler.PiwigoUpdateAlbumContentResponse(getMessageId(), getPiwigoMethod(), targetAlbum);
        storeResponse(r);
    }

}