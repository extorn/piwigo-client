package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class UserDeleteResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "DeleteUserRspHdlr";
    private final long userId;

    public UserDeleteResponseHandler(long userId) {
        super("pwg.users.delete", TAG);
        this.userId = userId;
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
        params.put("user_id", String.valueOf(userId));
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        PiwigoDeleteUserResponse r = new PiwigoDeleteUserResponse(getMessageId(), getPiwigoMethod(), isCached);
        storeResponse(r);
    }

    public static class PiwigoDeleteUserResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        public PiwigoDeleteUserResponse(long messageId, String piwigoMethod, boolean isCached) {
            super(messageId, piwigoMethod, isCached);
        }
    }
}