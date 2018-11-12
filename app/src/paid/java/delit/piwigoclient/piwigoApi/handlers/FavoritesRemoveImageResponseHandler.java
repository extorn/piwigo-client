package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class FavoritesRemoveImageResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UnmarkResAsFavRspHdlr";

    private final ResourceItem piwigoResource;

    public FavoritesRemoveImageResponseHandler(ResourceItem piwigoResource) {
        super("piwigo_client.favorites.removeImage", TAG);
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
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        piwigoResource.setFavorite(false);
        PiwigoRemoveFavoriteResponse r = new PiwigoRemoveFavoriteResponse(getMessageId(), getPiwigoMethod(), piwigoResource, isCached);
        storeResponse(r);
    }

    public static class PiwigoRemoveFavoriteResponse extends PiwigoResponseBufferingHandler.PiwigoResourceItemResponse {
        public PiwigoRemoveFavoriteResponse(long messageId, String piwigoMethod, ResourceItem piwigoResource, boolean isCached) {
            super(messageId, piwigoMethod, piwigoResource, isCached);
        }
    }

}