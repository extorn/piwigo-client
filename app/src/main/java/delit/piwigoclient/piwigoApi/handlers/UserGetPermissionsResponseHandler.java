package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class UserGetPermissionsResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetUsrPermRspHdlr";
    private long userId;

    public UserGetPermissionsResponseHandler(long userId) {
        super("pwg.permissions.getList", TAG);
        this.userId = userId;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("user_id", String.valueOf(userId));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        JSONObject result = rsp.getJSONObject("result");
        JSONArray cats = result.getJSONArray("categories");
        HashSet<Long> allowedDirectAlbums = new HashSet<>(cats.length());
        HashSet<Long> allowedIndirectAlbums = new HashSet<>(cats.length());

        for (int i = 0; i < cats.length(); i++) {
            JSONObject cat = (JSONObject) cats.get(i);

            long category = cat.getLong("id");

            JSONArray allowedUsers = cat.getJSONArray("users");
            for (int j = 0; j < allowedUsers.length(); j++) {
                if (allowedUsers.getLong(j) == userId) {
                    allowedDirectAlbums.add(category);
                    break;
                }
            }
            // check indirect permissions
            JSONArray allowedUsersIndirect = cat.getJSONArray("users_indirect");
            for (int j = 0; j < allowedUsersIndirect.length(); j++) {
                if (allowedUsersIndirect.getLong(j) == userId) {
                    allowedIndirectAlbums.add(category);
                    break;
                }
            }
        }
        PiwigoResponseBufferingHandler.PiwigoUserPermissionsResponse r = new PiwigoResponseBufferingHandler.PiwigoUserPermissionsResponse(getMessageId(), getPiwigoMethod(), userId, allowedDirectAlbums, allowedIndirectAlbums);
        storeResponse(r);
    }

}