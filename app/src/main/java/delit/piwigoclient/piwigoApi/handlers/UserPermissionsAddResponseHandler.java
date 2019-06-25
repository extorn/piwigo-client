package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class UserPermissionsAddResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "addUsrPermRspHdlr";
    private final HashSet<Long> newAlbumsAllowedAccessTo;
    private final long userId;

    public UserPermissionsAddResponseHandler(long userId, HashSet<Long> newAlbumsAllowedAccessTo) {
        super("pwg.permissions.add", TAG);
        this.userId = userId;
        this.newAlbumsAllowedAccessTo = newAlbumsAllowedAccessTo;
        if (newAlbumsAllowedAccessTo == null || newAlbumsAllowedAccessTo.size() == 0) {
            throw new IllegalArgumentException("User must be being given access to at least one album");
        }
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
        for (Long albumId : newAlbumsAllowedAccessTo) {
            params.add("cat_id[]", String.valueOf(albumId));
        }
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();

        PiwigoUserPermissionsAddedResponse r = new PiwigoUserPermissionsAddedResponse(getMessageId(), getPiwigoMethod(), userId, newAlbumsAllowedAccessTo, isCached);
        storeResponse(r);
    }

    public static class PiwigoUserPermissionsAddedResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final HashSet<Long> albumsForWhichPermissionAdded;
        private final long userId;

        public PiwigoUserPermissionsAddedResponse(long messageId, String piwigoMethod, long userId, HashSet<Long> albumsForWhichPermissionAdded, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.userId = userId;
            this.albumsForWhichPermissionAdded = albumsForWhichPermissionAdded;
        }

        public long getUserId() {
            return userId;
        }

        public HashSet<Long> getAlbumsForWhichPermissionAdded() {
            return albumsForWhichPermissionAdded;
        }
    }
}