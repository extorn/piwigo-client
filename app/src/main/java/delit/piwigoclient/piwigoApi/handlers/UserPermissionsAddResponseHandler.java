package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class UserPermissionsAddResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "addUsrPermRspHdlr";
    private final HashSet<Long> newAlbumsAllowedAccessTo;
    private long userId;

    public UserPermissionsAddResponseHandler(long userId, HashSet<Long> newAlbumsAllowedAccessTo) {
        super("pwg.permissions.add", TAG);
        this.userId = userId;
        this.newAlbumsAllowedAccessTo = newAlbumsAllowedAccessTo;
        if(newAlbumsAllowedAccessTo == null || newAlbumsAllowedAccessTo.size() == 0) {
            throw new IllegalArgumentException("User must be being given access to at least one album");
        }
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
        params.put("user_id", String.valueOf(userId));
        for (Long albumId : newAlbumsAllowedAccessTo) {
            params.add("cat_id[]", String.valueOf(albumId));
        }
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        JSONObject result = rsp.getJSONObject("result");

        PiwigoResponseBufferingHandler.PiwigoUserPermissionsAddedResponse r = new PiwigoResponseBufferingHandler.PiwigoUserPermissionsAddedResponse(getMessageId(), getPiwigoMethod(), userId, newAlbumsAllowedAccessTo);
        storeResponse(r);
    }

}