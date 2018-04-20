package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class UserPermissionsRemovedResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "delUsrPermRspHdlr";
    private final HashSet<Long> newAlbumsNotAllowedAccessTo;
    private final long userId;

    public UserPermissionsRemovedResponseHandler(long userId, HashSet<Long> newAlbumsNotAllowedAccessTo) {
        super("pwg.permissions.remove", TAG);
        this.userId = userId;
        this.newAlbumsNotAllowedAccessTo = newAlbumsNotAllowedAccessTo;
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
        if(newAlbumsNotAllowedAccessTo != null && newAlbumsNotAllowedAccessTo.size() > 0) {
            for (Long albumId : newAlbumsNotAllowedAccessTo) {
                params.add("cat_id[]", String.valueOf(albumId));
            }
        }
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();

        PiwigoResponseBufferingHandler.PiwigoUserPermissionsRemovedResponse r = new PiwigoResponseBufferingHandler.PiwigoUserPermissionsRemovedResponse(getMessageId(), getPiwigoMethod(), userId, newAlbumsNotAllowedAccessTo);
        storeResponse(r);
    }

}