package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;

import delit.libs.http.RequestParams;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

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

        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("user_id", String.valueOf(userId));
        if (newAlbumsNotAllowedAccessTo != null && newAlbumsNotAllowedAccessTo.size() > 0) {
            for (Long albumId : newAlbumsNotAllowedAccessTo) {
                params.add("cat_id[]", String.valueOf(albumId));
            }
        }
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();

        PiwigoUserPermissionsRemovedResponse r = new PiwigoUserPermissionsRemovedResponse(getMessageId(), getPiwigoMethod(), userId, newAlbumsNotAllowedAccessTo, isCached);
        storeResponse(r);
    }

    public static class PiwigoUserPermissionsRemovedResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final HashSet<Long> albumsForWhichPermissionRemoved;
        private final long userId;

        public PiwigoUserPermissionsRemovedResponse(long messageId, String piwigoMethod, long userId, HashSet<Long> albumsForWhichPermissionRemoved, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.userId = userId;
            this.albumsForWhichPermissionRemoved = albumsForWhichPermissionRemoved;
        }

        public long getUserId() {
            return userId;
        }

        public HashSet<Long> getAlbumsForWhichPermissionRemoved() {
            return albumsForWhichPermissionRemoved;
        }
    }
}