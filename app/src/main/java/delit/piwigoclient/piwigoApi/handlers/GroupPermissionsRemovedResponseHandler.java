package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;

import delit.libs.http.RequestParams;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

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

        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("group_id", String.valueOf(groupId));
        if (albumsNotAllowedAccessTo != null && albumsNotAllowedAccessTo.size() > 0) {
            for (Long albumId : albumsNotAllowedAccessTo) {
                params.add("cat_id[]", String.valueOf(albumId));
            }
        }
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();

        PiwigoGroupPermissionsRemovedResponse r = new PiwigoGroupPermissionsRemovedResponse(getMessageId(), getPiwigoMethod(), groupId, albumsNotAllowedAccessTo, isCached);
        storeResponse(r);
    }

    public static class PiwigoGroupPermissionsRemovedResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final ArrayList<Long> albumsForWhichPermissionRemoved;
        private final long groupId;

        public PiwigoGroupPermissionsRemovedResponse(long messageId, String piwigoMethod, long groupId, ArrayList<Long> albumsForWhichPermissionRemoved, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.groupId = groupId;
            this.albumsForWhichPermissionRemoved = albumsForWhichPermissionRemoved;
        }

        public long getGroupId() {
            return groupId;
        }

        public ArrayList<Long> getAlbumsForWhichPermissionRemoved() {
            return albumsForWhichPermissionRemoved;
        }
    }
}