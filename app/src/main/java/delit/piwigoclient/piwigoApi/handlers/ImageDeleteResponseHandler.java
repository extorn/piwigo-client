package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class ImageDeleteResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "DeleteImageRspHdlr";

    private long itemId = -1;
    private HashSet<Long> itemIds;

    public ImageDeleteResponseHandler(HashSet<Long> itemIds) {
        super("pwg.images.delete", TAG);
        this.itemIds = itemIds;
    }

    public ImageDeleteResponseHandler(long itemId) {
        super("pwg.images.delete", TAG);
        this.itemId = itemId;
    }

    @Override
    public RequestParams buildRequestParameters() {
        String sessionToken = "";
        if(PiwigoSessionDetails.isLoggedInWithSessionDetails()) {
            sessionToken = PiwigoSessionDetails.getInstance().getSessionToken();
        }
        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        if(itemId > 0) {
            params.put("image_id", String.valueOf(itemId));
        }
        if(itemIds != null) {
            for (Long itemId : itemIds) {
                params.add("image_id[]", String.valueOf(itemId));
            }
        }
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoDeleteImageResponse r = new PiwigoResponseBufferingHandler.PiwigoDeleteImageResponse(getMessageId(), getPiwigoMethod(), itemIds);
        storeResponse(r);
    }

}