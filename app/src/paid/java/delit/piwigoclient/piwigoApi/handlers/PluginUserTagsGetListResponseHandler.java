package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;
import java.util.LinkedHashSet;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.Tag;

public class PluginUserTagsGetListResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "PluginTagsListRspHdlr";
    private final String tagNameStart;

    public PluginUserTagsGetListResponseHandler(String tagNameStart) {
        super("user_tags.tags.list", TAG);
        this.tagNameStart = tagNameStart;
    }

    @Override
    public String getPiwigoMethod() {
        return super.getPiwigoMethod();
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("q", tagNameStart);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        boolean validResponse = rsp.isJsonArray();
        if(validResponse) {
            JsonArray tagsObj = rsp.getAsJsonArray();
            HashSet<Tag> tags = parseTagsFromJson(tagsObj);
            PiwigoUserTagsPluginGetTagsListRetrievedResponse r = new PiwigoUserTagsPluginGetTagsListRetrievedResponse(getMessageId(), getPiwigoMethod(), 0, tags.size(), tags.size(), tags, isCached);
            storeResponse(r);
        } else {
            throw new RuntimeException("Unexpected response from user tags plugin");
        }
    }

    public static HashSet<Tag> parseTagsFromJson(JsonArray tagsObj) {

        HashSet<Tag> tags = new LinkedHashSet<>(tagsObj.size());
        for (int i = 0; i < tagsObj.size(); i++) {
            JsonObject tagObj = tagsObj.get(i).getAsJsonObject();
            Tag g = parseTagFromJson(tagObj);
            tags.add(g);
        }
        return tags;
    }

    public static Tag parseTagFromJson(JsonObject tagObj) {
        String idStr = tagObj.get("id").getAsString();
        idStr = idStr.replaceAll("~~","");
        long id = Long.valueOf(idStr);
        String name = tagObj.get("name").getAsString();
        return new Tag(id, name);
    }

    public static class PiwigoUserTagsPluginGetTagsListRetrievedResponse extends TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse {

        public PiwigoUserTagsPluginGetTagsListRetrievedResponse(long messageId, String piwigoMethod, int page, int pageSize, int itemsOnPage, HashSet<Tag> tags, boolean isCached) {
            super(messageId, piwigoMethod, page, pageSize, itemsOnPage, tags, isCached);
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}