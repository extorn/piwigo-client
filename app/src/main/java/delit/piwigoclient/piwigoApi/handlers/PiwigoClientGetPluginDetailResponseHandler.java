package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

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
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonElement elem = result.get("version");
        String pluginVersion = elem.getAsString();

        getPiwigoSessionDetails().setPiwigoClientPluginVersion(pluginVersion);
        PiwigoPwgCliPluginDetailsResponse r = new PiwigoPwgCliPluginDetailsResponse(getMessageId(), getPiwigoMethod(), pluginVersion, isCached);
        storeResponse(r);
    }

    public static class PiwigoPwgCliPluginDetailsResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final String pluginVersion;

        public PiwigoPwgCliPluginDetailsResponse(long messageId, String piwigoMethod, String pluginVersion, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.pluginVersion = pluginVersion;
        }

        public String getPluginVersion() {
            return pluginVersion;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}