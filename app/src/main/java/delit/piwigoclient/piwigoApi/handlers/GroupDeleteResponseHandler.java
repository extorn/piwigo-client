package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONException;
import org.json.JSONObject;

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
        if(PiwigoSessionDetails.isLoggedInWithSessionDetails()) {
            sessionToken = PiwigoSessionDetails.getInstance().getSessionToken();
        }
        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("group_id", String.valueOf(groupId));
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoDeleteGroupResponse r = new PiwigoResponseBufferingHandler.PiwigoDeleteGroupResponse(getMessageId(), getPiwigoMethod());
        storeResponse(r);
    }
}