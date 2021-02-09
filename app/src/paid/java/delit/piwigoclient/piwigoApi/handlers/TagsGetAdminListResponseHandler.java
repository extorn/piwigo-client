package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.Tag;

public class TagsGetAdminListResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "TagsAdminListRspHdlr";
    private final int page;
    private final int pageSize;

    public TagsGetAdminListResponseHandler(int page, int pageSize) {
        super("pwg.tags.getAdminList", TAG);
        this.page = page;
        this.pageSize = pageSize;
    }

    @Override
    public String getPiwigoMethod() {
        return super.getPiwigoMethod();
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
        JsonArray tagsObj = result.get("tags").getAsJsonArray();
        ArrayList<Tag> tags = TagsGetListResponseHandler.parseTagsFromJson(tagsObj);
        for(Tag t : tags) {
            t.markAsAdminCopy();
        }
        PiwigoGetTagsAdminListRetrievedResponse r = new PiwigoGetTagsAdminListRetrievedResponse(getMessageId(), getPiwigoMethod(), page, pageSize, tags.size(), tags, isCached);
        storeResponse(r);
    }

    public static class PiwigoGetTagsAdminListRetrievedResponse extends TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse {

        public PiwigoGetTagsAdminListRetrievedResponse(long messageId, String piwigoMethod, int page, int pageSize, int itemsOnPage, ArrayList<Tag> tags, boolean isCached) {
            super(messageId, piwigoMethod, page, pageSize, itemsOnPage, tags, isCached);
        }
    }


    public boolean isUseHttpGet() {
        return true;
    }
}