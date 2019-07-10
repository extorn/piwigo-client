package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class UserAddResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "AddUserRspHdlr";
    private final User user;

    public UserAddResponseHandler(User user) {
        super("pwg.users.add", TAG);
        this.user = user;
    }

    @Override
    public RequestParams buildRequestParameters() {

        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("username", user.getUsername());
        params.put("password", user.getPassword());
        params.put("email", user.getEmail());
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray usersObj = result.get("users").getAsJsonArray();
        ArrayList<User> users = UsersGetListResponseHandler.parseUsersFromJson(usersObj);
        if (users.size() != 1) {
            throw new JSONException("Expected one user to be returned, but there were " + users.size());
        }
        PiwigoAddUserResponse r = new PiwigoAddUserResponse(getMessageId(), getPiwigoMethod(), users.get(0), isCached);
        storeResponse(r);
    }

    public static class PiwigoAddUserResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final User user;

        public PiwigoAddUserResponse(long messageId, String piwigoMethod, User user, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.user = user;
        }

        public User getUser() {
            return user;
        }
    }
}