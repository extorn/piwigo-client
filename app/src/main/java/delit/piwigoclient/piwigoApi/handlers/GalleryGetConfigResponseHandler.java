package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.ServerConfig;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class GalleryGetConfigResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GalleryGetCfgRspHdlr";

    public GalleryGetConfigResponseHandler() {
        super("piwigo_client.gallery.getConfig", TAG);
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
        JsonArray configItems = result.get("configItems").getAsJsonArray();

        ServerConfig serverConfig = new ServerConfig();

        for(int i = 0; i < configItems.size(); i++) {
            JsonElement configItemElem = configItems.get(i);
            JsonObject configItem = configItemElem.getAsJsonObject();
            String param = configItem.get("param").getAsString();
            switch(param) {
                case "activate_comments":
                    serverConfig.setCommentsAllowed(getBooleanConfigItemValue(configItem));
                    break;
                case "comments_author_mandatory":
                    serverConfig.setCommentsAuthorMandatory(getBooleanConfigItemValue(configItem));
                    break;
                case "comments_email_mandatory":
                    serverConfig.setCommentsEmailMandatory(getBooleanConfigItemValue(configItem));
                    break;
                case "comments_forall":
                    serverConfig.setAnonymousCommentsAllowed(getBooleanConfigItemValue(configItem));
                    break;
                case "gallery_locked":
                    serverConfig.setGalleryLocked(getBooleanConfigItemValue(configItem));
                    break;
                case "gallery_title":
                    serverConfig.setGalleryTitle(getStringConfigItemValue(configItem));
                    break;
                case "rate":
                    serverConfig.setRatingAllowed(getBooleanConfigItemValue(configItem));
                    break;
                case "rate_anonymous":
                    serverConfig.setAnonymousRatingAllowed(getBooleanConfigItemValue(configItem));
                    break;
                case "user_can_delete_comment":
                    serverConfig.setCommentsUserDeletable(getBooleanConfigItemValue(configItem));
                    break;
                case "user_can_edit_comment":
                    serverConfig.setCommentsUserEditable(getBooleanConfigItemValue(configItem));
                    break;
                default:
                    throw new JSONException("Unexpected parameter: " + param);
            }
        }
        PiwigoGalleryGetConfigResponse r = new PiwigoGalleryGetConfigResponse(getMessageId(), getPiwigoMethod(), serverConfig, isCached);
        storeResponse(r);
    }

    private String getStringConfigItemValue(JsonObject configItem) throws JSONException {
        JsonElement jsonElement = configItem.get("value");
        if(jsonElement.isJsonPrimitive()) {
            return jsonElement.getAsString();
        }
        throw new JSONException("Expected String value for param : " + configItem.get("param").getAsString());
    }

    private boolean getBooleanConfigItemValue(JsonObject configItem) throws JSONException {
        JsonElement jsonElement = configItem.get("value");
        if(jsonElement.isJsonPrimitive()) {
            return jsonElement.getAsBoolean();
        }
        throw new JSONException("Expected boolean value for param : " + configItem.get("param").getAsString());
    }

    public static class PiwigoGalleryGetConfigResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final ServerConfig serverConfig;

        public PiwigoGalleryGetConfigResponse(long messageId, String piwigoMethod, ServerConfig serverConfig, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.serverConfig = serverConfig;
        }

        public ServerConfig getServerConfig() {
            return serverConfig;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}