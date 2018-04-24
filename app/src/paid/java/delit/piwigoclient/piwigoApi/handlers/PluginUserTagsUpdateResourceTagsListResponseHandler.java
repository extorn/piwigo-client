package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import java.util.Iterator;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

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

        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {

        PiwigoResponseBufferingHandler.PiwigoUserTagsUpdateTagsListResponse r = new PiwigoResponseBufferingHandler.PiwigoUserTagsUpdateTagsListResponse(getMessageId(), getPiwigoMethod(), resource);
        storeResponse(r);
    }

}