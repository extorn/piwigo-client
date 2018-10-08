package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class FavoritesAddImageResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "MarkResAsFavRspHdlr";

    private final ResourceItem piwigoResource;

    public FavoritesAddImageResponseHandler(ResourceItem piwigoResource) {
        super("piwigo_client.favorites.addImage", TAG);
        this.piwigoResource = piwigoResource;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("image_id", String.valueOf(piwigoResource.getId()));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        piwigoResource.setFavorite(true);
        PiwigoAddFavoriteResponse r = new PiwigoAddFavoriteResponse(getMessageId(), getPiwigoMethod(), piwigoResource);
        storeResponse(r);
    }

    public static class PiwigoAddFavoriteResponse extends PiwigoResponseBufferingHandler.PiwigoResourceItemResponse {
        public PiwigoAddFavoriteResponse(long messageId, String piwigoMethod, ResourceItem piwigoResource) {
            super(messageId, piwigoMethod, piwigoResource);
        }
    }
}