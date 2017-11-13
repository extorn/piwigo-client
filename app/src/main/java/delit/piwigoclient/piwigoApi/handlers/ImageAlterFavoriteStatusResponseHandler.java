package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONException;
import org.json.JSONObject;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class ImageAlterFavoriteStatusResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "AlterResFavStatusRspHdlr";

    private final ResourceItem piwigoResource;

    public ImageAlterFavoriteStatusResponseHandler(ResourceItem piwigoResource) {
        super("?????????", TAG);
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
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        //TODO implement this
        PiwigoResponseBufferingHandler.PiwigoFavoriteStatusResponse r = new PiwigoResponseBufferingHandler.PiwigoFavoriteStatusResponse(getMessageId(), getPiwigoMethod(), piwigoResource);
        storeResponse(r);
    }

}