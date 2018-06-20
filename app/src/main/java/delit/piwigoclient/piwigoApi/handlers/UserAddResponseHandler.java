package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class UserAddResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "AddUserRspHdlr";
    private final User user;

    public UserAddResponseHandler(User user) {
        super("pwg.users.add", TAG);
        this.user = user;
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
        params.put("username", user.getUsername());
        params.put("password", user.getPassword());
        params.put("email", user.getEmail());
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray usersObj = result.get("users").getAsJsonArray();
        ArrayList<User> users = UsersGetListResponseHandler.parseUsersFromJson(usersObj);
        if (users.size() != 1) {
            throw new JSONException("Expected one user to be returned, but there were " + users.size());
        }
        PiwigoResponseBufferingHandler.PiwigoAddUserResponse r = new PiwigoResponseBufferingHandler.PiwigoAddUserResponse(getMessageId(), getPiwigoMethod(), users.get(0));
        storeResponse(r);
    }

}