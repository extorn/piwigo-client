package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class PiwigoClientFailedUploadsCleanResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "PwgCliFailUpClean";
    public static final String WS_METHOD_NAME = "piwigo_client.upload.clean";

    public PiwigoClientFailedUploadsCleanResponseHandler() {
        super(WS_METHOD_NAME, TAG);
    }

    @Override
    public RequestParams buildRequestParameters() {

        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonElement elem = result.get("filesRemoved");
        int filesCleaned = elem.getAsInt();
        PiwigoFailedUploadsCleanedResponse r = new PiwigoFailedUploadsCleanedResponse(getMessageId(), getPiwigoMethod(), filesCleaned, isCached);
        storeResponse(r);
    }

    public static class PiwigoFailedUploadsCleanedResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final int filesCleaned;

        public PiwigoFailedUploadsCleanedResponse(long messageId, String piwigoMethod, int filesCleaned, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.filesCleaned = filesCleaned;
        }

        public int getFilesCleaned() {
            return filesCleaned;
        }
    }
}