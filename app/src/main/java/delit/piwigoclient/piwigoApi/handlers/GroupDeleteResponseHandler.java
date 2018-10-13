package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class GroupDeleteResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "DeleteGroupRspHdlr";
    private final long groupId;

    public GroupDeleteResponseHandler(long groupId) {
        super("pwg.groups.delete", TAG);
        this.groupId = (groupId);
    }

    @Override
    public RequestParams buildRequestParameters() {
        String sessionToken = "";
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
            sessionToken = sessionDetails.getSessionToken();
        }
        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("group_id", String.valueOf(groupId));
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        PiwigoDeleteGroupResponse r = new PiwigoDeleteGroupResponse(getMessageId(), getPiwigoMethod());
        storeResponse(r);
    }

    public static class PiwigoDeleteGroupResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        public PiwigoDeleteGroupResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod, true);
        }
    }
}