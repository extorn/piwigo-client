package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.Iterator;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.libs.http.RequestParams;

public class PluginUserTagsUpdateResourceTagsListResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UpdateResTagsRspHdlr";
    private final T resource;

    public PluginUserTagsUpdateResourceTagsListResponseHandler(T resource) {
        super("user_tags.tags.update", TAG);
        this.resource = resource;
    }

    @Override
    public RequestParams buildRequestParameters() {

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("image_id", String.valueOf(resource.getId()));

        StringBuilder sb = new StringBuilder();
        Iterator<Tag> iter = resource.getTags().iterator();
        while (iter.hasNext()) {
            sb.append(iter.next().getName());
            if (iter.hasNext()) {
                sb.append(',');
            }
        }
        params.put("tags", sb.toString());

        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {

        String errorStr = null;
        if(rsp.isJsonObject()) {
            JsonObject rspObj = ((JsonObject)rsp);
            if(rspObj.has("error")) {
                JsonArray errors = rspObj.getAsJsonArray("error");
                StringBuilder sb = new StringBuilder();
                for(int i = 0; i < errors.size(); i++) {
                    JsonElement error = errors.get(i);
                    sb.append(error.getAsString());
                    if(i < errors.size() - 1) {
                        sb.append('\n');
                    }
                }
                errorStr = sb.toString();
            }
        }
        PiwigoUserTagsUpdateTagsListResponse r = new PiwigoUserTagsUpdateTagsListResponse(getMessageId(), getPiwigoMethod(), resource, isCached);
        r.setError(errorStr);
        storeResponse(r);
    }

    public static class PiwigoUserTagsUpdateTagsListResponse extends PiwigoResponseBufferingHandler.PiwigoResourceItemResponse {
        private String error;

        public PiwigoUserTagsUpdateTagsListResponse(long messageId, String piwigoMethod, ResourceItem piwigoResource, boolean isCached) {
            super(messageId, piwigoMethod, piwigoResource, isCached);
        }

        public boolean hasError() {
            return error != null;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}