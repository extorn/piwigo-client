package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class PiwigoClientGetPluginDetailResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "PwgCliGetPluginDetails";
    public static final String WS_METHOD_NAME = "piwigo_client.getPluginDetails";

    public PiwigoClientGetPluginDetailResponseHandler() {
        super(WS_METHOD_NAME, TAG);
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonElement elem = result.get("version");
        String pluginVersion = elem.getAsString();

        PiwigoSessionDetails.getInstance(getConnectionPrefs()).setPiwigoClientPluginVersion(pluginVersion);
        PiwigoPwgCliPluginDetailsResponse r = new PiwigoPwgCliPluginDetailsResponse(getMessageId(), getPiwigoMethod(), pluginVersion);
        storeResponse(r);
    }

    public static class PiwigoPwgCliPluginDetailsResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final String pluginVersion;

        public PiwigoPwgCliPluginDetailsResponse(long messageId, String piwigoMethod, String pluginVersion) {
            super(messageId, piwigoMethod, true);
            this.pluginVersion = pluginVersion;
        }

        public String getPluginVersion() {
            return pluginVersion;
        }
    }
}