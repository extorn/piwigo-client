package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;

import delit.libs.core.util.Logging;
import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class TagsGetListResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "TagsListRspHdlr";
    private final int page;
    private final int pageSize;

    public TagsGetListResponseHandler(int page, int pageSize) {
        super("pwg.tags.getList", TAG);
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
        ArrayList<Tag> tags = parseTagsFromJson(tagsObj);
        PiwigoGetTagsListRetrievedResponse r = new PiwigoGetTagsListRetrievedResponse(getMessageId(), getPiwigoMethod(), page, tags.size(), tags.size(), tags, isCached);
        storeResponse(r);
    }

    public static ArrayList<Tag> parseTagsFromJson(JsonArray tagsObj) throws JSONException {

        ArrayList<Tag> tags = new ArrayList<>(tagsObj.size());
        for (int i = 0; i < tagsObj.size(); i++) {
            JsonObject tagObj = tagsObj.get(i).getAsJsonObject();
            Tag g = parseTagFromJson(tagObj);
            tags.add(g);
        }
        return tags;
    }

    public static Tag parseTagFromJson(JsonObject tagObj) throws JSONException {
        long id = tagObj.get("id").getAsLong();
        String name = tagObj.get("name").getAsString();
        Date lastModified = parseDate(tagObj, "lastmodified");
        int usageCount = tagObj.has("counter")?tagObj.get("counter").getAsInt():0;
        return new Tag(id, name, usageCount, lastModified);
    }

    public static Date parseDate(JsonObject jsonObject, String fieldName) throws JSONException {
        if(jsonObject.has(fieldName) && !jsonObject.get(fieldName).isJsonNull()) {
            String dateStr = jsonObject.get(fieldName).getAsString();
            if (dateStr != null) {
                try {
                    return parsePiwigoServerDate(dateStr);
                } catch (ParseException e) {
Logging.recordException(e);
                    throw new JSONException("Unable to parse date " + dateStr);
                }
            }
        }
        return null;
    }

    public static class PiwigoGetTagsListRetrievedResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final ArrayList<Tag> tags;
        private final int itemsOnPage;
        private final int pageSize;
        private final int page;

        public PiwigoGetTagsListRetrievedResponse(long messageId, String piwigoMethod, int page, int pageSize, int itemsOnPage, ArrayList<Tag> tags, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.page = page;
            this.pageSize = pageSize;
            this.itemsOnPage = itemsOnPage;
            this.tags = tags;
        }

        public int getItemsOnPage() {
            return itemsOnPage;
        }

        public int getPage() {
            return page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public ArrayList<Tag> getTags() {
            return tags;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}