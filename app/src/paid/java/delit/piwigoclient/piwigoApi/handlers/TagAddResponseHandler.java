package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class TagAddResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "AddTagRspHdlr";

    final String tagname;

    public TagAddResponseHandler(String tagname) {
        super("pwg.tags.add", TAG);
        this.tagname = tagname;
    }

    @Override
    public RequestParams buildRequestParameters() {
        String sessionToken = "";
        if(PiwigoSessionDetails.isLoggedInWithSessionDetails()) {
            sessionToken = PiwigoSessionDetails.getInstance().getSessionToken();
        }
        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("name", tagname);
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        long tagId = result.get("id").getAsLong();
        Tag tag = new Tag(tagId, tagname);
        PiwigoAddTagResponse r = new PiwigoAddTagResponse(getMessageId(), getPiwigoMethod(), tag);
        storeResponse(r);
    }

    public static class PiwigoAddTagResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final Tag tag;

        public PiwigoAddTagResponse(long messageId, String piwigoMethod, Tag tag) {
            super(messageId, piwigoMethod, true);
            this.tag = tag;
        }

        public Tag getTag() {
            return tag;
        }
    }
}