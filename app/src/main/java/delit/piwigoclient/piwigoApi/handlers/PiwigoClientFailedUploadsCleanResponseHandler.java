package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class PiwigoClientFailedUploadsCleanResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "PwgCliFailUpClean";
    public static final String WS_METHOD_NAME = "piwigo_client.upload.clean";

    public PiwigoClientFailedUploadsCleanResponseHandler() {
        super(WS_METHOD_NAME, TAG);
    }

    @Override
    public RequestParams buildRequestParameters() {
        String sessionToken = "";
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
            sessionToken = sessionDetails.getSessionToken();
        }
        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("pwg_token", sessionToken);
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