package delit.piwigoclient.piwigoApi.upload.handlers;

import org.json.JSONException;import com.google.gson.JsonElement;import com.google.gson.JsonObject;import com.google.gson.JsonArray;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class ImageAddResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "AddImageRspHdlr";
    private final String name;
    private final String checksum;
    private final Long albumId;
    private final int privacyLevel;

    public ImageAddResponseHandler(String name, String checksum, Long albumId, int privacyLevel) {
        super("pwg.images.add", TAG);
        this.name = name;
        this.checksum = checksum;
        this.albumId = albumId;
        this.privacyLevel = privacyLevel;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("name", name);
        params.put("original_sum", checksum);
        params.put("categories", String.valueOf(albumId));
        params.put("level", String.valueOf(privacyLevel));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoAddImageResponse r = new PiwigoResponseBufferingHandler.PiwigoAddImageResponse(getMessageId(), getPiwigoMethod());
        storeResponse(r);
    }

}