package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;

import delit.libs.http.RequestParams;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class UserGetPermissionsResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetUsrPermRspHdlr";
    private final long userId;

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
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray cats = result.get("categories").getAsJsonArray();
        HashSet<Long> allowedDirectAlbums = new HashSet<>(cats.size());
        HashSet<Long> allowedIndirectAlbums = new HashSet<>(cats.size());

        for (int i = 0; i < cats.size(); i++) {
            JsonObject cat = (JsonObject) cats.get(i);

            long category = cat.get("id").getAsLong();

            JsonArray allowedUsers = cat.get("users").getAsJsonArray();
            for (int j = 0; j < allowedUsers.size(); j++) {
                if (allowedUsers.get(j).getAsLong() == userId) {
                    allowedDirectAlbums.add(category);
                    break;
                }
            }
            // check indirect permissions
            JsonArray allowedUsersIndirect = cat.get("users_indirect").getAsJsonArray();
            for (int j = 0; j < allowedUsersIndirect.size(); j++) {
                if (allowedUsersIndirect.get(j).getAsLong() == userId) {
                    allowedIndirectAlbums.add(category);
                    break;
                }
            }
        }
        PiwigoUserPermissionsResponse r = new PiwigoUserPermissionsResponse(getMessageId(), getPiwigoMethod(), userId, allowedDirectAlbums, allowedIndirectAlbums, isCached);
        storeResponse(r);
    }

    public static class PiwigoUserPermissionsResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final HashSet<Long> indirectlyAccessibleAlbumIds;
        private final HashSet<Long> directlyAccessibleAlbumIds;
        private final long userId;

        public PiwigoUserPermissionsResponse(long messageId, String piwigoMethod, long userId, HashSet<Long> directlyAccessibleAlbumIds, HashSet<Long> indirectlyAccessibleAlbumIds, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.userId = userId;
            this.directlyAccessibleAlbumIds = directlyAccessibleAlbumIds;
            this.indirectlyAccessibleAlbumIds = indirectlyAccessibleAlbumIds;
        }

        public long getUserId() {
            return userId;
        }

        public HashSet<Long> getDirectlyAccessibleAlbumIds() {
            return directlyAccessibleAlbumIds;
        }

        public HashSet<Long> getIndirectlyAccessibleAlbumIds() {
            return indirectlyAccessibleAlbumIds;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}