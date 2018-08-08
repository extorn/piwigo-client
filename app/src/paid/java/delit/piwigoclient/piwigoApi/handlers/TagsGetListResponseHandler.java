package delit.piwigoclient.piwigoApi.handlers;

import com.crashlytics.android.Crashlytics;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;

import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

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
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray tagsObj = result.get("tags").getAsJsonArray();
        HashSet<Tag> tags = parseTagsFromJson(tagsObj);
        PiwigoGetTagsListRetrievedResponse r = new PiwigoGetTagsListRetrievedResponse(getMessageId(), getPiwigoMethod(), page, tags.size(), tags.size(), tags);
        storeResponse(r);
    }

    public static HashSet<Tag> parseTagsFromJson(JsonArray tagsObj) throws JSONException {

        SimpleDateFormat piwigoDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK);

        HashSet<Tag> tags = new LinkedHashSet<>(tagsObj.size());
        for (int i = 0; i < tagsObj.size(); i++) {
            JsonObject tagObj = tagsObj.get(i).getAsJsonObject();
            Tag g = parseTagFromJson(tagObj, piwigoDateFormat);
            tags.add(g);
        }
        return tags;
    }

    public static Tag parseTagFromJson(JsonObject tagObj, SimpleDateFormat piwigoDateFormat) throws JSONException {
        long id = tagObj.get("id").getAsLong();
        String name = tagObj.get("name").getAsString();
        Date lastModified = parseDate(tagObj, "lastmodified", piwigoDateFormat);
        int usageCount = tagObj.has("counter")?tagObj.get("counter").getAsInt():0;
        return new Tag(id, name, usageCount, lastModified);
    }

    public static Date parseDate(JsonObject jsonObject, String fieldName, SimpleDateFormat piwigoDateFormat) throws JSONException {
        if(jsonObject.has(fieldName) && !jsonObject.get(fieldName).isJsonNull()) {
            String dateStr = jsonObject.get(fieldName).getAsString();
            if (dateStr != null) {
                try {
                    return piwigoDateFormat.parse(dateStr);
                } catch (ParseException e) {
Crashlytics.logException(e);
                    throw new JSONException("Unable to parse date " + dateStr);
                }
            }
        }
        return null;
    }

    public static class PiwigoGetTagsListRetrievedResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final HashSet<Tag> tags;
        private final int itemsOnPage;
        private final int pageSize;
        private final int page;

        public PiwigoGetTagsListRetrievedResponse(long messageId, String piwigoMethod, int page, int pageSize, int itemsOnPage, HashSet<Tag> tags) {
            super(messageId, piwigoMethod, true);
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

        public HashSet<Tag> getTags() {
            return tags;
        }
    }
}