package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class AdminTagDeleteResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "DeleteTagRspHdlr";

    final Tag tag;

    public AdminTagDeleteResponseHandler(Tag tag) {
        super("pwg.tags.delete", TAG);
        this.tag = tag;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("tag_id", tag.getId());
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        long tagId = result.get("id").getAsLong();
        if(tagId == tag.getId()) {
            PiwigoDeleteTagResponse r = new PiwigoDeleteTagResponse(getMessageId(), getPiwigoMethod(), tag, isCached);
            storeResponse(r);
        }
        resetSuccessAsFailure();
    }

    public static class PiwigoDeleteTagResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final Tag tag;

        public PiwigoDeleteTagResponse(long messageId, String piwigoMethod, Tag tag, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.tag = tag;
        }

        public Tag getTag() {
            return tag;
        }
    }
}