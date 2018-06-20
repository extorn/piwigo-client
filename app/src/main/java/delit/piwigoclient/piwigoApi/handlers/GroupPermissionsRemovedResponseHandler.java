package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class GroupPermissionsRemovedResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "delGrpPermRspHdlr";
    private final ArrayList<Long> albumsNotAllowedAccessTo;
    private final long groupId;

    public GroupPermissionsRemovedResponseHandler(long groupId, ArrayList<Long> albumsNotAllowedAccessTo) {
        super("pwg.permissions.remove", TAG);
        this.groupId = groupId;
        this.albumsNotAllowedAccessTo = albumsNotAllowedAccessTo;
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
        if (albumsNotAllowedAccessTo != null && albumsNotAllowedAccessTo.size() > 0) {
            for (Long albumId : albumsNotAllowedAccessTo) {
                params.add("cat_id[]", String.valueOf(albumId));
            }
        }
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();

        PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRemovedResponse r = new PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRemovedResponse(getMessageId(), getPiwigoMethod(), groupId, albumsNotAllowedAccessTo);
        storeResponse(r);
    }

}