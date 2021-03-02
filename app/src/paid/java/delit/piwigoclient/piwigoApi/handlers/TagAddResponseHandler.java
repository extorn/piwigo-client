package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class TagAddResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "AddTagRspHdlr";

    final String tagname;

    public TagAddResponseHandler(String tagname) {
        super("pwg.tags.add", TAG);
        this.tagname = tagname;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("name", tagname);
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        long tagId = result.get("id").getAsLong();
        Tag tag = new Tag(tagId, tagname);
        PiwigoAddTagResponse r = new PiwigoAddTagResponse(getMessageId(), getPiwigoMethod(), tag, isCached);
        storeResponse(r);
    }

    public static class PiwigoAddTagResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final Tag tag;

        public PiwigoAddTagResponse(long messageId, String piwigoMethod, Tag tag, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.tag = tag;
        }

        public Tag getTag() {
            return tag;
        }
    }
}