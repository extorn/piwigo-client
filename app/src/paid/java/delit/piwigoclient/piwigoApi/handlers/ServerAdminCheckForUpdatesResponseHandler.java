package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class ServerAdminCheckForUpdatesResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "ServerChkForUpdatesHndlr";


    public ServerAdminCheckForUpdatesResponseHandler() {
        super("pwg.extensions.checkUpdates", TAG);
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
        boolean serverUpdateAvailable = false;
        if(result.has("piwigo_need_update")) {
            serverUpdateAvailable = result.get("piwigo_need_update").getAsBoolean();
        }
        boolean pluginUpdateAvailable = false;
        if(result.has("ext_need_update")) {
            pluginUpdateAvailable = result.get("ext_need_update").getAsBoolean();
        }

        PiwigoServerUpdateResponse r = new PiwigoServerUpdateResponse(getMessageId(), getPiwigoMethod(), serverUpdateAvailable,pluginUpdateAvailable,isCached);
        storeResponse(r);
    }

    public static class PiwigoServerUpdateResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        boolean serverUpdateAvailable;
        boolean pluginUpdateAvailable;

        public PiwigoServerUpdateResponse(long messageId, String piwigoMethod, boolean serverUpdateAvailable, boolean pluginUpdateAvailable, boolean isCached) {
            super(messageId, piwigoMethod, isCached);
            this.serverUpdateAvailable = serverUpdateAvailable;
            this.pluginUpdateAvailable = pluginUpdateAvailable;
        }

        public PiwigoServerUpdateResponse(long messageId, String piwigoMethod, boolean isEndResponse, boolean isCached) {
            super(messageId, piwigoMethod, isEndResponse, isCached);
        }

        public boolean isServerUpdateAvailable() {
            return serverUpdateAvailable;
        }

        public boolean isPluginUpdateAvailable() {
            return pluginUpdateAvailable;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}