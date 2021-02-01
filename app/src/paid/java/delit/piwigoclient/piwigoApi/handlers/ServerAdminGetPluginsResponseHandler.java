package delit.piwigoclient.piwigoApi.handlers;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.json.JSONException;

import java.util.ArrayList;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.ServerPlugin;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class ServerAdminGetPluginsResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetPluginsHndlr";

    public ServerAdminGetPluginsResponseHandler(int page, int pageSize) {
        super("pwg.plugins.getList", TAG);
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {

        ArrayList<ServerPlugin> items = new ArrayList<>();
        JsonArray serverPlugins = rsp.getAsJsonArray();

        for(JsonElement serverPluginJson : serverPlugins) {
            items.add(getGson().fromJson(serverPluginJson, ServerPlugin.class));
        }

        ServerPluginListResponse r = new ServerPluginListResponse(getMessageId(), getPiwigoMethod(), items, isCached);
        storeResponse(r);
    }

    public static class ServerPluginListResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final ArrayList<ServerPlugin> plugins;

        public ServerPluginListResponse(long messageId, String piwigoMethod, ArrayList<ServerPlugin> plugins, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.plugins = plugins;
        }

        public ArrayList<ServerPlugin> getPlugins() {
            return plugins;
        }

        public ArrayList<ServerPlugin> getActivePlugins() {
            return getPluginsWithState("active");
        }

        public ArrayList<ServerPlugin> getPluginsWithState(@NonNull String state) {
            ArrayList<ServerPlugin> wantedPlugins = new ArrayList<>();
            for (ServerPlugin plugin : plugins) {
                if(state.equals(plugin.getState())) {
                    wantedPlugins.add(plugin);
                }
            }
            return wantedPlugins;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}